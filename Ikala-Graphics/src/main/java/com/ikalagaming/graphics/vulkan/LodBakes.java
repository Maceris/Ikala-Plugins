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
 * <p>When a world with baked clusters is added, {@link #plan} gives each a tile, points its texture
 * coordinates into the tile's page and its triangles at the page's material, and works out from its
 * {@link BakeTexels} what each texel samples; {@code LodBake} then bakes a budget of tiles a frame,
 * children before parents, since parents sample their children's tiles. Until its tile is baked a
 * cluster draws the empty page.
 *
 * <p>Planning, attaching and releasing are safe from any thread; pages are made and jobs taken on
 * the render thread.
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

    /**
     * One tile to bake.
     *
     * @param tile The tile.
     * @param level Its cluster's level, so children bake first.
     * @param sources The materials its texels sample, which records refer to by index.
     * @param records Each texel's record, row by row, {@link #RECORD_SIZE} bytes each.
     * @param world The world it is for, set once attached.
     */
    public record Job(
            @NonNull Tile tile,
            int level,
            @NonNull List<Material> sources,
            @NonNull ByteBuffer records,
            long world) {}

    /**
     * What a world's bakes take: its baked clusters as they are drawn, their tiles, and the jobs
     * that fill them.
     *
     * @param clusters Baked clusters as drawn, by id: texture coordinates in their page and its
     *     material.
     * @param tiles The tiles taken.
     * @param jobs The jobs, children first.
     */
    public record Plan(
            @NonNull Map<Integer, LodDag.Cluster> clusters,
            @NonNull List<Tile> tiles,
            @NonNull List<Job> jobs) {

        /**
         * A cluster as it is drawn.
         *
         * @param original The cluster as built.
         * @return The baked one, or the original if it isn't baked.
         */
        public LodDag.Cluster cluster(LodDag.@NonNull Cluster original) {
            return clusters.getOrDefault(original.id(), original);
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

    /** Jobs waiting for the GPU, children first. */
    private final List<Job> jobs = new ArrayList<>();

    /** Each world's tiles. */
    private final Map<Long, List<Tile>> byWorld = new HashMap<>();

    /** Each world's owner. */
    private final Map<Long, String> owners = new HashMap<>();

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
     * Give a world's baked clusters tiles, and work out what each texel samples. Safe from any
     * thread.
     *
     * @param dag The world.
     * @param cache The scene's materials, which clusters' material indices are into.
     * @return The plan, or null if nothing in it is baked.
     */
    public Plan plan(@NonNull LodDag dag, @NonNull MaterialCache cache) {
        final List<int[]> baked = new ArrayList<>();
        for (LodDag.Group group : dag.groups()) {
            for (int i = 0; i < group.bakeTiles().length; ++i) {
                if (dag.bakeOf(group.outputs()[i]) != null) {
                    baked.add(new int[] {group.outputs()[i], group.bakeTiles()[i]});
                }
            }
        }
        if (baked.isEmpty()) {
            return null;
        }
        baked.sort(Comparator.comparingInt(b -> b[0]));
        final Map<Integer, LodDag.Cluster> clusters = new HashMap<>();
        final Map<Integer, Tile> tiles = new HashMap<>();
        lock.lock();
        try {
            for (int[] b : baked) {
                tiles.put(
                        b[0],
                        allocate(
                                Math.min(LodDag.tileWidth(b[1]), PAGE),
                                Math.min(LodDag.tileHeight(b[1]), PAGE),
                                cache));
            }
        } finally {
            lock.unlock();
        }
        for (int[] b : baked) {
            final LodDag.Cluster cluster = dag.clusters().get(b[0]);
            clusters.put(b[0], inPage(cluster, tiles.get(b[0])));
        }
        final List<Job> planned = new ArrayList<>();
        for (int[] b : baked) {
            final LodDag.Cluster cluster = dag.clusters().get(b[0]);
            planned.add(job(dag, cluster, tiles.get(b[0]), clusters, cache));
        }
        planned.sort(Comparator.comparingInt(Job::level));
        return new Plan(clusters, new ArrayList<>(tiles.values()), planned);
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
     * coordinates there, the child's tangent frame and the cluster's, and how much of the child's
     * texture a texel spans.
     *
     * @param dag The world.
     * @param cluster The baked cluster, as built.
     * @param tile Its tile.
     * @param drawn Baked clusters as drawn, for children that were baked too.
     * @param cache The scene's materials.
     * @return The job.
     */
    private Job job(
            @NonNull LodDag dag,
            LodDag.@NonNull Cluster cluster,
            @NonNull Tile tile,
            @NonNull Map<Integer, LodDag.Cluster> drawn,
            @NonNull MaterialCache cache) {
        final BakeTexels bake = dag.bakeOf(cluster.id());
        final int texels = bake.width() * bake.height();
        final ByteBuffer records =
                ByteBuffer.allocate(texels * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final List<Material> sources = new ArrayList<>();
        final Map<Material, Integer> slots = new IdentityHashMap<>();
        final double[] weights = new double[CORNERS];
        final int[] child = new int[Attributes.VALUES];
        final int[] own = new int[Attributes.VALUES];
        final float[] direction = new float[AXES];
        final double worldPerTexel = worldPerTexel(cluster, bake.width(), bake.height());
        for (int texel = 0; texel < texels; ++texel) {
            final int at = texel * RECORD_SIZE;
            final int childId = bake.childCluster(texel);
            if (childId == BakeTexels.EMPTY) {
                records.putInt(at, EMPTY);
                continue;
            }
            final LodDag.Cluster source = drawn.getOrDefault(childId, dag.clusters().get(childId));
            final int t = bake.childTriangle(texel);
            final Material material = cache.getMaterial(source.materials()[t]);
            final int slot =
                    slots.computeIfAbsent(
                            material,
                            m -> {
                                sources.add(m);
                                return sources.size() - 1;
                            });
            final int[] attributes = source.attributes();
            final boolean textured =
                    Attributes.textured(attributes, t)
                            && !Attributes.isAveraged(attributes, t * Attributes.PER_TRIANGLE);
            float u = 0;
            float v = 0;
            double footprint = 0;
            if (textured) {
                for (int c = 0; c < CORNERS; ++c) {
                    weights[c] = bake.childWeight(texel, c);
                }
                Attributes.blend(attributes, t * Attributes.PER_TRIANGLE, weights, child, 0);
                final float[] uv = Attributes.textureCoordinates(child, 0);
                u = uv[0];
                v = uv[1];
                footprint = uvPerWorld(source, t) * worldPerTexel;
            } else {
                // No texture to place: the triangle's own facing, any tangent across it
                faceFrame(source.corners(), t, direction, child);
            }
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
        return new Job(tile, cluster.level(), sources, records, 0);
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
     * Tie a plan's tiles and jobs to the world they were planned for, and queue its jobs. Safe from
     * any thread.
     *
     * @param world The world's id.
     * @param owner The key of the plugin that owns it.
     * @param plan The plan.
     */
    public void attach(long world, @NonNull String owner, @NonNull Plan plan) {
        lock.lock();
        try {
            byWorld.put(world, new ArrayList<>(plan.tiles()));
            owners.put(world, owner);
            for (Job job : plan.jobs()) {
                jobs.add(new Job(job.tile(), job.level(), job.sources(), job.records(), world));
            }
            jobs.sort(Comparator.comparingInt(Job::level));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Give back a world's tiles, and drop its jobs. Safe from any thread.
     *
     * @param world The world's id.
     */
    public void release(long world) {
        lock.lock();
        try {
            final List<Tile> tiles = byWorld.remove(world);
            owners.remove(world);
            jobs.removeIf(job -> job.world() == world);
            if (tiles != null) {
                for (Tile tile : tiles) {
                    pages.get(tile.page()).give(tile.x(), tile.y(), tile.width(), tile.height());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Give back the tiles of every world a plugin owns. Safe from any thread.
     *
     * @param owner The plugin's key.
     */
    public void releaseAllOwnedBy(@NonNull String owner) {
        final List<Long> worlds = new ArrayList<>();
        lock.lock();
        try {
            for (Map.Entry<Long, String> entry : owners.entrySet()) {
                if (entry.getValue().equals(owner)) {
                    worlds.add(entry.getKey());
                }
            }
        } finally {
            lock.unlock();
        }
        worlds.forEach(this::release);
    }

    /**
     * Take the next jobs, up to a budget of texels, making the pages they write if they aren't yet.
     * Render thread.
     *
     * @param state The Vulkan state.
     * @param texels The most texels to take, though at least one job is taken.
     * @return The jobs, children first.
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
            bytes += align((long) job.sources().size() * ShaderBindings.Scene.Material.SIZEOF);
            offsets[i * 2 + 1] = bytes;
            bytes += align(job.records().capacity());
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
            for (Material material : job.sources()) {
                SceneRender.writeMaterial(data, material, state.textureRegistry);
            }
            data.position((int) offsets[i * 2 + 1]);
            data.put(job.records().duplicate().clear());
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
            owners.clear();
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
