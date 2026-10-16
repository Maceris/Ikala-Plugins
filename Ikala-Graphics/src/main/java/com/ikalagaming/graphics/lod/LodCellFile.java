package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetWriter;
import com.ikalagaming.graphics.asset.KnownSections;
import com.ikalagaming.graphics.asset.Section;
import com.ikalagaming.graphics.asset.SectionTag;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One region cell of a {@link LodStore} in an {@code .ika} file, so a cell whose inputs haven't
 * changed is read instead of built (see {@link LodCache}). The file holds the cell's DAG whole:
 * every cluster with its corners, materials and visibility, and every group; meshes are made again
 * from the corners when the cell is drawn.
 *
 * <p>Sections, all required, little endian, each record starting with its own size in bytes:
 *
 * <ul>
 *   <li>{@code LCEL}: the cell: its key's level and place, the DAG's origin, its leaf edge lengths,
 *       why building stopped, the sections its leaves' section indices stand for, and, for a
 *       parent, which child cluster each of its first clusters is.
 *   <li>{@code LCLU}: a count, then a record per cluster in id order.
 *   <li>{@code LGRP}: a count, then a record per group in id order.
 * </ul>
 *
 * <p>The META section names the exporter and the builder version, and holds the cache key, which a
 * reader checks against the one it looked for.
 */
public final class LodCellFile {

    /** The cell section. */
    public static final int LCEL = SectionTag.of("LCEL");

    /** The cluster section. */
    public static final int LCLU = SectionTag.of("LCLU");

    /** The group section. */
    public static final int LGRP = SectionTag.of("LGRP");

    /** The version of every section but the clusters. */
    public static final int VERSION = 1;

    /**
     * The version of the clusters section: 2 added each cluster's attributes; 3 stores each
     * cluster's distinct vertices once, with a 16-bit index per corner, about half the size.
     */
    public static final int CLUSTERS_VERSION = 3;

    /** The most vertices a cluster's table can hold, as its corners index it with 16 bits. */
    private static final int MAX_VERTICES = 1 << Short.SIZE;

    /** The exporter named in the metadata. */
    public static final String EXPORTER = "Ikala-Graphics LodStore";

    /** The metadata key of the cache key. */
    public static final String KEY = "lod.key";

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a sphere. */
    private static final int SPHERE_VALUES = 4;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** Ints in a stored input: the child cell's level, x, y, z and the cluster id. */
    private static final int INPUT_INTS = 5;

    /**
     * A cell as stored.
     *
     * @param key Its key in the store.
     * @param cell The cell.
     */
    record Stored(LodStore.@NonNull CellKey key, LodStore.@NonNull Cell cell) {}

    /**
     * The sections a reader of these files knows.
     *
     * @return The known sections.
     */
    public static KnownSections known() {
        return KnownSections.graphics()
                .with(LCEL, VERSION)
                .with(LCLU, CLUSTERS_VERSION)
                .with(LGRP, VERSION);
    }

    /**
     * Encode a cell as a file.
     *
     * @param cacheKey The cache key, written into the metadata.
     * @param builderVersion The builder version, written into the metadata.
     * @param key The cell's key in the store.
     * @param cell The cell.
     * @return The writer, ready to write.
     */
    static AssetWriter encode(
            @NonNull String cacheKey,
            int builderVersion,
            LodStore.@NonNull CellKey key,
            LodStore.@NonNull Cell cell) {
        return new AssetWriter()
                .metadata(
                        AssetMetadata.of(EXPORTER, String.valueOf(builderVersion))
                                .with(KEY, cacheKey))
                .section(LCEL, VERSION, true, encodeCell(key, cell))
                .section(LCLU, CLUSTERS_VERSION, true, encodeClusters(cell.dag()))
                .section(LGRP, VERSION, true, encodeGroups(cell.dag()));
    }

    /**
     * Decode a cell from a file.
     *
     * @param file The file.
     * @param cacheKey The cache key it must hold.
     * @param builderVersion The builder version it must have been written by.
     * @return The cell.
     * @throws IllegalArgumentException If it isn't the cell looked for, or doesn't make sense.
     */
    static Stored decode(@NonNull AssetFile file, @NonNull String cacheKey, int builderVersion) {
        final AssetMetadata metadata = file.metadata();
        if (!cacheKey.equals(metadata.properties().get(KEY))) {
            throw new IllegalArgumentException("The file holds another cell");
        }
        if (!String.valueOf(builderVersion)
                .equals(metadata.properties().get(AssetMetadata.EXPORTER_VERSION))) {
            throw new IllegalArgumentException("The file was built by another builder version");
        }
        final ByteBuffer cellData = require(file, LCEL);
        final ByteBuffer clusterData = require(file, LCLU);
        final ByteBuffer groupData = require(file, LGRP);

        // The cell
        final int cellStart = cellData.position();
        final int cellSize = cellData.getInt();
        final LodStore.CellKey key =
                new LodStore.CellKey(
                        cellData.getInt(), cellData.getInt(), cellData.getInt(), cellData.getInt());
        final double[] origin = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            origin[axis] = cellData.getDouble();
        }
        final double cellLeafEdge = cellData.getDouble();
        final double dagLeafEdge = cellData.getDouble();
        final int sectionCount = count(cellData.getInt());
        final int inputCount = count(cellData.getInt());
        final int reasonLength = count(cellData.getInt());
        final List<LodStore.SectionPos> sections = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; ++i) {
            sections.add(
                    new LodStore.SectionPos(
                            cellData.getInt(), cellData.getInt(), cellData.getInt()));
        }
        final List<LodStore.Input> inputs = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; ++i) {
            final LodStore.CellKey child =
                    new LodStore.CellKey(
                            cellData.getInt(),
                            cellData.getInt(),
                            cellData.getInt(),
                            cellData.getInt());
            inputs.add(new LodStore.Input(child, cellData.getInt()));
        }
        final byte[] reason = new byte[reasonLength];
        cellData.get(reason);
        if (cellData.position() - cellStart != cellSize) {
            throw new IllegalArgumentException("The cell record's size doesn't match");
        }

        final LodDag dag = new LodDag(origin);
        final int clusters = count(clusterData.getInt());
        for (int i = 0; i < clusters; ++i) {
            dag.add(decodeCluster(clusterData));
        }
        final int groups = count(groupData.getInt());
        for (int i = 0; i < groups; ++i) {
            dag.add(decodeGroup(groupData, clusters));
        }
        dag.setLeafEdge(dagLeafEdge);
        dag.setStopReason(new String(reason, StandardCharsets.UTF_8));
        for (LodStore.Input input : inputs) {
            if (input.cluster() < 0) {
                throw new IllegalArgumentException("An input names no cluster");
            }
        }
        if (inputs.size() > clusters) {
            throw new IllegalArgumentException("More inputs than clusters");
        }
        return new Stored(
                key,
                new LodStore.Cell(dag, List.copyOf(inputs), cellLeafEdge, List.copyOf(sections)));
    }

    /**
     * A required section's data.
     *
     * @param file The file.
     * @param tag The section's tag.
     * @return Its data, little endian.
     * @throws IllegalArgumentException If it's missing.
     */
    private static ByteBuffer require(@NonNull AssetFile file, int tag) {
        return file.section(tag)
                .map(Section::data)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No " + SectionTag.toString(tag) + " section"));
    }

    /**
     * Check a count read from a file.
     *
     * @param count The count.
     * @return It.
     * @throws IllegalArgumentException If it is negative.
     */
    private static int count(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("A negative count");
        }
        return count;
    }

    /**
     * Encode the cell record.
     *
     * @param key The cell's key.
     * @param cell The cell.
     * @return The data.
     */
    private static ByteBuffer encodeCell(
            LodStore.@NonNull CellKey key, LodStore.@NonNull Cell cell) {
        final byte[] reason = cell.dag().getStopReason().getBytes(StandardCharsets.UTF_8);
        final int size =
                Integer.BYTES * 4
                        + Double.BYTES * (AXES + 2)
                        + Integer.BYTES * 3
                        + cell.sections().size() * AXES * Integer.BYTES
                        + cell.inputs().size() * INPUT_INTS * Integer.BYTES
                        + reason.length
                        + Integer.BYTES;
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(size);
        out.putInt(key.level()).putInt(key.x()).putInt(key.y()).putInt(key.z());
        for (int axis = 0; axis < AXES; ++axis) {
            out.putDouble(cell.dag().getOrigin()[axis]);
        }
        out.putDouble(cell.leafEdge());
        out.putDouble(cell.dag().getLeafEdge());
        out.putInt(cell.sections().size());
        out.putInt(cell.inputs().size());
        out.putInt(reason.length);
        for (LodStore.SectionPos section : cell.sections()) {
            out.putInt(section.x()).putInt(section.y()).putInt(section.z());
        }
        for (LodStore.Input input : cell.inputs()) {
            final LodStore.CellKey child = input.child();
            out.putInt(child.level()).putInt(child.x()).putInt(child.y()).putInt(child.z());
            out.putInt(input.cluster());
        }
        out.put(reason);
        return out.flip();
    }

    /**
     * Encode every cluster.
     *
     * @param dag The DAG.
     * @return The data.
     */
    private static ByteBuffer encodeClusters(@NonNull LodDag dag) {
        final List<Packed> packed = new ArrayList<>(dag.clusters().size());
        int size = Integer.BYTES;
        for (LodDag.Cluster cluster : dag.clusters()) {
            final Packed one = pack(cluster);
            packed.add(one);
            size += clusterSize(cluster, one);
        }
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(dag.clusters().size());
        for (int c = 0; c < packed.size(); ++c) {
            final LodDag.Cluster cluster = dag.clusters().get(c);
            final Packed one = packed.get(c);
            out.putInt(clusterSize(cluster, one));
            out.putInt(cluster.id());
            out.putInt(cluster.level());
            out.putInt(cluster.triangleCount());
            out.putInt(cluster.producer());
            out.putInt(cluster.section());
            out.putDouble(cluster.error());
            putSphere(out, cluster.bound());
            putSphere(out, cluster.tight());
            out.putInt(one.vertexCount());
            out.putInt(one.attributes() == null ? 0 : 1);
            for (float value : one.positions()) {
                out.putFloat(value);
            }
            if (one.attributes() != null) {
                for (int value : one.attributes()) {
                    out.putInt(value);
                }
            }
            for (short index : one.indices()) {
                out.putShort(index);
            }
            for (int material : cluster.materials()) {
                out.putInt(material);
            }
            for (float seen : cluster.visibility()) {
                out.putFloat(seen);
            }
        }
        return out.flip();
    }

    /**
     * A cluster's corners as a table of distinct vertices and an index per corner.
     *
     * @param vertexCount How many distinct vertices there are.
     * @param positions Their positions, three floats each.
     * @param attributes Their attributes, {@link Attributes#VALUES} ints each, or null if the
     *     cluster has none.
     * @param indices Each corner's vertex, unsigned.
     */
    private record Packed(int vertexCount, float[] positions, int[] attributes, short[] indices) {}

    /**
     * A vertex's identity: its position's bits and its attributes.
     *
     * @param x The x bits.
     * @param y The y bits.
     * @param z The z bits.
     * @param normal The normal, or 0.
     * @param tangent The tangent, or 0.
     * @param uv The texture coordinates, or 0.
     */
    private record VertexKey(int x, int y, int z, int normal, int tangent, int uv) {}

    /**
     * Find a cluster's distinct vertices.
     *
     * @param cluster The cluster.
     * @return Its table and indices.
     */
    private static Packed pack(LodDag.@NonNull Cluster cluster) {
        final float[] corners = cluster.corners();
        final int[] attributes = cluster.attributes();
        final boolean any = attributes.length > 0;
        final int cornerCount = corners.length / AXES;
        final Map<VertexKey, Integer> seen = new HashMap<>();
        final float[] positions = new float[corners.length];
        final int[] kept = any ? new int[cornerCount * Attributes.VALUES] : null;
        final short[] indices = new short[cornerCount];
        int count = 0;
        for (int c = 0; c < cornerCount; ++c) {
            final int a = c * Attributes.VALUES;
            final VertexKey key =
                    new VertexKey(
                            Float.floatToRawIntBits(corners[c * AXES]),
                            Float.floatToRawIntBits(corners[c * AXES + 1]),
                            Float.floatToRawIntBits(corners[c * AXES + 2]),
                            any ? attributes[a] : 0,
                            any ? attributes[a + 1] : 0,
                            any ? attributes[a + 2] : 0);
            Integer index = seen.get(key);
            if (index == null) {
                index = count++;
                seen.put(key, index);
                System.arraycopy(corners, c * AXES, positions, index * AXES, AXES);
                if (any) {
                    System.arraycopy(
                            attributes, a, kept, index * Attributes.VALUES, Attributes.VALUES);
                }
            }
            indices[c] = (short) (int) index;
        }
        if (count > MAX_VERTICES) {
            throw new IllegalArgumentException("A cluster has too many vertices: " + count);
        }
        return new Packed(
                count,
                Arrays.copyOf(positions, count * AXES),
                any ? Arrays.copyOf(kept, count * Attributes.VALUES) : null,
                indices);
    }

    /**
     * The size of a cluster's record.
     *
     * @param cluster The cluster.
     * @param packed Its vertex table.
     * @return The bytes.
     */
    private static int clusterSize(LodDag.@NonNull Cluster cluster, @NonNull Packed packed) {
        final int triangles = cluster.triangleCount();
        return Integer.BYTES * 8
                + Double.BYTES * (1 + 2 * SPHERE_VALUES)
                + packed.vertexCount() * AXES * Float.BYTES
                + (packed.attributes() == null ? 0 : packed.attributes().length * Integer.BYTES)
                + triangles * LodMesh.CORNERS * Short.BYTES
                + triangles * (Integer.BYTES + Float.BYTES);
    }

    /**
     * Decode one cluster.
     *
     * @param in The data, at the record.
     * @return The cluster.
     */
    private static LodDag.Cluster decodeCluster(@NonNull ByteBuffer in) {
        final int start = in.position();
        final int size = in.getInt();
        final int id = in.getInt();
        final int level = in.getInt();
        final int triangles = count(in.getInt());
        final int producer = in.getInt();
        final int section = in.getInt();
        final double error = in.getDouble();
        final double[] bound = getSphere(in);
        final double[] tight = getSphere(in);
        final int vertexCount = count(in.getInt());
        final boolean any = in.getInt() != 0;
        final float[] positions = new float[vertexCount * AXES];
        for (int i = 0; i < positions.length; ++i) {
            positions[i] = in.getFloat();
        }
        final int[] table = any ? new int[vertexCount * Attributes.VALUES] : null;
        if (table != null) {
            for (int i = 0; i < table.length; ++i) {
                table[i] = in.getInt();
            }
        }
        final float[] corners = new float[triangles * TRIANGLE_VALUES];
        final int[] attributes =
                any ? new int[triangles * Attributes.PER_TRIANGLE] : Attributes.NONE;
        for (int c = 0; c < triangles * LodMesh.CORNERS; ++c) {
            final int index = Short.toUnsignedInt(in.getShort());
            if (index >= vertexCount) {
                throw new IllegalArgumentException(
                        "Cluster " + id + " has a corner past its " + vertexCount + " vertices");
            }
            System.arraycopy(positions, index * AXES, corners, c * AXES, AXES);
            if (table != null) {
                System.arraycopy(
                        table,
                        index * Attributes.VALUES,
                        attributes,
                        c * Attributes.VALUES,
                        Attributes.VALUES);
            }
        }
        final int[] materials = new int[triangles];
        for (int i = 0; i < triangles; ++i) {
            materials[i] = in.getInt();
        }
        final float[] visibility = new float[triangles];
        for (int i = 0; i < triangles; ++i) {
            visibility[i] = in.getFloat();
        }
        if (in.position() - start != size) {
            throw new IllegalArgumentException("Cluster " + id + "'s record size doesn't match");
        }
        return new LodDag.Cluster(
                id,
                level,
                corners,
                materials,
                visibility,
                attributes,
                error,
                bound,
                tight,
                producer,
                section);
    }

    /**
     * Encode every group.
     *
     * @param dag The DAG.
     * @return The data.
     */
    private static ByteBuffer encodeGroups(@NonNull LodDag dag) {
        int size = Integer.BYTES;
        for (LodDag.Group group : dag.groups()) {
            size += groupSize(group);
        }
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(dag.groups().size());
        for (LodDag.Group g : dag.groups()) {
            out.putInt(groupSize(g));
            out.putInt(g.id());
            out.putInt(g.level());
            out.putInt(g.children().length);
            out.putInt(g.outputs().length);
            out.putDouble(g.error());
            putSphere(out, g.bound());
            out.putDouble(g.simplifyError());
            out.putDouble(g.quadricError());
            out.putInt(g.trianglesIn());
            out.putInt(g.trianglesOut());
            out.putInt(g.borderEdges());
            out.putInt(g.edges());
            out.putInt(g.virtualEdges());
            out.putInt(g.rejectedFlips());
            out.putInt(g.rejectedLinks());
            out.putLong(g.buildNanos());
            for (int child : g.children()) {
                out.putInt(child);
            }
            for (int output : g.outputs()) {
                out.putInt(output);
            }
        }
        return out.flip();
    }

    /**
     * The size of a group's record.
     *
     * @param group The group.
     * @return The bytes.
     */
    private static int groupSize(LodDag.@NonNull Group group) {
        return Integer.BYTES * 5
                + Double.BYTES * (3 + SPHERE_VALUES)
                + Integer.BYTES * 7
                + Long.BYTES
                + (group.children().length + group.outputs().length) * Integer.BYTES;
    }

    /**
     * Decode one group.
     *
     * @param in The data, at the record.
     * @param clusters How many clusters the DAG has, which its ids must be under.
     * @return The group.
     */
    private static LodDag.Group decodeGroup(@NonNull ByteBuffer in, int clusters) {
        final int start = in.position();
        final int size = in.getInt();
        final int id = in.getInt();
        final int level = in.getInt();
        final int[] children = new int[count(in.getInt())];
        final int[] outputs = new int[count(in.getInt())];
        final double error = in.getDouble();
        final double[] bound = getSphere(in);
        final double simplifyError = in.getDouble();
        final double quadricError = in.getDouble();
        final int trianglesIn = in.getInt();
        final int trianglesOut = in.getInt();
        final int borderEdges = in.getInt();
        final int edges = in.getInt();
        final int virtualEdges = in.getInt();
        final int rejectedFlips = in.getInt();
        final int rejectedLinks = in.getInt();
        final long buildNanos = in.getLong();
        for (int i = 0; i < children.length; ++i) {
            children[i] = clusterId(in.getInt(), clusters);
        }
        for (int i = 0; i < outputs.length; ++i) {
            outputs[i] = clusterId(in.getInt(), clusters);
        }
        if (in.position() - start != size) {
            throw new IllegalArgumentException("Group " + id + "'s record size doesn't match");
        }
        return new LodDag.Group(
                id,
                level,
                children,
                outputs,
                error,
                bound,
                simplifyError,
                quadricError,
                trianglesIn,
                trianglesOut,
                borderEdges,
                edges,
                virtualEdges,
                rejectedFlips,
                rejectedLinks,
                buildNanos);
    }

    /**
     * Check a cluster id read from a file.
     *
     * @param id The id.
     * @param clusters How many clusters there are.
     * @return The id.
     * @throws IllegalArgumentException If there is no such cluster.
     */
    private static int clusterId(int id, int clusters) {
        if (id < 0 || id >= clusters) {
            throw new IllegalArgumentException("No cluster " + id);
        }
        return id;
    }

    /**
     * Write a sphere.
     *
     * @param out Where it goes.
     * @param sphere x, y, z, radius.
     */
    private static void putSphere(@NonNull ByteBuffer out, double @NonNull [] sphere) {
        for (int i = 0; i < SPHERE_VALUES; ++i) {
            out.putDouble(sphere[i]);
        }
    }

    /**
     * Read a sphere.
     *
     * @param in The data.
     * @return x, y, z, radius.
     */
    private static double[] getSphere(@NonNull ByteBuffer in) {
        final double[] sphere = new double[SPHERE_VALUES];
        for (int i = 0; i < SPHERE_VALUES; ++i) {
            sphere[i] = in.getDouble();
        }
        return sphere;
    }

    /** Static helpers only. */
    private LodCellFile() {}
}
