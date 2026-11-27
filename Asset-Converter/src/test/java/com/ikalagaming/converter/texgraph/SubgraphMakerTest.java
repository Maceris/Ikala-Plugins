package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Making a subgraph from part of a graph changes nothing it makes. */
class SubgraphMakerTest {
    /** The editor's starter graphs, beside Ikala-Plugins. */
    private static final Path STARTERS =
            Path.of("../../Ikala-Asset-Editor/plugins/Asset-Converter/data/starters");

    @TempDir Path folder;

    @AfterEach
    void forget() {
        SubgraphNodes.forget();
    }

    @Test
    void everyStarterMakesTheSamePixelsWithHalfOfItAsASubgraph() throws Exception {
        assumeTrue(Files.isDirectory(STARTERS), "The editor repo isn't beside Ikala-Plugins");
        final List<Path> files;
        try (Stream<Path> list = Files.list(STARTERS)) {
            files = list.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX)).sorted().toList();
        }
        assertTrue(files.size() >= 12, "The whole starter set");
        for (Path starter : files) {
            final String name = starter.getFileName().toString();
            final Path file = folder.resolve(name);
            Files.copy(starter, file);
            final TextureGraph texture = new TextureGraph(TextureLibraries.standard());
            assertEquals(List.of(), texture.read(Files.readString(file), folder), name);
            final BufferedImage[] before = maps(texture, name);

            final Set<String> half = upstreamHalf(texture.getGraph());
            final Path sub = folder.resolve("part_" + name);
            final SubgraphMaker.Made made = SubgraphMaker.make(texture, folder, half, sub);
            SubgraphNodes.refresh(made.type(), folder, texture.getLibrary(), texture.getGraph());
            SubgraphMaker.replace(texture.getGraph(), made);
            assertArraysEqual(before, maps(texture, name), name + " with a subgraph");

            // And as saved and read again
            final TextureGraph read = new TextureGraph(TextureLibraries.standard());
            assertEquals(List.of(), read.read(texture.write(), folder), name);
            assertArraysEqual(before, maps(read, name), name + " read again");
        }
    }

    @Test
    void choosingNodesAroundAnOutsideOneIsRefused() throws Exception {
        final TextureGraph texture = new TextureGraph(TextureLibraries.standard());
        final Graph graph = texture.getGraph();
        graph.put("a", "noise/value", 0, 0);
        graph.put("b", "math/abs", 200, 0);
        graph.put("c", "math/abs", 400, 0);
        graph.link("a", "value", "b", "value");
        graph.link("b", "result", "c", "value");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SubgraphMaker.make(
                                texture,
                                folder,
                                Set.of("a", "c"),
                                folder.resolve("x.texgraph.yml")),
                "b is fed by a and feeds c");
    }

    /**
     * Half the graph, closed upstream: a node in the middle and everything feeding it.
     *
     * @param graph The graph.
     * @return The nodes.
     */
    private static Set<String> upstreamHalf(Graph graph) {
        final List<Graph.Node> nodes = new ArrayList<>();
        for (Graph.Node node : graph.nodes()) {
            if (!OutputNodes.isOutput(node.getType())
                    && !NodeTypes.isEditorType(node.getType())
                    && !graph.upstream(node.getId()).isEmpty()) {
                nodes.add(node);
            }
        }
        nodes.sort(
                Comparator.comparingInt((Graph.Node n) -> graph.upstream(n.getId()).size())
                        .thenComparing(Graph.Node::getId));
        final Graph.Node middle = nodes.get(nodes.size() / 2);
        final Set<String> half = new LinkedHashSet<>(graph.upstream(middle.getId()));
        half.add(middle.getId());
        return half;
    }

    /**
     * A graph's color and normal maps as pixels.
     *
     * @param texture The graph.
     * @param name Its name, for messages.
     * @return The color's pixels, then the normal's.
     */
    private BufferedImage[] maps(TextureGraph texture, String name) {
        final List<String> problems = new ArrayList<>();
        final Evaluator evaluator = new Evaluator(texture.getLibrary());
        final Baker.Maps maps = Baker.maps(texture, evaluator, folder, null, problems);
        assertEquals(List.of(), problems, name);
        assertEquals(java.util.Map.of(), evaluator.getErrors(), name);
        assertNotNull(maps.color(), name);
        return new BufferedImage[] {maps.color(), maps.normal()};
    }

    private static void assertArraysEqual(
            BufferedImage[] expected, BufferedImage[] got, String what) {
        for (int i = 0; i < expected.length; ++i) {
            if (expected[i] == null) {
                continue;
            }
            assertNotNull(got[i], what);
            assertArrayEquals(pixels(expected[i]), pixels(got[i]), what + " map " + i);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
