package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.converter.texgraph.OutputNodes.Side;
import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/** The texture graph engine: evaluation, caching, seeds, files, layouts and bakes. */
class TextureGraphTest {
    /** Lotomation beside Ikala-Plugins, for its block textures and models, when checked out. */
    private static final Path LOTOMATION = Path.of("../../Lotomation");

    private static final Path DATA = LOTOMATION.resolve("plugins/Lotomation/data");

    private static final Path ART = LOTOMATION.resolve("art/textures/block");

    private final TextureLibrary library = TextureLibraries.standard();

    private final Evaluator.Settings settings = new Evaluator.Settings(16, 16, 7, null);

    @Test
    void srgbBytesSurviveTheTripThroughLinear() {
        for (int value = 0; value <= 255; ++value) {
            assertEquals(value, Srgb.encode(Srgb.decode(value)));
        }
    }

    @Test
    void outputsAreCachedAndOnlyDownstreamRunsAgain() {
        Graph graph = library.newGraph();
        String noise = graph.add("noise/value", 0, 0).getId();
        String constant = graph.add(CoreNodes.CONSTANT, 0, 100).getId();
        String add = graph.add("math/add", 200, 0).getId();
        graph.link(noise, "value", add, "a");
        graph.link(constant, "value", add, "b");
        Evaluator evaluator = new Evaluator(library);

        Plane first = evaluator.output(graph, settings, add, "result");
        assertNotNull(first);
        assertEquals(3, evaluator.getRuns());
        assertEquals(first, evaluator.output(graph, settings, add, "result"));
        assertEquals(3, evaluator.getRuns(), "Nothing changed, so nothing ran");

        graph.setParam(constant, "value", "0.25");
        Plane second = evaluator.output(graph, settings, add, "result");
        assertEquals(5, evaluator.getRuns(), "The constant and the add ran; the noise didn't");
        assertEquals(first.get(3, 4, 0) + 0.25f, second.get(3, 4, 0), 1e-6f);
    }

    @Test
    void addingANodeDoesNotReshuffleTheOthersSeeds() {
        Graph graph = library.newGraph();
        String noise = graph.add("noise/gradient", 0, 0).getId();
        Plane before = new Evaluator(library).output(graph, settings, noise, "value");
        graph.add("noise/gradient", 0, 200);
        Plane after = new Evaluator(library).output(graph, settings, noise, "value");
        assertEquals(before, after);
        assertNotEquals(
                before,
                new Evaluator(library)
                        .output(graph, new Evaluator.Settings(16, 16, 8, null), noise, "value"),
                "Another graph seed gives other noise");
    }

    @Test
    void noiseStaysInRangeAndVaries() {
        for (String kind : List.of("noise/value", "noise/gradient", "noise/cells")) {
            Graph graph = library.newGraph();
            String noise = graph.add(kind, 0, 0).getId();
            graph.setParam(noise, "octaves", "3");
            Plane small = new Evaluator(library).output(graph, settings, noise, "value");
            float low = 1;
            float high = 0;
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    float value = small.get(x, y, 0);
                    assertTrue(value >= 0 && value <= 1, kind + " is 0..1, not " + value);
                    low = Math.min(low, value);
                    high = Math.max(high, value);
                }
            }
            assertTrue(high - low > 0.2f, kind + " varies");
        }
    }

    @Test
    void reroutesAndNamedReroutesCarryValues() {
        Graph graph = library.newGraph();
        String constant = graph.add(CoreNodes.CONSTANT, 0, 0).getId();
        graph.setParam(constant, "value", "0.5");
        String send = graph.add("graph/send", 200, 0).getId();
        graph.setParam(send, "name", "half");
        String receive = graph.add("graph/receive", 0, 200).getId();
        graph.setParam(receive, "name", "half");
        String reroute = graph.add("graph/reroute", 100, 200).getId();
        String abs = graph.add("math/abs", 200, 200).getId();
        graph.link(constant, "value", send, "in");
        graph.link(receive, "out", reroute, "in");
        graph.link(reroute, "out", abs, "value");
        Plane out = new Evaluator(library).output(graph, settings, abs, "result");
        assertEquals(0.5f, out.get(0, 0, 0));
    }

    @Test
    void errorsStayOnTheirNodeAndBlockWhatUsesThem() {
        Graph graph = library.newGraph();
        String image = graph.add(CoreNodes.IMAGE, 0, 0).getId();
        String split = graph.add("channels/split", 200, 0).getId();
        graph.link(image, "color", split, "color");
        Evaluator evaluator = new Evaluator(library);
        assertNull(evaluator.output(graph, settings, split, "r"));
        assertEquals("Choose an image file", evaluator.getErrors().get(image));
        assertTrue(evaluator.getErrors().get(split).contains("has an error"));
    }

    @Test
    void theFileRoundTrips() {
        TextureGraph graph = new TextureGraph(library);
        graph.setWidth(32);
        graph.setSeed(42);
        graph.setTexture("block/test");
        graph.setModel("block/cube_column");
        String noise = graph.getGraph().add("noise/cells", 0, 0).getId();
        String output = graph.getGraph().add(OutputNodes.COLUMN, 240, 0).getId();
        graph.getGraph().link(noise, "value", output, "side_height");
        String text = graph.write();

        TextureGraph read = new TextureGraph(library);
        assertTrue(read.read(text).isEmpty());
        assertEquals(32, read.getWidth());
        assertEquals(42, read.getSeed());
        assertEquals("block/cube_column", read.getModel());
        assertEquals(text, read.write());
    }

    @Test
    void layoutsComeFromTheModels() throws IOException {
        assumeTrue(Files.isDirectory(DATA), "Lotomation isn't checked out beside Ikala-Plugins");
        assertEquals(1, Baker.layout(DATA, "block/cube_all").tiles());
        FaceLayout column = Baker.layout(DATA, "block/cube_column");
        assertEquals(2, column.tiles());
        assertEquals(Map.of(Side.SIDE, 0, Side.UP, 1, Side.DOWN, 1), column.tileOf());
        FaceLayout grass = Baker.layout(DATA, "block/cube_bottom_top");
        assertEquals(Map.of(Side.SIDE, 0, Side.UP, 1, Side.DOWN, 2), grass.tileOf());
        assertEquals(1, Baker.layout(DATA, "block/grass_tuft").tiles());
        assertEquals(List.of(0, 1, 2), grass.place(OutputNodes.faces(OutputNodes.BOTTOM_TOP)));
        assertFalse(placeFails(grass, OutputNodes.BOTTOM_TOP));
        assertTrue(placeFails(grass, OutputNodes.COLUMN), "A column doesn't fit grass's model");
    }

    private static boolean placeFails(FaceLayout layout, String output) {
        try {
            layout.place(OutputNodes.faces(output));
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Test
    void everyBlockGraphBakesTheMapsItReplaces(@TempDir Path temp) throws IOException {
        assumeTrue(Files.isDirectory(ART), "Lotomation isn't checked out beside Ikala-Plugins");
        // Bake into a copy, so the checked-in maps are what the bakes are compared with
        copy(DATA.resolve("textures"), temp.resolve("textures"));
        copy(DATA.resolve("models"), temp.resolve("models"));
        List<Path> graphs;
        try (Stream<Path> files = Files.list(ART)) {
            graphs =
                    files.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX)).sorted().toList();
        }
        assertEquals(12, graphs.size());
        for (Path file : graphs) {
            TextureGraph graph = new TextureGraph(library);
            assertTrue(graph.read(Files.readString(file)).isEmpty(), file + " reads cleanly");
            Baker.Result result = Baker.bake(graph, new Evaluator(library), file.getParent(), temp);
            assertTrue(result.ok(), file + ": " + result.problems());
            assertTrue(result.written().isEmpty(), file + " changed " + result.written());
            String name = graph.getTexture();
            for (String suffix : List.of("", Baker.NORMAL_SUFFIX)) {
                Path original = DATA.resolve("textures").resolve(name + suffix + Baker.PNG);
                Path baked = temp.resolve("textures").resolve(name + suffix + Baker.PNG);
                if (Files.exists(original)) {
                    assertTrue(result.unchanged().contains(baked), baked + " was baked");
                    assertTrue(
                            Baker.samePixels(read(original), read(baked)),
                            baked + " matches what it replaces");
                }
            }
        }
    }

    @Test
    void aNodeBuiltTextureBakesTheSameBytesEveryRun(@TempDir Path temp) throws Exception {
        String first = bakeNodeBuilt(temp.resolve("one"));
        String second = bakeNodeBuilt(temp.resolve("two"));
        assertEquals(first, second);
        for (String suffix : List.of("", Baker.HEIGHT_SUFFIX, Baker.NORMAL_SUFFIX)) {
            String file = "textures/block/golden" + suffix + Baker.PNG;
            assertEquals(
                    -1L,
                    Files.mismatch(
                            temp.resolve("one").resolve(file), temp.resolve("two").resolve(file)),
                    file + " is the same file every run");
        }
        // The golden hash: change it only when a node is meant to change what it makes
        assertEquals(GOLDEN, first);
    }

    /** The hash of the node-built texture's maps. */
    private static final String GOLDEN = "4370d5a62f78fdd3";

    /**
     * Bake a texture made only from nodes, and hash its files.
     *
     * @param data Where to bake.
     * @return The hash of the color, height and normal maps' pixels.
     * @throws Exception If it can't be baked or hashed.
     */
    private String bakeNodeBuilt(Path data) throws Exception {
        TextureGraph texture = new TextureGraph(library);
        texture.setSeed(3);
        texture.setTexture("block/golden");
        Graph graph = texture.getGraph();
        String cells = graph.add("noise/cells", 0, 0).getId();
        graph.setParam(cells, "size", "5");
        String grain = graph.add("noise/gradient", 0, 160).getId();
        graph.setParam(grain, "octaves", "3");
        String height = graph.add("math/lerp", 200, 0).getId();
        graph.link(cells, "value", height, "a");
        graph.link(grain, "value", height, "b");
        String dark = graph.add(CoreNodes.COLOR_INPUT, 200, 160).getId();
        graph.setParam(dark, "color", "#4a4038");
        String light = graph.add(CoreNodes.COLOR_INPUT, 200, 320).getId();
        graph.setParam(light, "color", "#9a8f80");
        String blend = graph.add("ops/blend", 400, 160).getId();
        graph.link(dark, "color", blend, "base");
        graph.link(light, "color", blend, "top");
        graph.link(height, "result", blend, "mask");
        String output = graph.add(OutputNodes.ALL, 600, 0).getId();
        graph.link(blend, "color", output, "color");
        graph.link(height, "result", output, "height");
        Baker.Result result = Baker.bake(texture, new Evaluator(library), data, data);
        assertTrue(result.ok(), result.problems().toString());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String suffix : List.of("", Baker.HEIGHT_SUFFIX, Baker.NORMAL_SUFFIX)) {
            // Pixels, not file bytes, so a JDK whose PNG encoder packs differently still passes
            BufferedImage image = read(data.resolve("textures/block/golden" + suffix + Baker.PNG));
            for (int value :
                    image.getRaster()
                            .getPixels(0, 0, image.getWidth(), image.getHeight(), (int[]) null)) {
                digest.update((byte) value);
                digest.update((byte) (value >> 8));
            }
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
    }

    private static BufferedImage read(Path file) throws IOException {
        return ImageIO.read(file.toFile());
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.toList()) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }
}
