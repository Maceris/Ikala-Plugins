package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.vulkan.LodRenderer;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.IntFunction;

/**
 * The LOD panel's measurements as IkGui tables: a DAG's levels from {@link LodStats}, and its
 * {@link Fidelity}. Rows are formatted once, when the numbers arrive, and drawn each frame.
 */
final class LodTables {

    /** How every table is drawn: bordered, striped, and as wide as its contents. */
    private static final int FLAGS =
            TableFlags.BORDERS | TableFlags.ROW_BACKGROUND | TableFlags.SIZING_FIXED_FIT;

    /** Percent in a whole. */
    private static final double PERCENT = 100;

    /**
     * One table.
     *
     * @param title A line above it saying what it shows.
     * @param headers The column headers.
     * @param rows Each row's cells.
     */
    record Table(@NonNull String title, @NonNull String[] headers, @NonNull List<String[]> rows) {}

    /**
     * The GPU cut's clusters and triangles by level, and its triangles in view.
     *
     * @param gpu What the culling pass counted.
     * @return The table.
     */
    static Table cutLevels(LodRenderer.@NonNull Stats gpu) {
        final List<String[]> rows = new ArrayList<>();
        for (int level = 0; level < gpu.levelTriangles().length; ++level) {
            rows.add(
                    new String[] {
                        Integer.toString(level),
                        String.format("%,d", gpu.levelClusters()[level]),
                        String.format("%,d", gpu.levelTriangles()[level]),
                        String.format(
                                "%.1f%%",
                                PERCENT
                                        * gpu.levelTriangles()[level]
                                        / Math.max(1, gpu.selectedTriangles())),
                        String.format("%,d", gpu.levelInView()[level])
                    });
        }
        return new Table(
                "GPU cut by level",
                new String[] {"level", "clusters", "triangles", "share", "in view"},
                rows);
    }

    /**
     * The levels table.
     *
     * @param dag The DAG.
     * @return The table.
     */
    static Table levels(@NonNull LodDag dag) {
        List<String[]> rows = new ArrayList<>();
        for (LodStats.Level l : LodStats.levels(dag)) {
            rows.add(
                    new String[] {
                        Integer.toString(l.level()),
                        String.format("%,d", l.triangles()),
                        String.format("%,d", l.clusters()),
                        Integer.toString(l.groups()),
                        Double.isNaN(l.halving()) ? "" : String.format("%.3f", l.halving()),
                        String.format("%.4f", l.maxError()),
                        String.format("%.4f", l.maxQuadricError()),
                        spread(l.groupClusters()),
                        spread(l.groupTriangles()),
                        String.format("%.1f%%", PERCENT * l.lockedShare()),
                        String.format(
                                "%.0f / %.1f / %.1f / %.1f",
                                l.buildMillis()[0],
                                l.buildMillis()[1],
                                l.buildMillis()[2],
                                l.buildMillis()[3]),
                        Integer.toString(l.virtualEdges()),
                        Integer.toString(l.rejectedFlips()),
                        Integer.toString(l.rejectedLinks())
                    });
        }
        return new Table(
                "Building stopped: " + dag.getStopReason(),
                new String[] {
                    "level",
                    "triangles",
                    "clusters",
                    "groups",
                    "halving",
                    "error",
                    "quadric estimate",
                    "group clusters",
                    "group triangles",
                    "locked",
                    "build ms (sum / p50 / p95 / max)",
                    "virtual",
                    "flips",
                    "links"
                },
                rows);
    }

    /**
     * The fidelity tables: the errors, then material shares over all area and from above.
     *
     * @param fidelity The measurements.
     * @param materialName A name for each material index.
     * @return The tables.
     */
    static List<Table> fidelity(
            @NonNull Fidelity fidelity, @NonNull IntFunction<String> materialName) {
        List<String[]> rows = new ArrayList<>();
        for (Fidelity.Level l : fidelity.levels()) {
            rows.add(
                    new String[] {
                        Integer.toString(l.level()),
                        String.format("%,d", l.triangles()),
                        percent(l.mismatch()),
                        percent(l.unavoidable()),
                        percent(l.assignment()),
                        percent(l.mismatchUp()),
                        percent(l.unavoidableUp()),
                        percent(l.assignmentUp())
                    });
        }
        List<Table> out = new ArrayList<>();
        out.add(
                new Table(
                        "Fidelity to the input, "
                                + Fidelity.SAMPLES
                                + " samples per triangle. Mismatch: area whose input material"
                                + " differs; unavoidable: the part one material per triangle"
                                + " can't fix; assignment: the rest.",
                        new String[] {
                            "level",
                            "triangles",
                            "mismatch",
                            "unavoidable",
                            "assignment",
                            "mismatch, above",
                            "unavoidable, above",
                            "assignment, above"
                        },
                        rows));
        out.add(distances(fidelity));
        final SortedSet<Integer> materials = new TreeSet<>();
        for (Fidelity.Level l : fidelity.levels()) {
            materials.addAll(l.shares().keySet());
        }
        out.add(
                shares(
                        fidelity,
                        materials,
                        materialName,
                        Fidelity.ASSIGNED,
                        Fidelity.MAPPED,
                        "Material share of area, assigned (found under the samples):"));
        out.add(
                shares(
                        fidelity,
                        materials,
                        materialName,
                        Fidelity.ASSIGNED_UP,
                        Fidelity.MAPPED_UP,
                        "Material share of area seen from above, assigned (found under the"
                                + " samples):"));
        return out;
    }

    /**
     * A table of material shares, a column per material.
     *
     * @param fidelity The measurements.
     * @param materials The materials, as columns.
     * @param materialName A name for each material index.
     * @param assigned Which share is the assigned one.
     * @param mapped Which share is the one found under the samples.
     * @param title The table's title.
     * @return The table.
     */
    private static Table shares(
            @NonNull Fidelity fidelity,
            @NonNull SortedSet<Integer> materials,
            @NonNull IntFunction<String> materialName,
            int assigned,
            int mapped,
            @NonNull String title) {
        final String[] headers = new String[materials.size() + 1];
        headers[0] = "level";
        int column = 1;
        for (int material : materials) {
            headers[column++] = materialName.apply(material);
        }
        List<String[]> rows = new ArrayList<>();
        for (Fidelity.Level l : fidelity.levels()) {
            final String[] row = new String[headers.length];
            row[0] = Integer.toString(l.level());
            column = 1;
            for (int material : materials) {
                final double[] share = l.shares().get(material);
                row[column++] =
                        share == null
                                ? ""
                                : String.format(
                                        "%.1f%% (%.1f%%)",
                                        PERCENT * share[assigned], PERCENT * share[mapped]);
            }
            rows.add(row);
        }
        return new Table(title, headers, rows);
    }

    /**
     * The distance table: how far each level is from the input, both ways, against its error.
     *
     * @param fidelity The measurements.
     * @return The table.
     */
    private static Table distances(@NonNull Fidelity fidelity) {
        List<String[]> rows = new ArrayList<>();
        for (Fidelity.Level l : fidelity.levels()) {
            final Fidelity.Distance d = l.distance();
            rows.add(
                    new String[] {
                        Integer.toString(l.level()),
                        String.format("%.3f / %.3f / %.3f", d.outMean(), d.outP95(), d.outMax()),
                        String.format("%.3f / %.3f / %.3f", d.inMean(), d.inP95(), d.inMax()),
                        String.format("%.3f", d.maxError()),
                        d.underBounded() + " of " + d.groups(),
                        String.format("%.2f", d.worstRatio())
                    });
        }
        return new Table(
                "Distance to the input. Out: from points on the level to the nearest input;"
                        + " in: from visible input to the nearest triangle its group made."
                        + " Under-bounded: groups farther than their stored error.",
                new String[] {
                    "level",
                    "out (mean / p95 / max)",
                    "in (mean / p95 / max)",
                    "max error",
                    "under-bounded",
                    "worst ratio"
                },
                rows);
    }

    /**
     * Draw a table with its title. Render thread, inside an IkGui window.
     *
     * @param table The table.
     */
    static void draw(@NonNull Table table) {
        IkGui.textWrapped(table.title());
        if (!IkGui.beginTable(table.title(), table.headers().length, FLAGS)) {
            return;
        }
        for (String header : table.headers()) {
            IkGui.tableSetupColumn(header);
        }
        IkGui.tableHeadersRow();
        for (String[] row : table.rows()) {
            IkGui.tableNextRow();
            for (String cell : row) {
                IkGui.tableNextColumn();
                IkGui.textUnformatted(cell);
            }
        }
        IkGui.endTable();
    }

    /**
     * Min, median and max, as text.
     *
     * @param values The three.
     * @return They, slash separated.
     */
    private static String spread(int @NonNull [] values) {
        return values[0] + " / " + values[1] + " / " + values[2];
    }

    /**
     * A share as a percentage.
     *
     * @param share The share.
     * @return The text.
     */
    private static String percent(double share) {
        return String.format("%.1f%%", PERCENT * share);
    }

    /** Static helpers only. */
    private LodTables() {}
}
