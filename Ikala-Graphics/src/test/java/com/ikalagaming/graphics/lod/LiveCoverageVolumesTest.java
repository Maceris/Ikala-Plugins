package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.asset.AssetFile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for coverage volumes in region cells, as live worlds build them. */
class LiveCoverageVolumesTest {

    /** Section size. */
    private static final int SECTION = 16;

    /** Sections along each side of the field. */
    private static final int COLUMNS = 8;

    /** Bushes along each side of a section. */
    private static final int BUSHES = 2;

    /** How far apart bushes are. */
    private static final float BUSH_SPACING = 7.5f;

    /** How high the bushes float. */
    private static final float BUSH_HEIGHT = 10;

    /** Sections along each side of a level 0 cell. */
    private static final int CELL_SECTIONS = 4;

    /** Stop building a cell once this many clusters or fewer are left. */
    private static final int STOP_AT = 4;

    /** Options for a test build, on two threads, no visibility, virtual edges or planar merging. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0, false, 2, false, 0, false).withVolumes(true);

    /**
     * Hilly sections with bushes floating over them: closed boxes with finely cut faces.
     *
     * @return The sections, each relative to its corner.
     */
    private static List<LodMesh> sections() {
        final List<LodMesh> out = new ArrayList<>();
        for (int sx = 0; sx < COLUMNS; ++sx) {
            for (int sz = 0; sz < COLUMNS; ++sz) {
                final LodMesh.Builder builder =
                        LodMesh.builder(new double[] {sx * SECTION, 0, sz * SECTION});
                final int[][] index = new int[SECTION + 1][SECTION + 1];
                for (int x = 0; x <= SECTION; ++x) {
                    for (int z = 0; z <= SECTION; ++z) {
                        index[x][z] =
                                builder.vertex(
                                        x,
                                        (float)
                                                FoldSmallPartsTest.height(
                                                        sx * SECTION + x, sz * SECTION + z),
                                        z);
                    }
                }
                for (int x = 0; x < SECTION; ++x) {
                    for (int z = 0; z < SECTION; ++z) {
                        builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], 0);
                        builder.triangle(index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], 0);
                    }
                }
                for (int i = 0; i < BUSHES; ++i) {
                    for (int j = 0; j < BUSHES; ++j) {
                        CoverageVolumesTest.bush(
                                builder, 2 + i * BUSH_SPACING, BUSH_HEIGHT, 2 + j * BUSH_SPACING);
                    }
                }
                out.add(builder.build());
            }
        }
        return out;
    }

    /**
     * A store of the sections in cells of a few sections each, built.
     *
     * @return The store.
     */
    private static LodStore built() {
        final LodStore store =
                new LodStore(new double[3], SECTION, CELL_SECTIONS, STOP_AT, OPTIONS, null);
        for (LodMesh mesh : sections()) {
            store.put(LodCellFileTest.position(mesh), mesh);
        }
        store.rebuild();
        return store;
    }

    /**
     * Volumes made in one cell go on up the cells: a parent takes its children's volumes in as
     * volumes, though it has none of their leaves, and makes coarser ones of its own from them.
     */
    @Test
    void testVolumesPassUpCells() {
        final LodStore store = built();
        int leafVolumes = 0;
        int parentVolumes = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            final LodStore.Cell cell = store.built(key);
            for (int id = cell.inputs().size(); id < cell.dag().clusters().size(); ++id) {
                final CoverageVolume volume = cell.dag().volumeOf(id);
                if (volume == null) {
                    continue;
                }
                assertTrue(volume.size() > 0, "Empty volume in " + key);
                assertEquals(0, cell.dag().clusters().get(id).triangleCount());
                if (key.level() == 0) {
                    ++leafVolumes;
                } else {
                    ++parentVolumes;
                }
            }
            // A parent's inputs keep their children's volumes
            for (int id = 0; id < cell.inputs().size(); ++id) {
                final LodStore.Input input = cell.inputs().get(id);
                final CoverageVolume child =
                        store.built(input.child()).dag().volumeOf(input.cluster());
                assertEquals(child != null, cell.dag().volumeOf(id) != null, "Input " + id);
            }
        }
        assertTrue(leafVolumes > 0, "No volumes in leaf cells");
        assertTrue(parentVolumes > 0, "No volumes made in parent cells");
    }

    /** Cells with volumes read back from their files with the same volumes. */
    @Test
    void testVolumesRoundTrip() {
        final LodStore store = built();
        int volumes = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            final LodStore.Cell cell = store.built(key);
            final AssetFile file =
                    AssetFile.read(
                            LodCellFile.encode("k", LodStore.BUILDER_VERSION, key, cell).toBuffer(),
                            LodCellFile.known());
            final LodDag back =
                    LodCellFile.decode(file, "k", LodStore.BUILDER_VERSION).cell().dag();
            for (int id = 0; id < cell.dag().clusters().size(); ++id) {
                final CoverageVolume a = cell.dag().volumeOf(id);
                final CoverageVolume b = back.volumeOf(id);
                if (a == null) {
                    assertNull(b);
                    continue;
                }
                assertNotNull(b);
                ++volumes;
                assertArrayEquals(a.origin(), b.origin());
                assertEquals(a.voxelSize(), b.voxelSize());
                assertEquals(a.size(), b.size());
                for (int v = 0; v < a.size(); ++v) {
                    assertEquals(a.keyOf(v), b.keyOf(v));
                    assertEquals(a.density(v), b.density(v));
                    assertEquals(a.roughness(v), b.roughness(v));
                    assertEquals(a.material(v), b.material(v));
                    assertEquals(a.shade(v), b.shade(v));
                    for (int k = 0; k < CoverageVolume.SGGX_VALUES; ++k) {
                        assertEquals(a.sggx(v, k), b.sggx(v, k));
                    }
                }
            }
        }
        assertTrue(volumes > 0, "No volumes were checked");
    }
}
