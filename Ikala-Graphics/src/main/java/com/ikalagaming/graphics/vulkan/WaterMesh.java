package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Each vertex is {@link #VERTEX_FLOATS} floats: its place relative to its tile's corner, its
 * tile's index as an int's bits, its calm factor, and for sides the face's outward normal along x
 * and z, zero on the surface.
 */
public final class WaterMesh {

    /** Floats in a vertex. */
    public static final int VERTEX_FLOATS = 7;

    /** Where in a vertex its tile's index is, as an int's bits. */
    public static final int TILE_FLOAT = 3;

    /** Where in a vertex its calm factor is. */
    public static final int CALM_FLOAT = 4;

    /** Where in a vertex its side's outward normal is, x then z. */
    public static final int NORMAL_FLOAT = 5;

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
     */
    public record Mesh(float[] vertices, int count) {}

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

    /** Each cell size's cells that hold water, by world cell. */
    private final Map<Float, Map<Long, Cell>> cells = new HashMap<>();

    /** The vertices so far. */
    private float[] vertices = new float[QUAD_VERTICES * VERTEX_FLOATS];

    /** How many vertices so far. */
    private int count;

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
        return new Mesh(mesh.vertices, mesh.count);
    }

    /** Index every water cell by its place in the world. */
    private void index() {
        for (int index = 0; index < tiles.size(); ++index) {
            final WaterTile tile = tiles.get(index);
            final Map<Long, Cell> bySize =
                    cells.computeIfAbsent(tile.cellSize(), s -> new HashMap<>());
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (tile.isWater(x, z)) {
                        bySize.put(
                                key(worldCell(tile, x, true), worldCell(tile, z, false)),
                                new Cell(index, x, z));
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
        for (int z = 0; z < tile.cellsZ(); ++z) {
            for (int x = 0; x < tile.cellsX(); ++x) {
                if (!tile.isWater(x, z)) {
                    continue;
                }
                for (int[] corner : QUAD) {
                    final int cx = x + corner[0];
                    final int cz = z + corner[1];
                    add(
                            index,
                            tile,
                            cx * tile.cellSize(),
                            tile.heightAt(cx, cz),
                            cz * tile.cellSize(),
                            calm(tile, cx, cz),
                            0,
                            0);
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
        final float[][] quad = {
            {ax, topA, az}, {bx, topB, bz}, {bx, lowB, bz},
            {ax, topA, az}, {bx, lowB, bz}, {ax, lowA, az}
        };
        for (float[] corner : quad) {
            add(index, tile, corner[0] * size, corner[1], corner[2] * size, 0, edge[0], edge[1]);
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
     * cell size.
     *
     * @param tile The tile the place is given in.
     * @param x The cell along its x, perhaps outside it.
     * @param z The cell along its z.
     * @return The cell, or null where no water is.
     */
    private Cell cellAt(WaterTile tile, int x, int z) {
        final Map<Long, Cell> bySize = cells.get(tile.cellSize());
        return bySize == null
                ? null
                : bySize.get(key(worldCell(tile, x, true), worldCell(tile, z, false)));
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
     * Pack two world cell indices into a key.
     *
     * @param x The index along x.
     * @param z The index along z.
     * @return The key.
     */
    private static long key(long x, long z) {
        return (x << KEY_BITS) | (z & KEY_MASK);
    }

    /**
     * Add a vertex.
     *
     * @param index Its tile's index.
     * @param tile Its tile.
     * @param x Its x relative to the tile's corner.
     * @param y Its height above the tile's corner.
     * @param z Its z.
     * @param calm Its calm factor.
     * @param normalX Its side's outward normal along x.
     * @param normalZ Along z.
     */
    private void add(
            int index,
            WaterTile tile,
            float x,
            float y,
            float z,
            float calm,
            float normalX,
            float normalZ) {
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
        ++count;
    }
}
