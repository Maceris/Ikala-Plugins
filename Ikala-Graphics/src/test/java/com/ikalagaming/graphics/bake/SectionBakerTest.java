package com.ikalagaming.graphics.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.Material;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Baking sections, built as the factory plugin would: placements plus their neighbors' masks. */
class SectionBakerTest {

    /** A section being laid out in a grid, the way the factory plugin knows it. */
    private static final class Grid {
        final int size;
        final BakeSource[] cells;
        final int[] rotations;

        Grid(int size) {
            this.size = size;
            cells = new BakeSource[size * size * size];
            rotations = new int[cells.length];
        }

        int index(int x, int y, int z) {
            return (z * size + y) * size + x;
        }

        void put(int x, int y, int z, BakeSource source, int rotation) {
            cells[index(x, y, z)] = source;
            rotations[index(x, y, z)] = rotation;
        }

        BakeSource at(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) {
                return null;
            }
            return cells[index(x, y, z)];
        }

        /** Bake it, working out every placement's face mask from its neighbors. */
        SectionBaker.Result bake() {
            List<Integer> placed = new ArrayList<>();
            for (int i = 0; i < cells.length; ++i) {
                if (cells[i] != null) {
                    placed.add(i);
                }
            }
            final int count = placed.size();
            BakeSource[] sources = new BakeSource[count];
            int[] materials = new int[count];
            int[] positions = new int[count];
            byte[] turns = new byte[count];
            byte[] masks = new byte[count];
            short[] user = new short[count];
            for (int p = 0; p < count; ++p) {
                final int i = placed.get(p);
                final int x = i % size;
                final int y = i / size % size;
                final int z = i / size / size;
                BakeSource[] neighbors = new BakeSource[Face.ALL.length];
                int[] neighborRotations = new int[Face.ALL.length];
                for (Face face : Face.ALL) {
                    int[] step = new int[3];
                    step[face.getAxis()] = face.getSign();
                    neighbors[face.ordinal()] = at(x + step[0], y + step[1], z + step[2]);
                    if (neighbors[face.ordinal()] != null) {
                        neighborRotations[face.ordinal()] =
                                rotations[index(x + step[0], y + step[1], z + step[2])];
                    }
                }
                sources[p] = cells[i];
                materials[p] = 1;
                positions[p] = SectionBaker.pack(x, y, z);
                turns[p] = (byte) rotations[i];
                masks[p] =
                        (byte)
                                Faces.hiddenFaces(
                                        cells[i], rotations[i], neighbors, neighborRotations);
                user[p] = (short) i;
            }
            return SectionBaker.bake(sources, materials, positions, turns, masks, user);
        }
    }

    @Test
    void packedPositionsKeepTheirSigns() {
        int packed = SectionBaker.pack(-512, 0, 511);
        assertEquals(-512, SectionBaker.unpack(packed, 0));
        assertEquals(0, SectionBaker.unpack(packed, 1));
        assertEquals(511, SectionBaker.unpack(packed, 2));
        assertEquals(-1, SectionBaker.unpack(SectionBaker.pack(-1, -1, -1), 1));
        assertThrows(IllegalArgumentException.class, () -> SectionBaker.pack(512, 0, 0));
    }

    @Test
    void aSolidSectionKeepsOnlyItsShell() {
        final int size = 16;
        Grid grid = new Grid(size);
        BakeSource cube = BakeFixtures.cube().source();
        for (int i = 0; i < grid.cells.length; ++i) {
            grid.cells[i] = cube;
        }
        SectionBaker.Result result = grid.bake();
        // Two triangles per outside face of each of the six sides
        assertEquals(size * size * 6 * 2, result.triangleCount());
        SectionBaker.Bucket opaque = result.get(Material.Transparency.OPAQUE);
        assertNotNull(opaque);
        assertEquals(0, opaque.min()[0]);
        assertEquals(size * BakedVertex.STEPS_PER_UNIT, opaque.max()[1]);
    }

    @Test
    void aCheckerboardKeepsEveryFace() {
        final int size = 4;
        Grid grid = new Grid(size);
        BakeSource cube = BakeFixtures.cube().source();
        int placed = 0;
        for (int x = 0; x < size; ++x) {
            for (int y = 0; y < size; ++y) {
                for (int z = 0; z < size; ++z) {
                    if ((x + y + z) % 2 == 0) {
                        grid.put(x, y, z, cube, CubeRotations.IDENTITY);
                        ++placed;
                    }
                }
            }
        }
        assertEquals(placed * 12, grid.bake().triangleCount());
    }

    @Test
    void transparencyPicksTheBucket() {
        Grid grid = new Grid(4);
        BakeSource stone = BakeFixtures.cube().source();
        BakeSource leaves =
                BakeFixtures.cube().source(BakeFixtures.material(Material.Transparency.CUTOUT));
        BakeSource glass =
                BakeFixtures.cube()
                        .source(BakeFixtures.material(Material.Transparency.TRANSLUCENT));
        grid.put(0, 0, 0, stone, 0);
        grid.put(2, 0, 0, leaves, 0);
        for (int x = 0; x < 4; ++x) {
            grid.put(x, 2, 0, glass, 0);
            grid.put(x, 3, 3, glass, 0);
        }
        SectionBaker.Result result = grid.bake();
        assertEquals(12, result.get(Material.Transparency.OPAQUE).triangleCount());
        assertEquals(12, result.get(Material.Transparency.CUTOUT).triangleCount());
        // Two rows of four panes of one glass: the faces between panes go
        SectionBaker.Bucket translucent = result.get(Material.Transparency.TRANSLUCENT);
        // Drawn in any order by the transparent stage, so not sorted
        assertEquals(2 * (4 * 4 + 2) * 2, translucent.triangleCount());
    }

    @Test
    void rotationsOffsetsAndUserDataReachTheVertices() {
        BakeSource slab = BakeFixtures.slab().source();
        final int upsideDown = FacesTest.rotationTurning(Face.NEG_Y, Face.POS_Y);
        SectionBaker.Result result =
                SectionBaker.bake(
                        new BakeSource[] {slab},
                        new int[] {7},
                        new int[] {SectionBaker.pack(3, -1, 0)},
                        new byte[] {(byte) upsideDown},
                        new byte[] {0},
                        new short[] {(short) 0xBEEF});
        SectionBaker.Bucket bucket = result.get(Material.Transparency.OPAQUE);
        assertNull(result.get(Material.Transparency.CUTOUT));
        // Hanging from the top of the cell below the origin: y from -0.5 to 0
        assertEquals(3 * BakedVertex.STEPS_PER_UNIT, bucket.min()[0]);
        assertEquals(-BakedVertex.STEPS_PER_UNIT / 2, bucket.min()[1]);
        assertEquals(0, bucket.max()[1]);
        for (int vertex = 0; vertex < bucket.vertexCount(); ++vertex) {
            int at = vertex * BakedVertex.SIZE;
            assertEquals((short) 0xBEEF, bucket.vertices().getShort(at + BakedVertex.USER_OFFSET));
            assertEquals(7, bucket.vertices().getShort(at + BakedVertex.MATERIAL_OFFSET));
        }
    }

    @Test
    void hiddenFacesAreDroppedAfterTurning() {
        BakeSource cube = BakeFixtures.cube().source();
        final int turned = FacesTest.rotationTurning(Face.POS_X, Face.POS_Y);
        // Hide the top: whichever face is on top after turning goes
        SectionBaker.Result result =
                SectionBaker.bake(
                        new BakeSource[] {cube},
                        new int[] {0},
                        new int[] {0},
                        new byte[] {(byte) turned},
                        new byte[] {(byte) Face.POS_Y.bit()},
                        new short[] {0});
        SectionBaker.Bucket bucket = result.get(Material.Transparency.OPAQUE);
        assertEquals(10, bucket.triangleCount());
        for (int triangle = 0; triangle < bucket.triangleCount(); ++triangle) {
            boolean allOnTop = true;
            for (int corner = 0; corner < 3; ++corner) {
                int at = bucket.indices()[triangle * 3 + corner] * BakedVertex.SIZE;
                allOnTop &= bucket.vertices().getShort(at + 2) == BakedVertex.STEPS_PER_UNIT;
            }
            assertTrue(!allOnTop, "The top face is gone");
        }
    }
}
