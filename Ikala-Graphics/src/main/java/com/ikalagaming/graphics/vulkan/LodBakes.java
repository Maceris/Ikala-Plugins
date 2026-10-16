package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.lod.Attributes;
import com.ikalagaming.graphics.lod.BakeTexels;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.vulkan.stages.SceneRender;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The pages level of detail bakes go into, and the bakes waiting for the GPU. A baked cluster (see
 * {@link com.ikalagaming.graphics.lod.BakeLayout}) gets a rectangular tile in a page; each page is
 * a color texture and a data texture of normal, roughness and metalness, drawn through one material
 * with {@link Material#isBakedData()} set, so baked clusters are ordinary textured triangles.
 *
 * <p>When a world with baked clusters is added, {@link #plan} works out from each one's {@link
 * BakeTexels} what each texel of its tile samples, down to the world's own materials. Tiles then
 * follow residency (see {@link LodRenderer}): a cluster loaded gets a tile through {@link #load},
 * its texture coordinates pointed into the tile's page and its triangles at the page's material,
 * and its bake queued; {@code LodBake} bakes a budget of tiles a frame, in any order, since no tile
 * reads another; the cluster is shown once it is baked, and its tile given back when it is evicted.
 *
 * <p>Planning and releasing are safe from any thread; loading, unloading, making pages and taking
 * jobs happen on the render thread.
 */
@Slf4j
public final class LodBakes {

    /**
     * A page's side, in texels. Small enough that half float texture coordinates over it are a
     * quarter of a texel apart at most.
     */
    public static final int PAGE = 512;

    /** Tiles' sides are whole multiples of this many texels, as BakeLayout lays them out. */
    private static final int ALIGN = 4;

    /** Columns of {@link #ALIGN} texels across a page. */
    private static final int COLUMNS = PAGE / ALIGN;

    /** Who the pages' textures belong to in the texture registry: no plugin, so none takes them. */
    private static final String OWNER = "LOD bakes";

    /** Bytes a texel's record takes, see {@code lod_bake.comp}. */
    public static final int RECORD_SIZE = 8 * Integer.BYTES;

    /** A texel's record's material slot when it stands for nothing. */
    private static final int EMPTY = -1;

    /** The most pages there can be, for the descriptor pool. */
    private static final int MAX_PAGES = 1024;

    /** The storage bindings: the color page, then the data page. */
    private static final int COLOR_BINDING = 0;

    /** The data page's binding. */
    private static final int DATA_BINDING = 1;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = CORNERS * AXES;

    /**
     * A tile in a page.
     *
     * @param page The page.
     * @param x Its left, in texels.
     * @param y Its top.
     * @param width Its width, in texels.
     * @param height Its height.
     */
    public record Tile(int page, int x, int y, int width, int height) {

        /**
         * The texels it holds.
         *
         * @return The count.
         */
        public int texels() {
            return width * height;
        }
    }

    /** Bytes a baked texel takes on the GPU: a texel of each page. */
    public static final int BYTES_PER_TEXEL = 8;

    /** Bytes a page takes on the GPU, both textures. */
    public static final long PAGE_BYTES = (long) PAGE * PAGE * BYTES_PER_TEXEL;

    /**
     * What is loaded.
     *
     * @param tiles Tiles loaded, over every world.
     * @param texels The texels they hold.
     * @param pages Pages made, whether or not they hold anything.
     */
    public record Stats(long tiles, long texels, int pages) {
        /** Nothing loaded. */
        public static final Stats NONE = new Stats(0, 0, 0);
    }

    /**
     * A baked cluster: what its tile holds, worked out once, and baked again each time the cluster
     * is loaded.
     *
     * @param cluster The cluster as built, with texture coordinates in its tile from 0 to 1.
     * @param width Its tile's width, in texels.
     * @param height Its tile's height.
     * @param sources The materials its texels sample, which records refer to by index.
     * @param records Each texel's record, row by row, {@link #RECORD_SIZE} bytes each.
     * @param worldPerTexel World units a texel spans.
     */
    public record Baked(
            LodDag.@NonNull Cluster cluster,
            int width,
            int height,
            @NonNull List<Material> sources,
            @NonNull ByteBuffer records,
            double worldPerTexel) {

        /**
         * The bytes its tile takes on the GPU.
         *
         * @return The bytes.
         */
        public long bytes() {
            return (long) width * height * BYTES_PER_TEXEL;
        }
    }

    /**
     * One tile to bake.
     *
     * @param tile The tile.
     * @param baked What it holds.
     * @param world The world it is for.
     */
    public record Job(@NonNull Tile tile, @NonNull Baked baked, long world) {}

    /**
     * A world's baked clusters.
     *
     * @param clusters Each baked cluster, by id.
     */
    public record Plan(@NonNull Map<Integer, Baked> clusters) {

        /**
         * A cluster's bake.
         *
         * @param id The cluster's id.
         * @return Its bake, or null if it isn't baked.
         */
        public Baked of(int id) {
            return clusters.get(id);
        }
    }

    /** A loaded cluster's tile, and whether it has been baked. */
    private static final class Loaded {
        /** The tile. */
        final Tile tile;

        /** Its job, until it is taken. */
        Job job;

        /**
         * Keep a tile.
         *
         * @param tile The tile.
         * @param job Its job.
         */
        Loaded(@NonNull Tile tile, @NonNull Job job) {
            this.tile = tile;
            this.job = job;
        }
    }

    /**
     * A page: two textures, their material, and which of it is free. Tiles are packed in along a
     * skyline, the height of each column taken so far; tiles given back go on a free list that
     * later tiles are cut from, and the page starts over once every tile is back.
     */
    private static final class Page {
        /** Its index. */
        final int index;

        /** Its material, which baked triangles are drawn with. */
        final Material material;

        /** Its index in the material cache. */
        final int materialIndex;

        /** How far down each column of {@link #ALIGN} texels is taken, in texels. */
        final int[] sky = new int[COLUMNS];

        /** Rectangles given back, as x, y, width and height. */
        final List<int[]> free = new ArrayList<>();

        /** Tiles taken and not given back. */
        int used;

        /** The color texture, once made. */
        TextureHandle color;

        /** The data texture, once made. */
        TextureHandle data;

        /** The color image's UNORM view, which bakes write through. */
        long colorStorageView = VK_NULL_HANDLE;

        /** The descriptor set bakes write the page through. */
        long set = VK_NULL_HANDLE;

        /**
         * Start an empty page.
         *
         * @param index Its index.
         * @param material Its material.
         * @param materialIndex The material's index.
         */
        Page(int index, Material material, int materialIndex) {
            this.index = index;
            this.material = material;
            this.materialIndex = materialIndex;
        }

        /**
         * Take a tile: the tightest rectangle given back that holds it, cutting off what is left,
         * or else the place along the skyline where its bottom would be highest, then leftmost.
         *
         * @param width The tile's width, a whole multiple of {@link #ALIGN} up to {@link #PAGE}.
         * @param height Its height, the same.
         * @return Its corner, packed, or -1 if the page has no room.
         */
        long take(int width, int height) {
            int[] tightest = null;
            for (int[] rectangle : free) {
                if (rectangle[2] >= width
                        && rectangle[3] >= height
                        && (tightest == null
                                || rectangle[2] * rectangle[3] < tightest[2] * tightest[3])) {
                    tightest = rectangle;
                }
            }
            if (tightest != null) {
                free.remove(tightest);
                cut(tightest, width, height);
                ++used;
                return pack(tightest[0], tightest[1]);
            }
            final int columns = width / ALIGN;
            int bestColumn = -1;
            int bestTop = Integer.MAX_VALUE;
            for (int column = 0; column + columns <= COLUMNS; ++column) {
                int top = 0;
                for (int c = column; c < column + columns; ++c) {
                    top = Math.max(top, sky[c]);
                }
                if (top + height <= PAGE && top < bestTop) {
                    bestTop = top;
                    bestColumn = column;
                }
            }
            if (bestColumn < 0) {
                return -1;
            }
            for (int c = bestColumn; c < bestColumn + columns; ++c) {
                if (sky[c] < bestTop) {
                    // The gap the tile bridges over is free to later, smaller tiles
                    free.add(new int[] {c * ALIGN, sky[c], ALIGN, bestTop - sky[c]});
                }
                sky[c] = bestTop + height;
            }
            ++used;
            return pack(bestColumn * ALIGN, bestTop);
        }

        /**
         * Free what a tile leaves of a rectangle it is cut from: the strip beside it and the strip
         * under it, the longer of the two running the rectangle's full length.
         *
         * @param rectangle The rectangle, x, y, width and height.
         * @param width The tile's width.
         * @param height The tile's height.
         */
        private void cut(int @NonNull [] rectangle, int width, int height) {
            final int x = rectangle[0];
            final int y = rectangle[1];
            final int right = rectangle[2] - width;
            final int below = rectangle[3] - height;
            if (right * rectangle[3] > below * rectangle[2]) {
                addFree(x + width, y, right, rectangle[3]);
                addFree(x, y + height, width, below);
            } else {
                addFree(x + width, y, right, height);
                addFree(x, y + height, rectangle[2], below);
            }
        }

        /**
         * Put a rectangle on the free list, if it has any texels.
         *
         * @param x Its left.
         * @param y Its top.
         * @param width Its width.
         * @param height Its height.
         */
        private void addFree(int x, int y, int width, int height) {
            if (width > 0 && height > 0) {
                free.add(new int[] {x, y, width, height});
            }
        }

        /**
         * Give a tile back, starting the page over once it is empty.
         *
         * @param x Its left.
         * @param y Its top.
         * @param width Its width.
         * @param height Its height.
         */
        void give(int x, int y, int width, int height) {
            if (--used == 0) {
                Arrays.fill(sky, 0);
                free.clear();
                return;
            }
            addFree(x, y, width, height);
        }
    }

    /** Guards everything below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The pages. */
    private final List<Page> pages = new ArrayList<>();

    /** Jobs waiting for the GPU, in the order they came. */
    private final List<Job> jobs = new ArrayList<>();

    /** Each world's loaded clusters, by id. */
    private final Map<Long, Map<Integer, Loaded>> byWorld = new HashMap<>();

    /** The descriptor set layout bakes write pages through. Render thread. */
    private long setLayout = VK_NULL_HANDLE;

    /** The pool page sets come from. */
    private long descriptorPool = VK_NULL_HANDLE;

    /** The sampler pages are read through: linear, clamped, no mips. */
    private long sampler = VK_NULL_HANDLE;

    /** Each frame's upload buffer. */
    private final SharedBuffer[] uploads = new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * Pack a tile's corner.
     *
     * @param x Its left.
     * @param y Its top.
     * @return The key.
     */
    private static long pack(int x, int y) {
        return ((long) x << Integer.SIZE) | y;
    }

    /**
     * A packed corner's left.
     *
     * @param packed The key.
     * @return Its x.
     */
    private static int unpackX(long packed) {
        return (int) (packed >>> Integer.SIZE);
    }

    /**
     * A packed corner's top.
     *
     * @param packed The key.
     * @return Its y.
     */
    private static int unpackY(long packed) {
        return (int) packed;
    }

    /**
     * Work out what each texel of a world's baked clusters samples. A baked child's texels already
     * name the materials and places they sample, so a texel over one samples those, at the nearest
     * of its texels: every tile then samples only the world's own materials, and can be baked
     * whether or not its children are. Safe from any thread.
     *
     * @param dag The world.
     * @param cache The scene's materials, which clusters' material indices are into.
     * @return The plan, or null if nothing in it is baked.
     */
    public static Plan plan(@NonNull LodDag dag, @NonNull MaterialCache cache) {
        final List<LodDag.Cluster> baked = new ArrayList<>();
        for (LodDag.Group group : dag.groups()) {
            for (int output : group.bakeTiles().length > 0 ? group.outputs() : new int[0]) {
                if (dag.bakeOf(output) != null) {
                    baked.add(dag.clusters().get(output));
                }
            }
        }
        if (baked.isEmpty()) {
            return null;
        }
        // Children before parents, which read their records
        baked.sort(
                Comparator.comparingInt(LodDag.Cluster::level)
                        .thenComparingInt(LodDag.Cluster::id));
        final Map<Integer, Baked> out = new HashMap<>();
        for (LodDag.Cluster cluster : baked) {
            out.put(cluster.id(), bake(dag, cluster, out, cache));
        }
        return new Plan(out);
    }

    /**
     * Give a loaded cluster a tile and queue its bake. Render thread.
     *
     * @param world The world's id.
     * @param baked The cluster's bake.
     * @return The cluster's mesh as drawn: texture coordinates in the tile's page, every triangle
     *     the page's material.
     */
    public ClusterMeshes.Mesh load(long world, @NonNull Baked baked) {
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        final Tile tile;
        lock.lock();
        try {
            unloadLocked(world, baked.cluster().id());
            tile = allocate(Math.min(baked.width(), PAGE), Math.min(baked.height(), PAGE), cache);
            final Job job = new Job(tile, baked, world);
            jobs.add(job);
            byWorld.computeIfAbsent(world, w -> new HashMap<>())
                    .put(baked.cluster().id(), new Loaded(tile, job));
        } finally {
            lock.unlock();
        }
        return ClusterMeshes.of(inPage(baked.cluster(), tile));
    }

    /**
     * Whether a loaded cluster's tile has been baked: its job was taken, and so is written before
     * anything that frame draws.
     *
     * @param world The world's id.
     * @param cluster The cluster's id.
     * @return True once it has.
     */
    public boolean isBaked(long world, int cluster) {
        lock.lock();
        try {
            final Map<Integer, Loaded> loaded = byWorld.get(world);
            final Loaded tile = loaded == null ? null : loaded.get(cluster);
            return tile != null && tile.job == null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Give back a cluster's tile, and drop its bake if it is still waiting. Render thread.
     *
     * @param world The world's id.
     * @param cluster The cluster's id.
     */
    public void unload(long world, int cluster) {
        lock.lock();
        try {
            unloadLocked(world, cluster);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Give back a cluster's tile. Lock held.
     *
     * @param world The world's id.
     * @param cluster The cluster's id.
     */
    private void unloadLocked(long world, int cluster) {
        final Map<Integer, Loaded> loaded = byWorld.get(world);
        final Loaded tile = loaded == null ? null : loaded.remove(cluster);
        if (tile == null) {
            return;
        }
        if (tile.job != null) {
            jobs.remove(tile.job);
        }
        pages.get(tile.tile.page())
                .give(tile.tile.x(), tile.tile.y(), tile.tile.width(), tile.tile.height());
        if (loaded.isEmpty()) {
            byWorld.remove(world);
        }
    }

    /**
     * What is loaded. Safe from any thread.
     *
     * @return The tiles, their texels and the pages.
     */
    public Stats stats() {
        lock.lock();
        try {
            long tiles = 0;
            long texels = 0;
            for (Map<Integer, Loaded> loaded : byWorld.values()) {
                for (Loaded tile : loaded.values()) {
                    ++tiles;
                    texels += tile.tile.texels();
                }
            }
            int made = 0;
            for (Page page : pages) {
                made += page.color == null ? 0 : 1;
            }
            return new Stats(tiles, texels, made);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a tile from the first page with room, starting a page if none has. Lock held.
     *
     * @param width The tile's width.
     * @param height The tile's height.
     * @param cache The scene's materials, for a new page's.
     * @return The tile.
     */
    private Tile allocate(int width, int height, @NonNull MaterialCache cache) {
        for (Page page : pages) {
            final long corner = page.take(width, height);
            if (corner >= 0) {
                return new Tile(page.index, unpackX(corner), unpackY(corner), width, height);
            }
        }
        if (pages.size() >= MAX_PAGES) {
            throw new IllegalStateException("Out of level of detail bake pages");
        }
        final Material material = new Material();
        material.setBakedData(true);
        final Page page = new Page(pages.size(), material, cache.addMaterial(material));
        cache.setDirty(true);
        pages.add(page);
        final long corner = page.take(width, height);
        return new Tile(page.index, unpackX(corner), unpackY(corner), width, height);
    }

    /**
     * A baked cluster as it is drawn: its texture coordinates moved from its tile into the page,
     * and every triangle the page's material.
     *
     * @param cluster The cluster, with coordinates in its tile from 0 to 1.
     * @param tile Its tile.
     * @return The cluster to draw.
     */
    private LodDag.Cluster inPage(LodDag.@NonNull Cluster cluster, @NonNull Tile tile) {
        final int[] attributes = cluster.attributes().clone();
        for (int at = 0; at + Attributes.VALUES <= attributes.length; at += Attributes.VALUES) {
            final float[] uv = Attributes.textureCoordinates(attributes, at);
            final float u = (tile.x() + uv[0] * tile.width()) / PAGE;
            final float v = (tile.y() + uv[1] * tile.height()) / PAGE;
            attributes[at + 2] =
                    (Float.floatToFloat16(u) & 0xFFFF) | (Float.floatToFloat16(v) << Short.SIZE);
        }
        final int[] materials = new int[cluster.triangleCount()];
        final Page page;
        lock.lock();
        try {
            page = pages.get(tile.page());
        } finally {
            lock.unlock();
        }
        Arrays.fill(materials, page.materialIndex);
        return new LodDag.Cluster(
                cluster.id(),
                cluster.level(),
                cluster.corners(),
                materials,
                cluster.visibility(),
                attributes,
                cluster.error(),
                cluster.bound(),
                cluster.tight(),
                cluster.producer(),
                cluster.section());
    }

    /**
     * Work out what each texel of a cluster's tile samples: its child's material and texture
     * coordinates there, or what the child's own tile samples there if it was baked too, the
     * source's tangent frame and the cluster's, and how much of the source's texture a texel spans.
     *
     * @param dag The world.
     * @param cluster The baked cluster, as built.
     * @param planned Clusters baked so far, for children that were baked too.
     * @param cache The scene's materials.
     * @return The bake.
     */
    private static Baked bake(
            @NonNull LodDag dag,
            LodDag.@NonNull Cluster cluster,
            @NonNull Map<Integer, Baked> planned,
            @NonNull MaterialCache cache) {
        final BakeTexels bake = dag.bakeOf(cluster.id());
        final int width = bake.width();
        final int height = bake.height();
        final int texels = width * height;
        final ByteBuffer records =
                ByteBuffer.allocate(texels * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final List<Material> sources = new ArrayList<>();
        final Map<Material, Integer> slots = new IdentityHashMap<>();
        final double[] weights = new double[CORNERS];
        final int[] child = new int[Attributes.VALUES];
        final int[] own = new int[Attributes.VALUES];
        final float[] direction = new float[AXES];
        final double worldPerTexel = worldPerTexel(cluster, width, height);
        for (int texel = 0; texel < texels; ++texel) {
            final int at = texel * RECORD_SIZE;
            final int childId = bake.childCluster(texel);
            if (childId == BakeTexels.EMPTY) {
                records.putInt(at, EMPTY);
                continue;
            }
            final LodDag.Cluster source = dag.clusters().get(childId);
            final int t = bake.childTriangle(texel);
            final int[] attributes = source.attributes();
            for (int c = 0; c < CORNERS; ++c) {
                weights[c] = bake.childWeight(texel, c);
            }
            final Material material;
            float u = 0;
            float v = 0;
            double footprint = 0;
            final Baked childBake = planned.get(childId);
            if (childBake != null) {
                // What the child's tile holds there, from its nearest texel with anything
                Attributes.blend(attributes, t * Attributes.PER_TRIANGLE, weights, child, 0);
                final float[] place = Attributes.textureCoordinates(child, 0);
                final int from = nearestFilled(childBake, place[0], place[1]);
                if (from < 0) {
                    records.putInt(at, EMPTY);
                    continue;
                }
                final ByteBuffer was = childBake.records();
                final int in = from * RECORD_SIZE;
                material = childBake.sources().get(was.getInt(in));
                u = was.getFloat(in + Integer.BYTES);
                v = was.getFloat(in + 2 * Integer.BYTES);
                child[0] = was.getInt(in + 3 * Integer.BYTES);
                child[1] = was.getInt(in + 4 * Integer.BYTES);
                footprint = was.getFloat(in + 7 * Integer.BYTES);
                if (childBake.worldPerTexel() > 0) {
                    footprint *= worldPerTexel / childBake.worldPerTexel();
                }
            } else {
                material = cache.getMaterial(source.materials()[t]);
                final boolean textured =
                        Attributes.textured(attributes, t)
                                && !Attributes.isAveraged(attributes, t * Attributes.PER_TRIANGLE);
                if (textured) {
                    Attributes.blend(attributes, t * Attributes.PER_TRIANGLE, weights, child, 0);
                    final float[] uv = Attributes.textureCoordinates(child, 0);
                    u = uv[0];
                    v = uv[1];
                    footprint = uvPerWorld(source, t) * worldPerTexel;
                } else {
                    // No texture to place: the triangle's own facing, any tangent across it
                    faceFrame(source.corners(), t, direction, child);
                }
            }
            final int slot =
                    slots.computeIfAbsent(
                            material,
                            m -> {
                                sources.add(m);
                                return sources.size() - 1;
                            });
            for (int c = 0; c < CORNERS; ++c) {
                weights[c] = bake.ownWeight(texel, c);
            }
            Attributes.blend(
                    cluster.attributes(),
                    bake.ownTriangle(texel) * Attributes.PER_TRIANGLE,
                    weights,
                    own,
                    0);
            records.putInt(at, slot);
            records.putFloat(at + Integer.BYTES, u);
            records.putFloat(at + 2 * Integer.BYTES, v);
            records.putInt(at + 3 * Integer.BYTES, child[0]);
            records.putInt(at + 4 * Integer.BYTES, child[1]);
            records.putInt(at + 5 * Integer.BYTES, own[0]);
            records.putInt(at + 6 * Integer.BYTES, own[1]);
            records.putFloat(at + 7 * Integer.BYTES, (float) footprint);
        }
        return new Baked(cluster, width, height, sources, records, worldPerTexel);
    }

    /** How far from a place in a tile to look for a texel with anything in it, in texels. */
    private static final int SEARCH = 2;

    /**
     * The texel of a baked tile nearest a place in it that stands for something.
     *
     * @param baked The bake.
     * @param u The place across the tile, from 0 to 1.
     * @param v The place down it.
     * @return The texel, row by row, or -1 if none is near.
     */
    private static int nearestFilled(@NonNull Baked baked, float u, float v) {
        final int width = baked.width();
        final int height = baked.height();
        final double x = u * width;
        final double y = v * height;
        final int cx = Math.clamp((int) Math.floor(x), 0, width - 1);
        final int cy = Math.clamp((int) Math.floor(y), 0, height - 1);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int ty = Math.max(0, cy - SEARCH); ty <= Math.min(height - 1, cy + SEARCH); ++ty) {
            for (int tx = Math.max(0, cx - SEARCH); tx <= Math.min(width - 1, cx + SEARCH); ++tx) {
                final int texel = ty * width + tx;
                if (baked.records().getInt(texel * RECORD_SIZE) == EMPTY) {
                    continue;
                }
                final double dx = tx + 0.5 - x;
                final double dy = ty + 0.5 - y;
                final double distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = texel;
                }
            }
        }
        return best;
    }

    /**
     * How far apart in the world a cluster's tile texels are, from the triangle whose tile edge is
     * longest for its length.
     *
     * @param cluster The cluster, with coordinates in its tile from 0 to 1.
     * @param width Its tile's width.
     * @param height Its tile's height.
     * @return World units a texel spans.
     */
    private static double worldPerTexel(LodDag.@NonNull Cluster cluster, int width, int height) {
        final float[] corners = cluster.corners();
        final int[] attributes = cluster.attributes();
        double best = 0;
        for (int t = 0; t < cluster.triangleCount(); ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                final int d = (c + 1) % CORNERS;
                final float[] a =
                        Attributes.textureCoordinates(
                                attributes, t * Attributes.PER_TRIANGLE + c * Attributes.VALUES);
                final float[] b =
                        Attributes.textureCoordinates(
                                attributes, t * Attributes.PER_TRIANGLE + d * Attributes.VALUES);
                final double texels = Math.hypot((b[0] - a[0]) * width, (b[1] - a[1]) * height);
                if (texels > 1) {
                    best = Math.max(best, distance(corners, t, c, d) / texels);
                }
            }
        }
        return best;
    }

    /**
     * How fast a triangle's texture coordinates change across it, at most, per world unit.
     *
     * @param cluster The cluster.
     * @param t The triangle.
     * @return Texture coordinate units per world unit.
     */
    private static double uvPerWorld(LodDag.@NonNull Cluster cluster, int t) {
        double best = 0;
        for (int c = 0; c < CORNERS; ++c) {
            final int d = (c + 1) % CORNERS;
            final float[] a =
                    Attributes.textureCoordinates(
                            cluster.attributes(),
                            t * Attributes.PER_TRIANGLE + c * Attributes.VALUES);
            final float[] b =
                    Attributes.textureCoordinates(
                            cluster.attributes(),
                            t * Attributes.PER_TRIANGLE + d * Attributes.VALUES);
            final double length = distance(cluster.corners(), t, c, d);
            if (length > 0) {
                best = Math.max(best, Math.hypot(b[0] - a[0], b[1] - a[1]) / length);
            }
        }
        return best;
    }

    /**
     * How far apart two of a triangle's corners are.
     *
     * @param corners The corners.
     * @param t The triangle.
     * @param c One corner.
     * @param d The other.
     * @return The distance.
     */
    private static double distance(float @NonNull [] corners, int t, int c, int d) {
        double sum = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            final double step =
                    corners[t * TRIANGLE_VALUES + d * AXES + axis]
                            - corners[t * TRIANGLE_VALUES + c * AXES + axis];
            sum += step * step;
        }
        return Math.sqrt(sum);
    }

    /**
     * A triangle's facing and a tangent across it, packed as attributes are.
     *
     * @param corners The corners.
     * @param t The triangle.
     * @param scratch Three floats to use.
     * @param out Receives the packed normal, then the packed tangent.
     */
    private static void faceFrame(
            float @NonNull [] corners, int t, float @NonNull [] scratch, int @NonNull [] out) {
        final int base = t * TRIANGLE_VALUES;
        final float[] e1 = new float[AXES];
        final float[] e2 = new float[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            e1[axis] = corners[base + AXES + axis] - corners[base + axis];
            e2[axis] = corners[base + 2 * AXES + axis] - corners[base + axis];
        }
        scratch[0] = e1[1] * e2[2] - e1[2] * e2[1];
        scratch[1] = e1[2] * e2[0] - e1[0] * e2[2];
        scratch[2] = e1[0] * e2[1] - e1[1] * e2[0];
        if (scratch[0] == 0 && scratch[1] == 0 && scratch[2] == 0) {
            scratch[1] = 1;
        }
        out[0] = Attributes.encodeDirection(scratch);
        if (e1[0] == 0 && e1[1] == 0 && e1[2] == 0) {
            e1[0] = 1;
        }
        out[1] = Attributes.encodeDirection(e1) & ~1;
    }

    /**
     * Give back a world's tiles, and drop its waiting bakes. Safe from any thread.
     *
     * @param world The world's id.
     */
    public void release(long world) {
        lock.lock();
        try {
            final Map<Integer, Loaded> loaded = byWorld.get(world);
            if (loaded != null) {
                for (Integer cluster : new ArrayList<>(loaded.keySet())) {
                    unloadLocked(world, cluster);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take the next jobs, up to a budget of texels, making the pages they write if they aren't yet.
     * Render thread.
     *
     * @param state The Vulkan state.
     * @param texels The most texels to take, though at least one job is taken.
     * @return The jobs.
     */
    public List<Job> take(@NonNull VulkanState state, long texels) {
        final List<Job> taken = new ArrayList<>();
        final List<Page> toMake = new ArrayList<>();
        lock.lock();
        try {
            long total = 0;
            while (!jobs.isEmpty() && (taken.isEmpty() || total < texels)) {
                final Job job = jobs.remove(0);
                taken.add(job);
                byWorld.get(job.world()).get(job.baked().cluster().id()).job = null;
                total += job.tile().texels();
                final Page page = pages.get(job.tile().page());
                if (page.color == null && !toMake.contains(page)) {
                    toMake.add(page);
                }
            }
        } finally {
            lock.unlock();
        }
        for (Page page : toMake) {
            make(state, page);
        }
        return taken;
    }

    /**
     * The descriptor set a job's page is written through.
     *
     * @param job The job.
     * @return The set.
     */
    public long setOf(@NonNull Job job) {
        lock.lock();
        try {
            return pages.get(job.tile().page()).set;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The layout of the sets pages are written through, made the first time it is asked for. Render
     * thread.
     *
     * @param state The Vulkan state.
     * @return The layout.
     */
    public long getSetLayout(@NonNull VulkanState state) {
        if (setLayout != VK_NULL_HANDLE) {
            return setLayout;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final LongBuffer output = stack.callocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            for (int i = 0; i < 2; ++i) {
                bindings.get(i)
                        .binding(i == 0 ? COLOR_BINDING : DATA_BINDING)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical,
                            VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pBindings(bindings),
                            null,
                            output));
            setLayout = output.get(0);
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(2 * MAX_PAGES);
            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical,
                            VkDescriptorPoolCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .maxSets(MAX_PAGES)
                                    .pPoolSizes(sizes),
                            null,
                            output));
            descriptorPool = output.get(0);
            checkError(
                    vkCreateSampler(
                            state.device.logical,
                            VkSamplerCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .magFilter(VK_FILTER_LINEAR)
                                    .minFilter(VK_FILTER_LINEAR)
                                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                                    .minLod(0)
                                    .maxLod(0),
                            null,
                            output));
            sampler = output.get(0);
        }
        return setLayout;
    }

    /**
     * Make a page's textures, cleared and in the general layout, register them for sampling and
     * give them to its material. Render thread.
     *
     * @param state The Vulkan state.
     * @param page The page.
     */
    private void make(@NonNull VulkanState state, @NonNull Page page) {
        getSetLayout(state);
        final long[] colorImage = new long[2];
        final long[] dataImage = new long[2];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final LongBuffer output = stack.callocLong(1);
            image(state, true, colorImage, stack);
            image(state, false, dataImage, stack);
            final long colorSampled = view(state, colorImage[0], VK_FORMAT_R8G8B8A8_SRGB, stack);
            page.colorStorageView = view(state, colorImage[0], VK_FORMAT_R8G8B8A8_UNORM, stack);
            final long dataView = view(state, dataImage[0], VK_FORMAT_R8G8B8A8_UNORM, stack);
            state.immediateCommands.submit(
                    state,
                    commandBuffer -> {
                        try (MemoryStack inner = MemoryStack.stackPush()) {
                            clear(commandBuffer, colorImage[0], inner);
                            clear(commandBuffer, dataImage[0], inner);
                        }
                    });
            page.color = register(state, colorImage, colorSampled, Format.R8G8B8A8_SRGB);
            page.data = register(state, dataImage, dataView, Format.R8G8B8A8_UNORM);
            final LongBuffer sets = stack.mallocLong(1);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical,
                            VkDescriptorSetAllocateInfo.calloc(stack)
                                    .sType$Default()
                                    .descriptorPool(descriptorPool)
                                    .pSetLayouts(stack.longs(setLayout)),
                            sets));
            page.set = sets.get(0);
            VkDescriptorImageInfo.Buffer color = VkDescriptorImageInfo.calloc(1, stack);
            color.get(0).imageView(page.colorStorageView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer data = VkDescriptorImageInfo.calloc(1, stack);
            data.get(0).imageView(dataView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(page.set)
                    .dstBinding(COLOR_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(color);
            writes.get(1)
                    .sType$Default()
                    .dstSet(page.set)
                    .dstBinding(DATA_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(data);
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
        page.material.setTexture(page.color);
        page.material.setNormalMap(page.data);
        GraphicsManager.getScene().getMaterialCache().setDirty(true);
        log.debug("Made level of detail bake page {}", page.index);
    }

    /**
     * Make a page image.
     *
     * @param state The Vulkan state.
     * @param color Whether it is the color page, readable as sRGB or UNORM.
     * @param out Receives the image and its allocation.
     * @param stack The stack to allocate on.
     */
    private static void image(
            @NonNull VulkanState state,
            boolean color,
            long @NonNull [] out,
            @NonNull MemoryStack stack) {
        final LongBuffer image = stack.callocLong(1);
        final PointerBuffer allocation = stack.callocPointer(1);
        VkImageCreateInfo info =
                VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(VK_FORMAT_R8G8B8A8_UNORM)
                        .extent(e -> e.set(PAGE, PAGE, 1))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(
                                VK_IMAGE_USAGE_STORAGE_BIT
                                        | VK_IMAGE_USAGE_SAMPLED_BIT
                                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        if (color) {
            info.flags(VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT)
                    .pNext(
                            VkImageFormatListCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pViewFormats(
                                            stack.ints(
                                                    VK_FORMAT_R8G8B8A8_UNORM,
                                                    VK_FORMAT_R8G8B8A8_SRGB)));
        }
        checkError(
                vmaCreateImage(
                        state.vmaAllocator,
                        info,
                        VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO),
                        image,
                        allocation,
                        null));
        out[0] = image.get(0);
        out[1] = allocation.get(0);
    }

    /**
     * Make a view of a page image.
     *
     * @param state The Vulkan state.
     * @param image The image.
     * @param format The view's format.
     * @param stack The stack to allocate on.
     * @return The view.
     */
    private static long view(
            @NonNull VulkanState state, long image, int format, @NonNull MemoryStack stack) {
        final LongBuffer output = stack.callocLong(1);
        final VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack);
        if (format == VK_FORMAT_R8G8B8A8_SRGB) {
            // sRGB can't be stored to, so this view of the storage image is for sampling only
            info.pNext(
                    VkImageViewUsageCreateInfo.calloc(stack)
                            .sType$Default()
                            .usage(VK_IMAGE_USAGE_SAMPLED_BIT));
        }
        checkError(
                vkCreateImageView(
                        state.device.logical,
                        info.sType$Default()
                                .image(image)
                                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                .format(format)
                                .subresourceRange(
                                        range ->
                                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                        .levelCount(1)
                                                        .layerCount(1)),
                        null,
                        output));
        return output.get(0);
    }

    /**
     * Clear a new page image to zero and leave it in the general layout, ready to be written and
     * sampled.
     *
     * @param commandBuffer The command buffer.
     * @param image The image.
     * @param stack The stack to allocate on.
     */
    private static void clear(
            @NonNull VkCommandBuffer commandBuffer, long image, @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_NONE)
                .srcAccessMask(VK_ACCESS_2_NONE)
                .dstStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(
                        range ->
                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
        VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
        range.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdClearColorImage(
                commandBuffer,
                image,
                VK_IMAGE_LAYOUT_GENERAL,
                VkClearColorValue.calloc(stack),
                range);
        barrier.get(0)
                .srcStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .dstStageMask(
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                                | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                .dstAccessMask(
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK_IMAGE_LAYOUT_GENERAL);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
    }

    /**
     * Register a page image for sampling, through bindless textures, in the general layout.
     *
     * @param state The Vulkan state.
     * @param image The image and its allocation.
     * @param view The view shaders sample.
     * @param format The view's format.
     * @return Its handle.
     */
    private TextureHandle register(
            @NonNull VulkanState state, long @NonNull [] image, long view, @NonNull Format format) {
        final TextureInfoVulkan info =
                new TextureInfoVulkan()
                        .texture(image[0])
                        .textureAllocation(image[1])
                        .view(view)
                        .sampler(sampler);
        info.format = format;
        info.layout = VK_IMAGE_LAYOUT_GENERAL;
        state.bindlessTextures.reserve(info);
        final TextureHandle handle = state.textureRegistry.add(OWNER, info, PAGE, PAGE, format);
        state.bindlessTextures.update(state, info);
        state.textureRegistry.markResident(handle);
        return handle;
    }

    /**
     * Write jobs into a frame's upload buffer: each job's source materials, then its records, each
     * part on a 16 byte boundary. The buffer is only rewritten once that frame is done with it.
     * Render thread.
     *
     * @param frame The frame in flight.
     * @param state The Vulkan state.
     * @param batch The jobs.
     * @param offsets Receives each job's materials' and records' offsets, two a job.
     * @return The buffer.
     */
    public SharedBuffer write(
            int frame,
            @NonNull VulkanState state,
            @NonNull List<Job> batch,
            long @NonNull [] offsets) {
        long bytes = 0;
        for (int i = 0; i < batch.size(); ++i) {
            final Job job = batch.get(i);
            offsets[i * 2] = bytes;
            bytes +=
                    align(
                            (long) job.baked().sources().size()
                                    * ShaderBindings.Scene.Material.SIZEOF);
            offsets[i * 2 + 1] = bytes;
            bytes += align(job.baked().records().capacity());
        }
        SharedBuffer buffer = uploads[frame];
        if (buffer == null) {
            buffer =
                    SharedBuffer.allocate(
                            Math.max(bytes, 1), state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            uploads[frame] = buffer;
        }
        buffer.ensureCapacity(Math.max(bytes, 1), state);
        final ByteBuffer data =
                MemoryUtil.memByteBuffer(buffer.allocationInfo.pMappedData(), (int) bytes)
                        .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < batch.size(); ++i) {
            final Job job = batch.get(i);
            data.position((int) offsets[i * 2]);
            for (Material material : job.baked().sources()) {
                SceneRender.writeMaterial(data, material, state.textureRegistry);
            }
            data.position((int) offsets[i * 2 + 1]);
            data.put(job.baked().records().duplicate().clear());
        }
        return buffer;
    }

    /**
     * Round a size up to 16 bytes.
     *
     * @param bytes The size.
     * @return The rounded size.
     */
    private static long align(long bytes) {
        return (bytes + 15) & ~15L;
    }

    /**
     * Whether any job is waiting.
     *
     * @return True if one is.
     */
    public boolean hasJobs() {
        lock.lock();
        try {
            return !jobs.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Destroy the pages and everything else. Render thread, with the GPU idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        lock.lock();
        try {
            final MaterialCache cache =
                    GraphicsManager.getScene() == null
                            ? null
                            : GraphicsManager.getScene().getMaterialCache();
            for (Page page : pages) {
                for (TextureHandle handle : new TextureHandle[] {page.color, page.data}) {
                    final TextureInfoVulkan info = state.textureRegistry.remove(handle);
                    if (info != null) {
                        state.bindlessTextures.release(state, info);
                        info.destroy(state);
                    }
                }
                if (page.colorStorageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(state.device.logical, page.colorStorageView, null);
                }
                if (cache != null) {
                    cache.removeMaterial(page.material);
                }
            }
            pages.clear();
            jobs.clear();
            byWorld.clear();
        } finally {
            lock.unlock();
        }
        for (int i = 0; i < uploads.length; ++i) {
            if (uploads[i] != null) {
                SharedBuffer.free(uploads[i], state);
                uploads[i] = null;
            }
        }
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
            descriptorPool = VK_NULL_HANDLE;
        }
        if (setLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(state.device.logical, setLayout, null);
            setLayout = VK_NULL_HANDLE;
        }
        if (sampler != VK_NULL_HANDLE) {
            vkDestroySampler(state.device.logical, sampler, null);
            sampler = VK_NULL_HANDLE;
        }
    }
}
