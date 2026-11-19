package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.vulkan.FrameTimings;

import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * What a {@link GateBenchmark} run found, what it takes to pass, and the text it is reported as:
 * the report added to {@link GateBenchmark#RESULTS_FILE} and the gate's verdict lines.
 */
public final class GateReport {

    /** The most the GPU may take per frame to pass, in milliseconds: half a 60 Hz frame. */
    public static final double GPU_BUDGET_MS = 8;

    /** Edits the gate makes. */
    public static final int GATE_DIGS = 20;

    /** Edits a second the gate makes, as a quick player digs. */
    public static final double GATE_DIG_RATE = 20;

    /** The longest an edit may take to be drawn at full resolution, in milliseconds. */
    public static final double EDIT_BUDGET_MS = 250;

    /** Bytes in a mebibyte. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /**
     * What a run found.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches were scattered over the world.
     * @param load How heavy the scene was.
     * @param buildMilliseconds How long laying out and composing took.
     * @param bakeMilliseconds How long until every section was resident, after composing.
     * @param frames How many frames were measured.
     * @param timings Each part of the frame: GPU total, CPU, then each stage.
     * @param passed Whether the GPU's 95th percentile was within budget.
     * @param width The width the scene was drawn at, in pixels.
     * @param height The height the scene was drawn at, in pixels.
     * @param gate What the level of detail gate found, or null if it wasn't checked.
     */
    public record Results(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lights,
            GateScene.@NonNull LoadStats load,
            long buildMilliseconds,
            long bakeMilliseconds,
            int frames,
            @NonNull List<FrameTimings.Timing> timings,
            boolean passed,
            int width,
            int height,
            @Nullable Gate gate) {}

    /**
     * One crack check of the level of detail's cut, as the GPU picks it.
     *
     * @param where Where the camera was.
     * @param cracks What the check found.
     * @param missing Clusters picked whose meshes weren't on the GPU: holes.
     * @param triangles The triangles the cut draws.
     * @param hidden Sections drawing themselves instead, whose edges are excused.
     */
    public record Check(
            @NonNull String where,
            CrackCheck.@NonNull Tiled cracks,
            int missing,
            long triangles,
            int hidden) {

        /**
         * Whether the cut had no cracks and no holes.
         *
         * @return True if it was clean.
         */
        public boolean isClean() {
            return cracks.isClean() && missing == 0;
        }
    }

    /**
     * What the level of detail gate found, besides the GPU time.
     *
     * @param lodBytes Level of detail mesh bytes on the GPU once loaded.
     * @param lodTotalBytes Level of detail mesh bytes kept on the CPU.
     * @param cacheRead Cells read from the disk cache while loading.
     * @param cacheBuilt Cells built while loading.
     * @param checks The crack checks.
     * @param edits Edits made.
     * @param editsUnfinished Edits that weren't drawn or didn't reach the level of detail in time.
     * @param worstDrawnMillis The longest an edit took to be drawn at full resolution.
     * @param worstDrawnFrames The most frames an edit took to be drawn.
     * @param lodMillisP95 How long edits took to reach the level of detail, 95th percentile.
     */
    public record Gate(
            long lodBytes,
            long lodTotalBytes,
            long cacheRead,
            long cacheBuilt,
            @NonNull List<Check> checks,
            int edits,
            int editsUnfinished,
            double worstDrawnMillis,
            int worstDrawnFrames,
            double lodMillisP95) {

        /**
         * Whether every check was clean.
         *
         * @return True if no cut had cracks or holes.
         */
        public boolean cracksPassed() {
            return !checks.isEmpty() && checks.stream().allMatch(Check::isClean);
        }

        /**
         * Whether every edit was drawn at full resolution in time.
         *
         * @return True if they were.
         */
        public boolean editsPassed() {
            return edits == GATE_DIGS && editsUnfinished == 0 && worstDrawnMillis <= EDIT_BUDGET_MS;
        }

        /**
         * Whether this was a reload from the cache: something was read.
         *
         * @return True if cells were read.
         */
        public boolean isReload() {
            return cacheRead > 0;
        }

        /**
         * Whether a reload built nothing.
         *
         * @return True if it read every cell.
         */
        public boolean reloadPassed() {
            return isReload() && cacheBuilt == 0;
        }
    }

    /**
     * Describe a crack check in a line.
     *
     * @param check The check.
     * @return The line.
     */
    static String describe(@NonNull Check check) {
        final CrackCheck.Tiled cracks = check.cracks();
        final StringBuilder out =
                new StringBuilder(
                        String.format(
                                "%s: %,d cracks, %,d overdrawn edges, %,d holes (%,d gaps within"
                                        + " the error, %,d edges under volumes); %,d clusters and"
                                        + " %,d triangles drawn, %,d edges checked, %,d too long to"
                                        + " check, %,d on %,d sections drawing themselves",
                                check.where(),
                                cracks.openEdges(),
                                cracks.overdrawnEdges(),
                                check.missing(),
                                cracks.nearEdges(),
                                cracks.coveredEdges(),
                                cracks.drawnClusters(),
                                check.triangles(),
                                cracks.checkedEdges(),
                                cracks.longEdges(),
                                cracks.excusedEdges(),
                                check.hidden()));
        for (double[] at : cracks.examples()) {
            out.append(String.format("; at (%.2f, %.2f, %.2f)", at[0], at[1], at[2]));
        }
        return out.toString();
    }

    /**
     * The gate's verdicts, a line each, for the report and the debug window.
     *
     * @param results The results.
     * @return The lines, empty if the gate wasn't checked.
     */
    public static List<String> gateLines(@NonNull Results results) {
        final Gate gate = results.gate();
        if (gate == null) {
            return List.of();
        }
        final FrameTimings.Timing gpu =
                results.timings().isEmpty() ? null : results.timings().get(0);
        final List<String> lines = new ArrayList<>();
        lines.add(
                String.format(
                        "GPU at the 95th percentile: %.2f of %.1f ms, %s",
                        gpu == null ? Double.NaN : gpu.p95(),
                        GPU_BUDGET_MS,
                        verdict(results.passed())));
        final long cracks = gate.checks().stream().mapToLong(c -> c.cracks().openEdges()).sum();
        final long overdrawn =
                gate.checks().stream().mapToLong(c -> c.cracks().overdrawnEdges()).sum();
        final long holes = gate.checks().stream().mapToLong(Check::missing).sum();
        lines.add(
                gate.checks().isEmpty()
                        ? "Cracks: not checked"
                        : String.format(
                                "Cracks: %,d cracks, %,d overdrawn edges and %,d holes in %d cuts,"
                                        + " %s",
                                cracks,
                                overdrawn,
                                holes,
                                gate.checks().size(),
                                verdict(gate.cracksPassed())));
        lines.add(
                String.format(
                        "Edits: %d of %d at %.0f a second, worst drawn at full resolution after %.1f"
                                + " ms (%d frames) of %.0f ms, in level of detail after %.0f ms at"
                                + " the 95th percentile, %s",
                        gate.edits() - gate.editsUnfinished(),
                        GATE_DIGS,
                        GATE_DIG_RATE,
                        gate.worstDrawnMillis(),
                        gate.worstDrawnFrames(),
                        EDIT_BUDGET_MS,
                        gate.lodMillisP95(),
                        verdict(gate.editsPassed())));
        lines.add(
                gate.isReload()
                        ? String.format(
                                "Reload: %,d cells read from the cache and %,d built, ready after"
                                        + " %.1f s, %s",
                                gate.cacheRead(),
                                gate.cacheBuilt(),
                                results.bakeMilliseconds() / 1e3,
                                verdict(gate.reloadPassed()))
                        : String.format(
                                "First build: %,d cells built, ready after %.1f s; run again to"
                                        + " check the reload",
                                gate.cacheBuilt(), results.bakeMilliseconds() / 1e3));
        lines.add(
                String.format(
                        "Level of detail: %.1f of %.1f MiB on the GPU",
                        gate.lodBytes() / BYTES_PER_MIB, gate.lodTotalBytes() / BYTES_PER_MIB));
        return lines;
    }

    /**
     * A verdict in a word.
     *
     * @param passed Whether it passed.
     * @return The word.
     */
    private static String verdict(boolean passed) {
        return passed ? "passed" : "FAILED";
    }

    /**
     * Write results out as text.
     *
     * @param results The results.
     * @return The report.
     */
    public static String report(@NonNull Results results) {
        final StringBuilder out = new StringBuilder();
        final GateScene.LoadStats load = results.load();
        out.append(
                String.format(
                        "%s gate benchmark: %s at radius %d with %,d lights, seed %d, %d x %d, %s%n",
                        LocalDateTime.now().withNano(0),
                        results.recipe(),
                        results.radius(),
                        results.lights(),
                        GateBenchmark.SEED,
                        results.width(),
                        results.height(),
                        results.passed() ? "PASSED" : "FAILED"));
        out.append(
                String.format(
                        "  Load: %,d sections in the sphere, %,d with content in %,d columns (%.2f per"
                                + " column), %,d placements, %,d triangles (%,.0f per section)%n",
                        load.sectionsInSphere(),
                        load.contentSections(),
                        load.columns(),
                        load.sectionsPerColumn(),
                        load.placements(),
                        load.triangles(),
                        load.trianglesPerSection()));
        out.append(
                String.format(
                        "  Laid out and composed in %,d ms, baked and uploaded in %,d ms, %d frames"
                                + " measured, budget %.1f ms of GPU at the 95th percentile%n",
                        results.buildMilliseconds(),
                        results.bakeMilliseconds(),
                        results.frames(),
                        GPU_BUDGET_MS));
        out.append(String.format("  %-24s %9s %9s %9s%n", "Part (ms)", "Average", "95%", "Worst"));
        for (FrameTimings.Timing timing : results.timings()) {
            out.append(
                    String.format(
                            "  %-24s %9.3f %9.3f %9.3f%n",
                            timing.name(), timing.average(), timing.p95(), timing.max()));
        }
        if (results.gate() != null) {
            for (Check check : results.gate().checks()) {
                out.append("  Crack check ").append(describe(check)).append(System.lineSeparator());
            }
            out.append("  Level of detail gate:").append(System.lineSeparator());
            for (String line : gateLines(results)) {
                out.append("    ").append(line).append(System.lineSeparator());
            }
        }
        return out.toString();
    }

    /** Static methods and records only. */
    private GateReport() {}
}
