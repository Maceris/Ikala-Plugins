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
import java.util.List;

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

    /** The version of the clusters section: 2 added each cluster's attributes. */
    public static final int CLUSTERS_VERSION = 2;

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
        int size = Integer.BYTES;
        for (LodDag.Cluster cluster : dag.clusters()) {
            size += clusterSize(cluster);
        }
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(dag.clusters().size());
        for (LodDag.Cluster cluster : dag.clusters()) {
            out.putInt(clusterSize(cluster));
            out.putInt(cluster.id());
            out.putInt(cluster.level());
            out.putInt(cluster.triangleCount());
            out.putInt(cluster.producer());
            out.putInt(cluster.section());
            out.putDouble(cluster.error());
            putSphere(out, cluster.bound());
            putSphere(out, cluster.tight());
            for (float corner : cluster.corners()) {
                out.putFloat(corner);
            }
            for (int material : cluster.materials()) {
                out.putInt(material);
            }
            for (float seen : cluster.visibility()) {
                out.putFloat(seen);
            }
            out.putInt(cluster.attributes().length);
            for (int value : cluster.attributes()) {
                out.putInt(value);
            }
        }
        return out.flip();
    }

    /**
     * The size of a cluster's record.
     *
     * @param cluster The cluster.
     * @return The bytes.
     */
    private static int clusterSize(LodDag.@NonNull Cluster cluster) {
        final int triangles = cluster.triangleCount();
        return Integer.BYTES * 6
                + Double.BYTES * (1 + 2 * SPHERE_VALUES)
                + triangles * (TRIANGLE_VALUES * Float.BYTES + Integer.BYTES + Float.BYTES)
                + Integer.BYTES
                + cluster.attributes().length * Integer.BYTES;
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
        final float[] corners = new float[triangles * TRIANGLE_VALUES];
        for (int i = 0; i < corners.length; ++i) {
            corners[i] = in.getFloat();
        }
        final int[] materials = new int[triangles];
        for (int i = 0; i < triangles; ++i) {
            materials[i] = in.getInt();
        }
        final float[] visibility = new float[triangles];
        for (int i = 0; i < triangles; ++i) {
            visibility[i] = in.getFloat();
        }
        final int attributeCount = in.getInt();
        if (attributeCount != 0 && attributeCount != triangles * Attributes.PER_TRIANGLE) {
            throw new IllegalArgumentException(
                    "Cluster " + id + " has " + attributeCount + " attribute values");
        }
        final int[] attributes = attributeCount == 0 ? Attributes.NONE : new int[attributeCount];
        for (int i = 0; i < attributeCount; ++i) {
            attributes[i] = in.getInt();
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
