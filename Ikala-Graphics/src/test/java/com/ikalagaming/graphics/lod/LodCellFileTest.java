package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetWriter;
import com.ikalagaming.graphics.asset.Section;
import com.ikalagaming.graphics.asset.SectionTag;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests for storing region cells in files. */
class LodCellFileTest {

    /** Section size. */
    static final int SECTION = SyntheticTerrain.SECTION;

    /** Options for a test build, on two threads, without visibility to keep it quick. */
    static final DagBuilder.Options OPTIONS = new DagBuilder.Options(0.5, false, 2, false, 0);

    /** The cache key the forged files are written with. */
    private static final String KEY = "k";

    /** How long refusing a forged count may take: no time at all, unless it allocated first. */
    private static final long QUICK_NANOS = 2_000_000_000L;

    /** Counts far past anything a file could hold, and one that is negative. */
    private static final int[] HUGE = {Integer.MAX_VALUE, Integer.MAX_VALUE / 16, -1};

    /** Where the cell record's section count is in its section. */
    private static final int CELL_SECTIONS_AT = 60;

    /** Where the cell record's input count is in its section. */
    private static final int CELL_INPUTS_AT = 64;

    /** Where the cell record's stop reason length is in its section. */
    private static final int CELL_REASON_AT = 68;

    /** Where the first cluster's level is in the cluster section. */
    private static final int LEVEL_AT = 12;

    /** Where the first cluster's triangle count is in the cluster section. */
    private static final int TRIANGLES_AT = 16;

    /** Where the first cluster's vertex count is in the cluster section. */
    private static final int VERTICES_AT = 100;

    /** Where the first group's child count is in the group section. */
    private static final int CHILDREN_AT = 16;

    /** Where the first group's output count is in the group section. */
    private static final int OUTPUTS_AT = 20;

    /** Bytes in a group record before its children. */
    private static final int GROUP_FIXED_BYTES = 112;

    /** A sound cell file, with groups, that the forged ones are made from. */
    private static AssetFile valid;

    /**
     * A store of sections in one-section cells, not yet built.
     *
     * @param sections The sections.
     * @param cache The cache, or null.
     * @return The store.
     */
    static LodStore store(List<LodMesh> sections, LodCache cache) {
        final LodStore store = new LodStore(new double[3], SECTION, 1, 4, OPTIONS, cache);
        for (LodMesh mesh : sections) {
            store.put(position(mesh), mesh);
        }
        return store;
    }

    /**
     * Where a section mesh is.
     *
     * @param mesh The mesh.
     * @return Its position, in sections.
     */
    static LodStore.SectionPos position(LodMesh mesh) {
        final double[] origin = mesh.getOrigin();
        return new LodStore.SectionPos(
                Math.floorDiv((int) origin[0], SECTION),
                Math.floorDiv((int) origin[1], SECTION),
                Math.floorDiv((int) origin[2], SECTION));
    }

    /**
     * Check two DAGs are the same in every field a file keeps.
     *
     * @param expected One DAG.
     * @param actual The other.
     */
    static void assertSameDag(LodDag expected, LodDag actual) {
        assertArrayEquals(expected.getOrigin(), actual.getOrigin());
        assertEquals(expected.getLeafEdge(), actual.getLeafEdge());
        assertEquals(expected.getStopReason(), actual.getStopReason());
        assertEquals(expected.clusters().size(), actual.clusters().size());
        for (int i = 0; i < expected.clusters().size(); ++i) {
            final LodDag.Cluster a = expected.clusters().get(i);
            final LodDag.Cluster b = actual.clusters().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.level(), b.level());
            assertArrayEquals(a.corners(), b.corners());
            assertArrayEquals(a.materials(), b.materials());
            assertArrayEquals(a.visibility(), b.visibility());
            assertEquals(a.error(), b.error());
            assertArrayEquals(a.bound(), b.bound());
            assertArrayEquals(a.tight(), b.tight());
            assertEquals(a.producer(), b.producer());
            assertEquals(a.section(), b.section());
            assertEquals(expected.consumer(i), actual.consumer(i));
        }
        assertEquals(expected.groups().size(), actual.groups().size());
        for (int i = 0; i < expected.groups().size(); ++i) {
            final LodDag.Group a = expected.groups().get(i);
            final LodDag.Group b = actual.groups().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.level(), b.level());
            assertArrayEquals(a.children(), b.children());
            assertArrayEquals(a.outputs(), b.outputs());
            assertEquals(a.error(), b.error());
            assertArrayEquals(a.bound(), b.bound());
            assertEquals(a.simplifyError(), b.simplifyError());
            assertEquals(a.quadricError(), b.quadricError());
            assertEquals(a.trianglesIn(), b.trianglesIn());
            assertEquals(a.trianglesOut(), b.trianglesOut());
            assertEquals(a.borderEdges(), b.borderEdges());
            assertEquals(a.edges(), b.edges());
            assertEquals(a.virtualEdges(), b.virtualEdges());
            assertEquals(a.rejectedFlips(), b.rejectedFlips());
            assertEquals(a.rejectedLinks(), b.rejectedLinks());
            assertEquals(a.buildNanos(), b.buildNanos());
        }
    }

    /** Every cell, leaves and parents, reads back as it was written. */
    @Test
    void testRoundTrip() {
        final LodStore store = store(DagTest.terrain(2), null);
        store.rebuild();
        int parents = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            final LodStore.Cell cell = store.built(key);
            final AssetFile file =
                    AssetFile.read(
                            LodCellFile.encode("k", LodStore.BUILDER_VERSION, key, cell).toBuffer(),
                            LodCellFile.known());
            final LodCellFile.Stored back = LodCellFile.decode(file, "k", LodStore.BUILDER_VERSION);
            assertEquals(key, back.key());
            assertSameDag(cell.dag(), back.cell().dag());
            assertEquals(cell.inputs(), back.cell().inputs());
            assertEquals(cell.sections(), back.cell().sections());
            assertEquals(cell.leafEdge(), back.cell().leafEdge());
            parents += key.level() > 0 ? 1 : 0;
        }
        assertEquals(true, parents > 0, "Parents were checked too");
    }

    /** A file read for the wrong key or builder version is refused. */
    @Test
    void testWrongKeyOrVersionRefused() {
        final LodStore store = store(DagTest.terrain(1), null);
        store.rebuild();
        final LodStore.CellKey key = store.cellKeys().iterator().next();
        final AssetFile file =
                AssetFile.read(
                        LodCellFile.encode("k", LodStore.BUILDER_VERSION, key, store.built(key))
                                .toBuffer(),
                        LodCellFile.known());
        assertThrows(
                IllegalArgumentException.class,
                () -> LodCellFile.decode(file, "other", LodStore.BUILDER_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () -> LodCellFile.decode(file, "k", LodStore.BUILDER_VERSION + 1));
    }

    /** Build the sound file the forged ones are made from: a cell that has groups. */
    @BeforeAll
    static void makeValid() {
        final LodStore store = store(DagTest.terrain(1), null);
        store.rebuild();
        for (LodStore.CellKey key : store.cellKeys()) {
            final LodStore.Cell cell = store.built(key);
            if (!cell.dag().groups().isEmpty()) {
                valid =
                        AssetFile.read(
                                LodCellFile.encode(KEY, LodStore.BUILDER_VERSION, key, cell)
                                        .toBuffer(),
                                LodCellFile.known());
                return;
            }
        }
        throw new IllegalStateException("No cell has groups");
    }

    /**
     * The sound file's sections' payloads, copied so they can be changed.
     *
     * @return Each payload by its section's tag, in the file's order.
     */
    private static Map<Integer, ByteBuffer> payloads() {
        final Map<Integer, ByteBuffer> out = new LinkedHashMap<>();
        for (Section section : valid.sections()) {
            if (section.tag() == SectionTag.META) {
                continue;
            }
            final ByteBuffer data = section.data();
            final ByteBuffer copy =
                    ByteBuffer.allocate(data.remaining()).order(ByteOrder.LITTLE_ENDIAN);
            copy.put(data).flip();
            out.put(section.tag(), copy);
        }
        return out;
    }

    /**
     * Write a file of the sound file's metadata and the payloads given. The writer works the
     * checksums out again, so the file is as a forger would make it, not merely damaged.
     *
     * @param payloads Each section's payload by its tag.
     * @return The file.
     */
    private static ByteBuffer write(Map<Integer, ByteBuffer> payloads) {
        final AssetWriter writer = new AssetWriter().metadata(valid.metadata());
        for (Map.Entry<Integer, ByteBuffer> entry : payloads.entrySet()) {
            final int tag = entry.getKey();
            final int version =
                    valid.section(tag).map(Section::version).orElse(versionOf(tag));
            writer.section(tag, version, true, entry.getValue());
        }
        return writer.toBuffer();
    }

    /**
     * The version a section is written with.
     *
     * @param tag The section's tag.
     * @return Its version.
     */
    private static int versionOf(int tag) {
        if (tag == LodCellFile.LVOL) {
            return LodCellFile.VOLUMES_VERSION;
        }
        return LodCellFile.VERSION;
    }

    /**
     * The sound file with one int in one section changed.
     *
     * @param tag The section.
     * @param offset Where the int is in its payload.
     * @param value What it becomes.
     * @return The forged file.
     */
    private static ByteBuffer forged(int tag, int offset, int value) {
        final Map<Integer, ByteBuffer> payloads = payloads();
        payloads.get(tag).putInt(offset, value);
        return write(payloads);
    }

    /**
     * An int in a section of the sound file.
     *
     * @param tag The section.
     * @param offset Where the int is in its payload.
     * @return The int.
     */
    private static int intAt(int tag, int offset) {
        return valid.section(tag).orElseThrow().data().getInt(offset);
    }

    /**
     * The size of a section of the sound file.
     *
     * @param tag The section.
     * @return Its payload's bytes.
     */
    private static int sizeOf(int tag) {
        return valid.section(tag).orElseThrow().data().remaining();
    }

    /**
     * Check a forged file is refused as corrupt, quickly, without first asking for the memory its
     * counts claim. An attempt to make a huge array would fail with an error, or take far longer.
     *
     * @param forged The file.
     */
    private static void assertRefused(ByteBuffer forged) {
        final AssetFile file = AssetFile.read(forged, LodCellFile.known());
        final long start = System.nanoTime();
        assertThrows(
                IllegalArgumentException.class,
                () -> LodCellFile.decode(file, KEY, LodStore.BUILDER_VERSION));
        assertTrue(System.nanoTime() - start < QUICK_NANOS, "Refused quickly");
    }

    /**
     * Check an int in a section of the sound file is refused at every huge value and at one just
     * past what the data could hold.
     *
     * @param tag The section.
     * @param offset Where the int is in its payload.
     * @param itemBytes The fewest bytes each item it counts takes.
     */
    private static void assertCountRefused(int tag, int offset, int itemBytes) {
        for (int value : HUGE) {
            assertRefused(forged(tag, offset, value));
        }
        assertRefused(forged(tag, offset, sizeOf(tag) / itemBytes + 1));
    }

    /** The sound file the forged ones are made from reads. */
    @Test
    void testValidFileReads() {
        final LodDag dag = LodCellFile.decode(valid, KEY, LodStore.BUILDER_VERSION).cell().dag();
        assertEquals(intAt(LodCellFile.LCLU, 0), dag.clusters().size());
        assertEquals(intAt(LodCellFile.LGRP, 0), dag.groups().size());
        // And with every section written again, as the forged files are
        final AssetFile rewritten = AssetFile.read(write(payloads()), LodCellFile.known());
        assertSameDag(
                dag, LodCellFile.decode(rewritten, KEY, LodStore.BUILDER_VERSION).cell().dag());
    }

    /** Forged counts in the cell record are refused. */
    @Test
    void testForgedCellCountsRefused() {
        assertCountRefused(LodCellFile.LCEL, CELL_SECTIONS_AT, 3 * Integer.BYTES);
        assertCountRefused(LodCellFile.LCEL, CELL_INPUTS_AT, 5 * Integer.BYTES);
        assertCountRefused(LodCellFile.LCEL, CELL_REASON_AT, 1);
    }

    /** Forged counts and levels in the clusters are refused. */
    @Test
    void testForgedClusterCountsRefused() {
        assertCountRefused(LodCellFile.LCLU, 0, Integer.BYTES);
        assertCountRefused(LodCellFile.LCLU, TRIANGLES_AT, Short.BYTES);
        assertCountRefused(LodCellFile.LCLU, VERTICES_AT, Float.BYTES);
        assertRefused(forged(LodCellFile.LCLU, LEVEL_AT, -1));
        assertRefused(forged(LodCellFile.LCLU, LEVEL_AT, Integer.MAX_VALUE));
    }

    /** Forged counts in the groups are refused. */
    @Test
    void testForgedGroupCountsRefused() {
        assertCountRefused(LodCellFile.LGRP, 0, Integer.BYTES);
        assertCountRefused(LodCellFile.LGRP, CHILDREN_AT, Integer.BYTES);
        assertCountRefused(LodCellFile.LGRP, OUTPUTS_AT, Integer.BYTES);
        // More children than the DAG has clusters, though the data could hold their ids
        assertRefused(forged(LodCellFile.LGRP, CHILDREN_AT, intAt(LodCellFile.LCLU, 0) + 1));
        final int tilesAt =
                Integer.BYTES
                        + GROUP_FIXED_BYTES
                        + Integer.BYTES
                                * (intAt(LodCellFile.LGRP, CHILDREN_AT)
                                        + intAt(LodCellFile.LGRP, OUTPUTS_AT));
        assertCountRefused(LodCellFile.LGRP, tilesAt, Integer.BYTES);
    }

    /** Forged counts in the optional sections are refused. */
    @Test
    void testForgedOptionalCountsRefused() {
        for (int value : HUGE) {
            // A volume of the first cluster claiming that many voxels
            final Map<Integer, ByteBuffer> volumes = payloads();
            final ByteBuffer volume =
                    ByteBuffer.allocate(Integer.BYTES * 3 + Double.BYTES * 4)
                            .order(ByteOrder.LITTLE_ENDIAN);
            volume.putInt(1).putInt(0);
            for (int i = 0; i < 4; ++i) {
                volume.putDouble(1);
            }
            volumes.put(LodCellFile.LVOL, volume.putInt(value).flip());
            assertRefused(write(volumes));

            // That many volumes, kind lists or bakes
            for (int tag : new int[] {LodCellFile.LVOL, LodCellFile.LKND, LodCellFile.LBAK}) {
                final Map<Integer, ByteBuffer> counted = payloads();
                counted.put(
                        tag,
                        ByteBuffer.allocate(Integer.BYTES)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(value)
                                .flip());
                assertRefused(write(counted));
            }

            // The first cluster's volume kinds claiming that many
            final Map<Integer, ByteBuffer> kinds = payloads();
            kinds.put(
                    LodCellFile.LKND,
                    ByteBuffer.allocate(Integer.BYTES * 3)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(1)
                            .putInt(0)
                            .putInt(value)
                            .flip());
            assertRefused(write(kinds));
        }
    }

    /**
     * Forged counts and record sizes are refused when looking up where clusters lie, too.
     *
     * @param folder Where the forged files go.
     * @throws IOException If they can't be written.
     */
    @Test
    void testForgedClusterPlacesRefused(@TempDir Path folder) throws IOException {
        final Path file = folder.resolve("forged.ika");
        for (int value : HUGE) {
            for (int offset : new int[] {0, Integer.BYTES}) {
                writeFile(file, forged(LodCellFile.LCLU, offset, value));
                final long start = System.nanoTime();
                assertThrows(IllegalArgumentException.class, () -> LodCellFile.clusterPlaces(file));
                assertTrue(System.nanoTime() - start < QUICK_NANOS, "Refused quickly");
            }
        }
        // A record too small to be one, which would otherwise never move on
        writeFile(file, forged(LodCellFile.LCLU, Integer.BYTES, 0));
        assertThrows(IllegalArgumentException.class, () -> LodCellFile.clusterPlaces(file));
    }

    /**
     * A cached file with a forged count is a broken file, so the cache deletes it, rather than a
     * read failure that keeps it.
     *
     * @param folder The cache's folder.
     * @throws IOException If the file can't be written.
     */
    @Test
    void testForgedCacheFileDeleted(@TempDir Path folder) throws IOException {
        final LodCache cache = new LodCache(folder, Long.MAX_VALUE);
        final Path file = cache.fileOf(KEY);
        writeFile(file, forged(LodCellFile.LCLU, TRIANGLES_AT, Integer.MAX_VALUE));
        assertNull(cache.load(KEY, LodStore.BUILDER_VERSION));
        assertFalse(Files.exists(file), "The broken file was deleted");
    }

    /**
     * Write a file.
     *
     * @param path Where.
     * @param data What.
     * @throws IOException If it can't be written.
     */
    private static void writeFile(Path path, ByteBuffer data) throws IOException {
        final byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        Files.write(path, bytes);
    }
}
