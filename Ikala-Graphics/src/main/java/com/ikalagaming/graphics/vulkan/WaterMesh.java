package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.lod.LongIntMap;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the water's surfaces from its tiles, see {@code WaterRender}: two triangles for each cell
 * that holds water, and side faces where the water ends, so a surface raised by waves never shows
 * as a sheet. Each corner of the surface has a calm factor, how much of the waves it takes: less in
 * shallow water, and fading out over the last {@link #EDGE_CELLS} cells before an edge, land or a
 * step to water at another level, where waves would otherwise rise past the bank. Sides go down
 * from the edge of a cell whose neighbor holds no water to the cell's bottom, or to the neighbor's
 * surface where it stands lower, which makes banks, steps between levels and the tops of
 * waterfalls. Neighbors are found across tiles that share a cell size.
 *
 * <p>Surface corners are shared by the cells around them, and drawn through indices, so the waves
 * are worked out once a corner. Each vertex is {@link #VERTEX_FLOATS} floats: its place relative to
 * its tile's corner, its tile's index as an int's bits, its calm factor, or on a side how far the
 * water has fallen from the lip, for sides the face's outward normal along x and z, zero on the
 * surface, how fast the water flows there along x and z, and how deep it is, on a side at the lip.
 */
public final class WaterMesh {

    /** Floats in a vertex. */
    public static final int VERTEX_FLOATS = 10;

    /** Where in a vertex its tile's index is, as an int's bits. */
    public static final int TILE_FLOAT = 3;

    /** Where in a vertex its calm factor is, or on a side how far the water has fallen. */
    public static final int CALM_FLOAT = 4;

    /** Where in a vertex its side's outward normal is, x then z. */
    public static final int NORMAL_FLOAT = 5;

    /** Where in a vertex how fast the water flows is, x then z, in meters a second. */
    public static final int FLOW_FLOAT = 7;

    /** Where in a vertex how deep the water is, in meters. */
    public static final int DEPTH_FLOAT = 9;

    /** How deep water is taken to be where the tile doesn't give the bottom, in meters. */
    public static final float UNKNOWN_DEPTH = 1000;

    /** How shallow water calms its waves from, in meters: none at the bottom, all this deep. */
    public static final float CALM_DEPTH = 2;

    /** How many cells waves fade out over before an edge. */
    public static final int EDGE_CELLS = 2;

    /** How much higher one cell's surface must stand than its neighbor's to make a step. */
    public static final float LEVEL_STEP = 0.05f;

    /** How deep a side goes where the tile doesn't give the bottom, in meters. */
    public static final float SIDE_DEPTH = 1;

    /** Vertices a quad is drawn with: two triangles. */
    private static final int QUAD_VERTICES = 6;

    /**
     * Values a cell's data has: flow along x and z, depth, its wind climate, how far water falling
     * onto it falls, and its surface's height at rest.
     */
    public static final int CELL_DATA = 6;

    /** Where in a cell's data its wind climate is, -1 for none. */
    public static final int CELL_CLIMATE = 3;

    /** Corners of a cell. */
    private static final int CELL_CORNERS = 4;

    /** Which of a side's corners is the low one under its first, see {@code addSide}. */
    private static final int SIDE_LOW_A = 3;

    /** Where in a cell's data how far water falling onto it falls, in meters; 0 for none. */
    public static final int CELL_PLUNGE = 4;

    /** Where in a cell's data its surface's height at rest is, in the world. */
    public static final int CELL_SURFACE = 5;

    /** A side's four corners in its two triangles. */
    private static final int[] SIDE_TRIANGLES = {0, 1, 2, 0, 2, 3};

    /** A cell's corners in a quad's two triangles, along x and z. */
    private static final int[][] QUAD = {{0, 0}, {1, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};

    /** The four neighbors across a cell's edges, and each edge's two corners. */
    private static final int[][] EDGES = {
        {-1, 0, 0, 0, 0, 1}, {1, 0, 1, 0, 1, 1}, {0, -1, 0, 0, 1, 0}, {0, 1, 0, 1, 1, 1}
    };

    /** Bits of a key for each cell coordinate. */
    private static final int KEY_BITS = 32;

    /** The low bits of a key. */
    private static final long KEY_MASK = 0xFFFFFFFFL;

    /**
     * The built surfaces.
     *
     * @param vertices The vertices, {@link #VERTEX_FLOATS} floats each.
     * @param count How many vertices.
     * @param indices The triangles' corners, three a triangle, indices into the vertices: the
     *     surfaces' first, then the sides'.
     * @param indexCount How many indices.
     * @param surfaceIndexCount How many of them are the surfaces', the rest the sides'.
     */
    public record Mesh(
            float[] vertices, int count, int[] indices, int indexCount, int surfaceIndexCount) {}

    /**
     * A cell of a tile, found by its place in the world.
     *
     * @param tile The tile's index.
     * @param x The cell along the tile's x.
     * @param z The cell along its z.
     */
    private record Cell(int tile, int x, int z) {}

    /** The tiles being built. */
    private final List<WaterTile> tiles;

    /**
     * Each cell size's cells that hold water, by world cell, as indices into {@link #cellList}: in
     * primitive maps, since every corner looks around itself, and boxed keys were most of what
     * building water allocated.
     */
    private final Map<Float, LongIntMap> cells = new HashMap<>();

    /** The cells that hold water. */
    private final List<Cell> cellList = new ArrayList<>();

    /** The cells of the cell size of the tile being added, which {@link #cellAt} looks in. */
    private LongIntMap sizeCells;

    /** About how many cells a cell size's map starts out holding. */
    private static final int EXPECTED_CELLS = 1024;

    /** The vertices so far. */
    private float[] vertices = new float[QUAD_VERTICES * VERTEX_FLOATS];

    /** How many vertices so far. */
    private int count;

    /** The indices so far. */
    private int[] indices = new int[QUAD_VERTICES];

    /** How many indices so far. */
    private int indexCount;

    /** The sides' indices so far, which go after the surfaces'. */
    private int[] sideIndices = new int[QUAD_VERTICES];

    /** How many side indices so far. */
    private int sideIndexCount;

    /** Each surface corner's vertex in the tile being added, -1 before it is made. */
    private int[] cornerVertices = new int[0];

    /**
     * Start building.
     *
     * @param tiles The tiles.
     */
    private WaterMesh(@NonNull List<WaterTile> tiles) {
        this.tiles = tiles;
    }

    /**
     * Build the surfaces and sides of a list of tiles.
     *
     * @param tiles The tiles, by index.
     * @return The vertices.
     */
    public static Mesh build(@NonNull List<WaterTile> tiles) {
        final WaterMesh mesh = new WaterMesh(tiles);
        mesh.index();
        for (int index = 0; index < tiles.size(); ++index) {
            mesh.addTile(index);
        }
        final int surfaces = mesh.indexCount;
        for (int i = 0; i < mesh.sideIndexCount; ++i) {
            mesh.addIndex(mesh.sideIndices[i]);
        }
        return new Mesh(mesh.vertices, mesh.count, mesh.indices, mesh.indexCount, surfaces);
    }

    /** Index every water cell by its place in the world. */
    private void index() {
        for (int index = 0; index < tiles.size(); ++index) {
            final WaterTile tile = tiles.get(index);
            final LongIntMap bySize =
                    cells.computeIfAbsent(tile.cellSize(), s -> new LongIntMap(EXPECTED_CELLS));
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (tile.isWater(x, z)) {
                        bySize.put(
                                key(worldCell(tile, x, true), worldCell(tile, z, false)),
                                cellList.size());
                        cellList.add(new Cell(index, x, z));
                    }
                }
            }
        }
    }

    /**
     * Add a tile's surface cells and sides.
     *
     * @param index The tile's index.
     */
    private void addTile(int index) {
        final WaterTile tile = tiles.get(index);
        sizeCells = cells.get(tile.cellSize());
        final int stride = tile.cellsX() + 1;
        cornerVertices = new int[stride * (tile.cellsZ() + 1)];
        Arrays.fill(cornerVertices, -1);
        for (int z = 0; z < tile.cellsZ(); ++z) {
            for (int x = 0; x < tile.cellsX(); ++x) {
                if (!tile.isWater(x, z)) {
                    continue;
                }
                for (int[] corner : QUAD) {
                    final int cx = x + corner[0];
                    final int cz = z + corner[1];
                    int vertex = cornerVertices[cz * stride + cx];
                    if (vertex < 0) {
                        vertex =
                                add(
                                        index,
                                        cx * tile.cellSize(),
                                        tile.heightAt(cx, cz),
                                        cz * tile.cellSize(),
                                        calm(tile, cx, cz),
                                        0,
                                        0);
                        flowAndDepth(vertex, tile, cx, cz);
                        cornerVertices[cz * stride + cx] = vertex;
                    }
                    addIndex(vertex);
                }
                for (int[] edge : EDGES) {
                    addSide(index, tile, x, z, edge);
                }
            }
        }
    }

    /**
     * Add the side across one edge of a cell, if the water ends or steps down there.
     *
     * @param index The tile's index.
     * @param tile The tile.
     * @param x The cell along x.
     * @param z The cell along z.
     * @param edge The neighbor's offset along x and z, then the edge's two corners' offsets.
     */
    private void addSide(int index, WaterTile tile, int x, int z, int[] edge) {
        final int ax = x + edge[2];
        final int az = z + edge[3];
        final int bx = x + edge[4];
        final int bz = z + edge[5];
        final float topA = tile.heightAt(ax, az);
        final float topB = tile.heightAt(bx, bz);
        final Cell neighbor = cellAt(tile, x + edge[0], z + edge[1]);
        float lowA;
        float lowB;
        if (neighbor == null) {
            lowA = tile.hasBottoms() ? tile.bottomAt(ax, az) : topA - SIDE_DEPTH;
            lowB = tile.hasBottoms() ? tile.bottomAt(bx, bz) : topB - SIDE_DEPTH;
        } else {
            // The neighbor's surface at the same two corners, from its tile's base
            final WaterTile other = tiles.get(neighbor.tile());
            final float base = (float) (other.origin().y() - tile.origin().y());
            final int dx = neighbor.x() - (x + edge[0]);
            final int dz = neighbor.z() - (z + edge[1]);
            lowA = base + other.heightAt(ax + dx, az + dz);
            lowB = base + other.heightAt(bx + dx, bz + dz);
            if (topA - lowA <= LEVEL_STEP && topB - lowB <= LEVEL_STEP) {
                return;
            }
        }
        if (lowA >= topA && lowB >= topB) {
            return;
        }
        final float size = tile.cellSize();
        final float[][] corners = {{ax, topA, az}, {bx, topB, bz}, {bx, lowB, bz}, {ax, lowA, az}};
        final int first = count;
        for (int c = 0; c < corners.length; ++c) {
            final float[] corner = corners[c];
            // How far the water has fallen from the lip above this corner
            final float fallen = (c == 0 || c == SIDE_LOW_A ? topA : topB) - corner[1];
            final int vertex =
                    add(
                            index,
                            corner[0] * size,
                            corner[1],
                            corner[2] * size,
                            fallen,
                            edge[0],
                            edge[1]);
            flowAndDepth(vertex, tile, (int) corner[0], (int) corner[2]);
        }
        for (int corner : SIDE_TRIANGLES) {
            addSideIndex(first + corner);
        }
    }

    /**
     * How much of the waves a surface corner takes: less in shallow water, and less near an edge,
     * land or a step to another level, none on one.
     *
     * @param tile The tile.
     * @param cx The corner along x.
     * @param cz The corner along z.
     * @return The calm factor, 0 to 1.
     */
    private float calm(WaterTile tile, int cx, int cz) {
        final float height = tile.heightAt(cx, cz);
        float calm = 1;
        if (tile.hasBottoms()) {
            calm = Math.clamp((height - tile.bottomAt(cx, cz)) / CALM_DEPTH, 0, 1);
        }
        for (int ring = 0; ring < EDGE_CELLS; ++ring) {
            if (edgeInRing(tile, cx, cz, ring, height)) {
                return Math.min(calm, ring / (float) EDGE_CELLS);
            }
        }
        return calm;
    }

    /**
     * Whether any cell a ring out from a corner is an edge: holds no water, or stands at another
     * level. Ring 0 is the four cells sharing the corner.
     *
     * @param tile The corner's tile.
     * @param cx The corner along x.
     * @param cz The corner along z.
     * @param ring How far out.
     * @param height The corner's height.
     * @return Whether an edge is there.
     */
    private boolean edgeInRing(WaterTile tile, int cx, int cz, int ring, float height) {
        for (int z = cz - 1 - ring; z <= cz + ring; ++z) {
            for (int x = cx - 1 - ring; x <= cx + ring; ++x) {
                final boolean onRing =
                        x == cx - 1 - ring
                                || x == cx + ring
                                || z == cz - 1 - ring
                                || z == cz + ring;
                if (!onRing) {
                    continue;
                }
                final Cell cell = cellAt(tile, x, z);
                if (cell == null) {
                    return true;
                }
                final WaterTile other = tiles.get(cell.tile());
                final float level =
                        (float) (other.origin().y() - tile.origin().y())
                                + (other.heightAt(cell.x(), cell.z())
                                                + other.heightAt(cell.x() + 1, cell.z() + 1))
                                        / 2;
                if (Math.abs(level - height) > LEVEL_STEP) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The water cell at a place given in a tile's cells, which may be in another tile of the same
     * cell size. Only for the tile being added, whose cell size's cells it looks in.
     *
     * @param tile The tile the place is given in.
     * @param x The cell along its x, perhaps outside it.
     * @param z The cell along its z.
     * @return The cell, or null where no water is.
     */
    private Cell cellAt(WaterTile tile, int x, int z) {
        if (sizeCells == null) {
            return null;
        }
        final int at = sizeCells.get(key(worldCell(tile, x, true), worldCell(tile, z, false)));
        return at == LongIntMap.MISSING ? null : cellList.get(at);
    }

    /**
     * What each one meter cell water covers holds, see {@link #waterCellData}, looked up without
     * boxing.
     */
    public static final class CellData {
        /** None. */
        public static final CellData EMPTY = new CellData(new LongIntMap(0), List.of());

        /** Each cell's row in {@link #rows}, by {@link #worldKey}. */
        private final LongIntMap index;

        /** The cells' values, shared by cells alike. */
        private final List<float[]> rows;

        /**
         * Hold some cells.
         *
         * @param index Each cell's row, by {@link #worldKey}.
         * @param rows The values.
         */
        private CellData(@NonNull LongIntMap index, @NonNull List<float[]> rows) {
            this.index = index;
            this.rows = rows;
        }

        /**
         * What a cell holds.
         *
         * @param x The cell along x.
         * @param z Along z.
         * @return Its values, or null where no water is.
         */
        public float[] get(long x, long z) {
            final int at = index.get(worldKey(x, z));
            return at == LongIntMap.MISSING ? null : rows.get(at);
        }

        /**
         * How many cells hold water.
         *
         * @return The count.
         */
        public int size() {
            return index.size();
        }
    }

    /**
     * A cell's index across the whole world, along one axis.
     *
     * @param tile Its tile.
     * @param cell Its index in the tile.
     * @param alongX Whether along x, rather than z.
     * @return The world cell index.
     */
    private static long worldCell(WaterTile tile, int cell, boolean alongX) {
        final double origin = alongX ? tile.origin().x() : tile.origin().z();
        return Math.round(origin / tile.cellSize()) + cell;
    }

    /**
     * The world's one meter cells that water covers, as {@link #worldKey} has them, for the wave
     * grid's mask.
     *
     * @param tiles The tiles.
     * @return The cells' keys.
     */
    public static Set<Long> waterCells(@NonNull List<WaterTile> tiles) {
        final Set<Long> covered = new HashSet<>();
        for (WaterTile tile : tiles) {
            final double size = tile.cellSize();
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (!tile.isWater(x, z)) {
                        continue;
                    }
                    final double minX = tile.origin().x() + x * size;
                    final double minZ = tile.origin().z() + z * size;
                    for (long cz = (long) Math.floor(minZ); cz < Math.ceil(minZ + size); ++cz) {
                        for (long cx = (long) Math.floor(minX); cx < Math.ceil(minX + size); ++cx) {
                            covered.add(worldKey(cx, cz));
                        }
                    }
                }
            }
        }
        return covered;
    }

    /**
     * The key of a world cell.
     *
     * @param x The cell along x.
     * @param z Along z.
     * @return Its key.
     */
    public static long worldKey(long x, long z) {
        return key(x, z);
    }

    /**
     * Pack two world cell indices into a key, its bits mixed: a packed pair's own hash, the two
     * halves XORed, is the same along every diagonal, which turned the maps' buckets into trees and
     * a streamed world's rebuilds into hundreds of milliseconds. The mix is the MurmurHash3
     * finalizer, which never maps two values to one, so keys stay unique.
     *
     * @param x The index along x.
     * @param z The index along z.
     * @return The key.
     */
    private static long key(long x, long z) {
        long h = (x << KEY_BITS) | (z & KEY_MASK);
        h ^= h >>> MIX_SHIFT;
        h *= MIX_A;
        h ^= h >>> MIX_SHIFT;
        h *= MIX_B;
        h ^= h >>> MIX_SHIFT;
        return h;
    }

    /** The first constant of the MurmurHash3 finalizer. */
    private static final long MIX_A = 0xff51afd7ed558ccdL;

    /** The second constant of the MurmurHash3 finalizer. */
    private static final long MIX_B = 0xc4ceb9fe1a85ec53L;

    /** The shift of the MurmurHash3 finalizer. */
    private static final int MIX_SHIFT = 33;

    /**
     * Add a vertex.
     *
     * @param index Its tile's index.
     * @param x Its x relative to the tile's corner.
     * @param y Its height above the tile's corner.
     * @param z Its z.
     * @param calm Its calm factor.
     * @param normalX Its side's outward normal along x.
     * @param normalZ Along z.
     * @return Its index.
     */
    private int add(
            int index, float x, float y, float z, float calm, float normalX, float normalZ) {
        if ((count + 1) * VERTEX_FLOATS > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        final int at = count * VERTEX_FLOATS;
        vertices[at] = x;
        vertices[at + 1] = y;
        vertices[at + 2] = z;
        vertices[at + TILE_FLOAT] = Float.intBitsToFloat(index);
        vertices[at + CALM_FLOAT] = calm;
        vertices[at + NORMAL_FLOAT] = normalX;
        vertices[at + NORMAL_FLOAT + 1] = normalZ;
        return count++;
    }

    /**
     * Set a vertex's flow and depth from its tile's corner.
     *
     * @param vertex The vertex.
     * @param tile Its tile.
     * @param cx The corner along x.
     * @param cz Along z.
     */
    private void flowAndDepth(int vertex, WaterTile tile, int cx, int cz) {
        final int at = vertex * VERTEX_FLOATS;
        vertices[at + FLOW_FLOAT] = tile.flowAt(cx, cz, false);
        vertices[at + FLOW_FLOAT + 1] = tile.flowAt(cx, cz, true);
        vertices[at + DEPTH_FLOAT] =
                tile.hasBottoms()
                        ? Math.max(0, tile.heightAt(cx, cz) - tile.bottomAt(cx, cz))
                        : UNKNOWN_DEPTH;
    }

    /**
     * What each one meter cell water covers holds, for the wave grid: how fast the water flows
     * there, along x and z, and how deep it is, from the cell's corners, its tile's wind climate,
     * and how far water falls onto it from a higher neighbor, the most of any, by {@link
     * #worldKey}.
     *
     * @param tiles The tiles.
     * @param climates Each tile's wind climate, -1 for none.
     * @return The cells' flows, depths and climates.
     */
    public static CellData waterCellData(
            @NonNull List<WaterTile> tiles, @NonNull List<Integer> climates) {
        final LongIntMap covered = new LongIntMap(EXPECTED_CELLS);
        final List<float[]> rows = new ArrayList<>();
        for (int t = 0; t < tiles.size(); ++t) {
            final WaterTile tile = tiles.get(t);
            final int climate = t < climates.size() ? climates.get(t) : -1;
            final double size = tile.cellSize();
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (!tile.isWater(x, z)) {
                        continue;
                    }
                    final float[] data = new float[CELL_DATA];
                    data[CELL_CLIMATE] = climate;
                    data[CELL_SURFACE] = (float) tile.origin().y();
                    for (int c = 0; c < CELL_CORNERS; ++c) {
                        final int cx = x + (c & 1);
                        final int cz = z + (c >> 1);
                        data[CELL_SURFACE] += tile.heightAt(cx, cz) / CELL_CORNERS;
                        data[0] += tile.flowAt(cx, cz, false) / CELL_CORNERS;
                        data[1] += tile.flowAt(cx, cz, true) / CELL_CORNERS;
                        data[2] +=
                                (tile.hasBottoms()
                                                ? tile.heightAt(cx, cz) - tile.bottomAt(cx, cz)
                                                : UNKNOWN_DEPTH)
                                        / CELL_CORNERS;
                    }
                    final double minX = tile.origin().x() + x * size;
                    final double minZ = tile.origin().z() + z * size;
                    final int row = rows.size();
                    rows.add(data);
                    for (long wz = (long) Math.floor(minZ); wz < Math.ceil(minZ + size); ++wz) {
                        for (long wx = (long) Math.floor(minX); wx < Math.ceil(minX + size); ++wx) {
                            covered.put(worldKey(wx, wz), row);
                        }
                    }
                }
            }
        }
        // Falls: a neighbor's surface standing higher than a cell's pours onto it
        final LongIntMap surfaceAt = new LongIntMap(EXPECTED_CELLS);
        float[] surfaces = new float[EXPECTED_CELLS];
        long[] cells = new long[2 * EXPECTED_CELLS];
        int cellCount = 0;
        for (WaterTile tile : tiles) {
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (!tile.isWater(x, z)) {
                        continue;
                    }
                    final float surface =
                            (float) tile.origin().y()
                                    + (tile.heightAt(x, z)
                                                    + tile.heightAt(x + 1, z)
                                                    + tile.heightAt(x, z + 1)
                                                    + tile.heightAt(x + 1, z + 1))
                                            / CELL_CORNERS;
                    final long wx = (long) Math.floor(tile.origin().x() + x * tile.cellSize());
                    final long wz = (long) Math.floor(tile.origin().z() + z * tile.cellSize());
                    final long key = worldKey(wx, wz);
                    final int known = surfaceAt.get(key);
                    if (known != LongIntMap.MISSING) {
                        surfaces[known] = Math.max(surfaces[known], surface);
                    } else {
                        final int slot = surfaceAt.size();
                        if (slot == surfaces.length) {
                            surfaces = Arrays.copyOf(surfaces, slot * 2);
                        }
                        surfaces[slot] = surface;
                        surfaceAt.put(key, slot);
                    }
                    if (2 * cellCount + 1 >= cells.length) {
                        cells = Arrays.copyOf(cells, cells.length * 2);
                    }
                    cells[2 * cellCount] = wx;
                    cells[2 * cellCount + 1] = wz;
                    ++cellCount;
                }
            }
        }
        for (int c = 0; c < cellCount; ++c) {
            final long wx = cells[2 * c];
            final long wz = cells[2 * c + 1];
            final long key = worldKey(wx, wz);
            final int here = surfaceAt.get(key);
            final int row = covered.get(key);
            if (here == LongIntMap.MISSING || row == LongIntMap.MISSING) {
                continue;
            }
            final float[] data = rows.get(row);
            float drop = 0;
            for (int[] step : PLUNGE_NEIGHBORS) {
                final int above = surfaceAt.get(worldKey(wx + step[0], wz + step[1]));
                if (above != LongIntMap.MISSING && surfaces[above] - surfaces[here] > LEVEL_STEP) {
                    drop = Math.max(drop, surfaces[above] - surfaces[here]);
                }
            }
            if (drop > data[CELL_PLUNGE]) {
                // Cells of one tile share their data array: give this one its own
                final float[] own = data.clone();
                own[CELL_PLUNGE] = drop;
                covered.put(key, rows.size());
                rows.add(own);
            }
        }
        return new CellData(covered, rows);
    }

    /** The four neighbors a cell's water can fall from. */
    private static final int[][] PLUNGE_NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Add an index.
     *
     * @param vertex The vertex it points to.
     */
    private void addIndex(int vertex) {
        if (indexCount == indices.length) {
            indices = Arrays.copyOf(indices, indices.length * 2);
        }
        indices[indexCount++] = vertex;
    }

    /**
     * Add a side's index, drawn after every surface.
     *
     * @param vertex The vertex it points to.
     */
    private void addSideIndex(int vertex) {
        if (sideIndexCount == sideIndices.length) {
            sideIndices = Arrays.copyOf(sideIndices, sideIndices.length * 2);
        }
        sideIndices[sideIndexCount++] = vertex;
    }
}
