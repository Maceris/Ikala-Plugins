package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.vulkan.FrameTimings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The gate benchmark's report, verdict lines and presets, word for word, so moving the code around
 * can't change what a run writes out.
 */
class GateReportTest {

    /** Where the report's date ends, for leaving it out. */
    private static final int DATE_LENGTH = "2026-11-18T10:00:00".length();

    /**
     * A finished run, with or without the level of detail gate.
     *
     * @param gate What the gate found, or null.
     * @return The results.
     */
    private static GateReport.Results results(GateReport.Gate gate) {
        return new GateReport.Results(
                SyntheticTerrain.Recipe.TERRAIN,
                12,
                1000,
                new GateScene.LoadStats(1800, 900, 450, 1_234_567, 9_876_543),
                4321,
                12_345,
                600,
                List.of(
                        new FrameTimings.Timing(FrameTimings.GPU_TOTAL, 5.5, 7.25, 9.125),
                        new FrameTimings.Timing(FrameTimings.CPU, 3, 4, 5),
                        new FrameTimings.Timing("Opaque", 1.5, 2, 2.5)),
                true,
                1920,
                1080,
                gate);
    }

    /**
     * A gate that passed some checks and failed others.
     *
     * @return The gate.
     */
    private static GateReport.Gate gate() {
        final GateReport.Check clean =
                new GateReport.Check(
                        "from (0, 70, 0)",
                        new CrackCheck.Tiled(0, 0, 1200, 50_000, 3, 7, 11, 13, List.of()),
                        0,
                        2_500_000,
                        4);
        final GateReport.Check cracked =
                new GateReport.Check(
                        "over the edits",
                        new CrackCheck.Tiled(
                                2,
                                1,
                                1300,
                                60_000,
                                0,
                                0,
                                5,
                                0,
                                List.of(new double[] {1.25, 2.5, -3.75})),
                        1,
                        2_600_000,
                        0);
        return new GateReport.Gate(
                512L * 1024 * 1024,
                1024L * 1024 * 1024,
                0,
                4096,
                List.of(clean, cracked),
                GateReport.GATE_DIGS,
                1,
                120.5,
                7,
                900);
    }

    /**
     * The report without its date, with Unix line ends.
     *
     * @param results The results.
     * @return The text.
     */
    private static String report(GateReport.Results results) {
        return GateReport.report(results).substring(DATE_LENGTH).replace("\r\n", "\n");
    }

    /** A run without the gate reports its load and timings. */
    @Test
    void testReportWithoutGate() {
        assertEquals(
                " gate benchmark: TERRAIN at radius 12 with 1,000 lights, seed 20261010, 1920 x"
                        + " 1080, PASSED\n"
                        + "  Load: 1,800 sections in the sphere, 900 with content in 450 columns"
                        + " (2.00 per column), 1,234,567 placements, 9,876,543 triangles (10,974"
                        + " per section)\n"
                        + "  Laid out and composed in 4,321 ms, baked and uploaded in 12,345 ms,"
                        + " 600 frames measured, budget 8.0 ms of GPU at the 95th percentile\n"
                        + "  Part (ms)                  Average       95%     Worst\n"
                        + "  GPU total                    5.500     7.250     9.125\n"
                        + "  CPU                          3.000     4.000     5.000\n"
                        + "  Opaque                       1.500     2.000     2.500\n",
                report(results(null)));
        assertEquals(List.of(), GateReport.gateLines(results(null)));
    }

    /** A gate run adds every crack check and the gate's verdicts. */
    @Test
    void testReportWithGate() {
        final String text = report(results(gate()));
        final String checks =
                "  Crack check from (0, 70, 0): 0 cracks, 0 overdrawn edges, 0 holes (11 gaps"
                        + " within the error, 13 edges under volumes); 1,200 clusters and"
                        + " 2,500,000 triangles drawn, 50,000 edges checked, 3 too long to check,"
                        + " 7 on 4 sections drawing themselves\n"
                        + "  Crack check over the edits: 2 cracks, 1 overdrawn edges, 1 holes (5"
                        + " gaps within the error, 0 edges under volumes); 1,300 clusters and"
                        + " 2,600,000 triangles drawn, 60,000 edges checked, 0 too long to check,"
                        + " 0 on 0 sections drawing themselves; at (1.25, 2.50, -3.75)\n"
                        + "  Level of detail gate:\n";
        final int at = text.indexOf("  Crack check");
        assertEquals(checks + gateText(), text.substring(at));
    }

    /**
     * The gate's lines as the report indents them.
     *
     * @return The text.
     */
    private static String gateText() {
        final StringBuilder out = new StringBuilder();
        for (String line : expectedGateLines()) {
            out.append("    ").append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * What {@link GateReport#gateLines} gives for {@link #gate()}.
     *
     * @return The lines.
     */
    private static List<String> expectedGateLines() {
        return List.of(
                "GPU at the 95th percentile: 7.25 of 8.0 ms, passed",
                "Cracks: 2 cracks, 1 overdrawn edges and 1 holes in 2 cuts, FAILED",
                "Edits: 19 of 20 at 20 a second, worst drawn at full resolution after 120.5 ms (7"
                        + " frames) of 250 ms, in level of detail after 900 ms at the 95th"
                        + " percentile, FAILED",
                "First build: 4,096 cells built, ready after 12.3 s; run again to check the"
                        + " reload",
                "Level of detail: 512.0 of 1024.0 MiB on the GPU");
    }

    /** The verdict lines on their own. */
    @Test
    void testGateLines() {
        assertEquals(expectedGateLines(), GateReport.gateLines(results(gate())));
    }

    /** A reload that read everything passes its line. */
    @Test
    void testReloadLine() {
        final GateReport.Gate base = gate();
        final GateReport.Gate reload =
                new GateReport.Gate(
                        base.lodBytes(),
                        base.lodTotalBytes(),
                        4096,
                        0,
                        base.checks(),
                        base.edits(),
                        0,
                        base.worstDrawnMillis(),
                        base.worstDrawnFrames(),
                        base.lodMillisP95());
        assertEquals(
                "Reload: 4,096 cells read from the cache and 0 built, ready after 12.3 s, passed",
                GateReport.gateLines(results(reload)).get(3));
        assertEquals(
                "Edits: 20 of 20 at 20 a second, worst drawn at full resolution after 120.5 ms (7"
                        + " frames) of 250 ms, in level of detail after 900 ms at the 95th"
                        + " percentile, passed",
                GateReport.gateLines(results(reload)).get(2));
    }

    /** The presets the menus offer, as they are. */
    @Test
    void testPresets() {
        final StringBuilder out = new StringBuilder();
        for (GatePresets.Preset preset : GatePresets.PRESETS) {
            out.append(preset.id())
                    .append(' ')
                    .append(preset.name())
                    .append(' ')
                    .append(preset.recipe())
                    .append(' ')
                    .append(preset.radius())
                    .append(' ')
                    .append(preset.lod())
                    .append(' ')
                    .append(new TreeMap<>(preset.options()))
                    .append('\n');
            for (GateViews.View view : preset.views()) {
                out.append("  ").append(view).append('\n');
            }
        }
        assertEquals(
                "island Island ISLAND 8 false {fountain=true, glassTest=true, loop=infinity,"
                        + " loopSpeed=0, wind=6,30}\n"
                        + "  View[name=Shore, x=0.0, y=6.0, z=70.0, pitch=0.15, yaw=0.0]\n"
                        + "  View[name=Fountain, x=0.0, y=16.0, z=16.0, pitch=0.45, yaw=0.0]\n"
                        + "  View[name=Glass and water, x=0.0, y=15.0, z=20.0, pitch=0.3,"
                        + " yaw=0.0]\n"
                        + "  View[name=Figure eight, x=0.0, y=55.0, z=130.0, pitch=1.0, yaw=0.0]\n"
                        + "  View[name=Under the sea, x=0.0, y=-4.0, z=72.0, pitch=-0.1,"
                        + " yaw=0.0]\n"
                        + "hills Hills TERRAIN 12 false {fog=0.01,0.05,20, haze=true,"
                        + " physicalSky=true}\n"
                        + "hills-stream Hills, streaming TERRAIN 32 true {fog=0.01,0.05,20,"
                        + " haze=true, physicalSky=true, stream=true}\n"
                        + "caves Caves CAVES 8 false {}\n"
                        + "glass Glass GLASS 8 false {}\n"
                        + "jumble Jumble JUMBLE 16 true {}\n",
                out.toString());
        assertEquals(Map.of(), GatePresets.PRESETS.get(3).options());
    }
}
