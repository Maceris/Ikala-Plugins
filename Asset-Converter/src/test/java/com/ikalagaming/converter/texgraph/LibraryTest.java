package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/** The node library: every node runs, the tricky ones are right, and the starter graphs bake. */
class LibraryTest {
    /** The editor's starter graphs, when the editor repo is checked out beside Ikala-Plugins. */
    private static final Path STARTERS =
            Path.of("../../Ikala-Asset-Editor/plugins/Asset-Converter/data/starters");

    private final TextureLibrary library = TextureLibraries.standard();

    private final Evaluator.Settings settings = new Evaluator.Settings(16, 16, 5, null);

    @Test
    void everyNodeRunsWithNothingLinked() {
        final List<String> failed = new ArrayList<>();
        for (NodeTypes.NodeType type : library.getNodeTypes().all()) {
            if (NodeTypes.isEditorType(type.id())
                    || OutputNodes.isOutput(type.id())
                    || type.outputs().isEmpty()) {
                continue;
            }
            final Graph graph = library.newGraph();
            final String id = graph.add(type.id(), 0, 0).getId();
            final Evaluator evaluator = new Evaluator(library);
            final Plane plane =
                    evaluator.output(graph, settings, id, type.outputs().getFirst().name());
            final String error = evaluator.getErrors().get(id);
            // Nodes that need a file or a parameter say so; nothing else may fail
            if (plane == null
                    && !(error != null
                            && (error.contains("image file")
                                    || error.contains("parameter")
                                    || error.contains("Choose")))) {
                failed.add(type.id() + ": " + error);
            }
        }
        assertEquals(List.of(), failed);
    }

    @Test
    void expressionsFollowPrecedenceAndNameTheirMistakes() {
        final double[] vars = new double[10];
        vars[0] = 2;
        vars[1] = 3;
        assertEquals(14, Expression.parse("a + b * 4").value(vars), 1e-9);
        assertEquals(-4, Expression.parse("-a ^ 2").value(vars), 1e-9);
        assertEquals(1, Expression.parse("a < b ? 1 : 0").value(vars), 1e-9);
        assertEquals(0.5, Expression.parse("smoothstep(0, 4, a)").value(vars), 1e-9);
        assertEquals(1, Expression.parse("-1 % 2").value(vars), 1e-9);
        assertEquals(2, Expression.parse("clamp(a, 0, 5)").value(vars), 1e-9);
        final TextureNode.NodeException unknown =
                assertThrows(TextureNode.NodeException.class, () -> Expression.parse("a + q"));
        assertEquals("No variable called q at character 5", unknown.getMessage());
        assertThrows(TextureNode.NodeException.class, () -> Expression.parse("sin(a, b)"));
        assertThrows(TextureNode.NodeException.class, () -> Expression.parse("(a + b"));
    }

    @Test
    void quantizeKeepsToItsPaletteAndDitherMixesNeighbors() {
        final Plane gray = Plane.filled(16, 16, 0.2f, 0.2f, 0.2f, 1);
        final float[] palette = ColorNodes.palette("#000000 #ffffff");
        final Plane none = ColorNodes.quantize(gray, palette, "none", 1);
        final Plane ordered = ColorNodes.quantize(gray, palette, "ordered", 1);
        final Plane diffusion = ColorNodes.quantize(gray, palette, "diffusion", 1);
        assertEquals(2, ordered.paletteSize());
        final Set<Float> plain = new HashSet<>();
        final Set<Float> mixed = new HashSet<>();
        final Set<Float> spread = new HashSet<>();
        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                plain.add(none.get(x, y, 0));
                mixed.add(ordered.get(x, y, 0));
                spread.add(diffusion.get(x, y, 0));
            }
        }
        assertEquals(1, plain.size(), "Without dither one color");
        assertEquals(2, mixed.size(), "Ordered dither mixes both");
        assertEquals(2, spread.size(), "Error diffusion mixes both");
        final Plane colors = TexturePins.throughPalette(ordered);
        assertEquals(1, colors.get(0, 0, 3));

        final Plane noise = Noise.make(Noise.Kind.VALUE, 16, 16, 4, 2, 0.5f, 3);
        assertEquals(
                3, ColorNodes.reduce(TexturePins.convert(noise, "scalar", "color"), 3).length / 4);
    }

    @Test
    void curvesPassThroughTheirPointsWithoutOvershooting() {
        final float[][] points = OperationNodes.points("0:0 0.5:0.8 1:1");
        assertEquals(0.8f, OperationNodes.curve(points, 0.5f), 1e-6f);
        for (float t = 0; t <= 1; t += 0.01f) {
            final float value = OperationNodes.curve(points, t);
            assertTrue(value >= 0 && value <= 1, "No overshoot at " + t);
        }
        assertThrows(TextureNode.NodeException.class, () -> OperationNodes.points("0:0"));
    }

    @Test
    void distanceAndBlurWrapAroundTheTile() {
        final Plane mask = Plane.filled(16, 16, 1);
        mask.set(0, 0, 0, 0);
        final Plane distance = OperationNodes.distance(mask, 16);
        assertEquals(0, distance.get(0, 0, 0));
        assertEquals(1 / 16f, distance.get(15, 0, 0), 1e-6f, "Next to the gap across the wrap");

        final Plane dot = new Plane(16, 16, 1);
        dot.set(0, 0, 0, 1);
        final Plane blurred = OperationNodes.blur(dot, 1);
        float sum = 0;
        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                sum += blurred.get(x, y, 0);
            }
        }
        assertEquals(1, sum, 1e-4f, "Blur keeps the total");
        assertEquals(blurred.get(1, 0, 0), blurred.get(15, 0, 0), 1e-6f);
    }

    @Test
    void simplexNoiseWrapsAtTheTilesEdges() {
        for (float v = 0; v < 4; v += 0.37f) {
            assertEquals(Simplex.torus(0.25f, v, 4, 4, 9), Simplex.torus(4.25f, v, 4, 4, 9), 1e-5f);
        }
        final Plane simplex = Noise.make(Noise.Kind.SIMPLEX, 16, 16, 4, 1, 0.5f, 9);
        assertNotEquals(simplex.get(1, 1, 0), simplex.get(8, 9, 0), "It varies");
        final Plane warped = Noise.make(Noise.Kind.GRADIENT, 16, 16, 4, 1, 0.5f, 1.5f, 9);
        assertNotEquals(
                Noise.make(Noise.Kind.GRADIENT, 16, 16, 4, 1, 0.5f, 9), warped, "Warp moves it");
    }

    @Test
    void bricksHaveGapsAndAnIdPerBrick() {
        final Graph graph = library.newGraph();
        final String bricks = graph.add("pattern/bricks", 0, 0).getId();
        final Evaluator evaluator = new Evaluator(library);
        final Plane mask = evaluator.output(graph, settings, bricks, "mask");
        final Plane id = evaluator.output(graph, settings, bricks, "id");
        assertEquals(0, mask.get(3, 3, 0), "The first row's bottom is gap");
        assertEquals(0, mask.get(7, 1, 0), "The first brick's end is gap");
        assertEquals(1, mask.get(3, 2, 0));
        assertEquals(id.get(1, 1, 0), id.get(5, 1, 0), "Same brick");
        assertNotEquals(id.get(1, 1, 0), id.get(1, 5, 0), "Next row's brick");
    }

    @Test
    void starterGraphsBakeFromNodesAlone() throws Exception {
        assumeTrue(Files.isDirectory(STARTERS), "The editor repo isn't beside Ikala-Plugins");
        final List<Path> files;
        try (Stream<Path> list = Files.list(STARTERS)) {
            files = list.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX)).sorted().toList();
        }
        assertTrue(files.size() >= 12, "The whole starter set");
        final BufferedImage sheet =
                new BufferedImage(16 * 3, 16 * files.size(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < files.size(); ++row) {
            final Path file = files.get(row);
            final TextureGraph texture = new TextureGraph(library);
            assertEquals(List.of(), texture.read(Files.readString(file)), file.toString());
            for (Graph.Node node : texture.getGraph().nodes()) {
                assertTrue(
                        !CoreNodes.IMAGE.equals(node.getType()),
                        file + " is made from nodes alone");
            }
            final List<String> problems = new ArrayList<>();
            final Evaluator evaluator = new Evaluator(library);
            final Baker.Maps maps = Baker.maps(texture, evaluator, STARTERS, null, problems);
            assertEquals(List.of(), problems, file.toString());
            assertEquals(Map.of(), evaluator.getErrors(), file.toString());
            assertNotNull(maps.color(), file.toString());
            for (int x = 0; x < maps.color().getWidth(); ++x) {
                for (int y = 0; y < 16; ++y) {
                    sheet.setRGB(x, row * 16 + y, maps.color().getRGB(x, y));
                }
            }
        }
        final Path out = Path.of("build/starter-sheet.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(sheet, "png", out.toFile());
    }
}
