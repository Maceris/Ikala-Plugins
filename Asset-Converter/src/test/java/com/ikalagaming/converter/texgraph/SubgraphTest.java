package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/** Subgraphs: graphs placed in other graphs as one node. */
class SubgraphTest {
    private static final String NOISE = "noise/value";

    private static final String MULTIPLY = "math/multiply";

    private static final String SCALAR_IN = SubgraphNodes.INPUT_PREFIX + TexturePins.SCALAR;

    private static final String SCALAR_OUT = SubgraphNodes.OUTPUT_PREFIX + TexturePins.SCALAR;

    @TempDir Path folder;

    @AfterEach
    void forget() {
        SubgraphNodes.forget();
    }

    /**
     * Write a subgraph: noise n times its input k, out as result.
     *
     * @param name The file's name.
     * @return The file.
     */
    private Path noiseTimes(String name) throws IOException {
        final TextureGraph sub = new TextureGraph(TextureLibraries.standard());
        final Graph graph = sub.getGraph();
        graph.put("k", SCALAR_IN, 0, 100);
        graph.setParam("k", SubgraphNodes.NAME, "k");
        graph.put("n", NOISE, 0, 0);
        graph.put("m", MULTIPLY, 200, 0);
        graph.put("out", SCALAR_OUT, 400, 0);
        graph.setParam("out", SubgraphNodes.NAME, "result");
        graph.link("n", "value", "m", "a");
        graph.link("k", "value", "m", "b");
        graph.link("m", "result", "out", "value");
        final Path file = folder.resolve(name);
        Files.writeString(file, sub.write());
        return file;
    }

    /**
     * A graph placing a subgraph file, with a constant of 0.5 into its input k.
     *
     * @param file The subgraph.
     * @return The graph.
     */
    private TextureGraph placing(Path file) {
        final TextureGraph outer = new TextureGraph(TextureLibraries.standard());
        final Graph graph = outer.getGraph();
        final String type = SubgraphNodes.typeFor(folder, file);
        SubgraphNodes.refresh(type, folder, outer.getLibrary(), graph);
        graph.put("k", CoreNodes.CONSTANT, 0, 0);
        graph.setParam("k", "value", "0.5");
        graph.put("s", type, 200, 0);
        graph.link("k", "value", "s", "k");
        return outer;
    }

    @Test
    void aSubgraphMakesWhatItsNodesMadeInline() throws IOException {
        final TextureGraph inline = new TextureGraph(TextureLibraries.standard());
        final Graph graph = inline.getGraph();
        graph.put("k", CoreNodes.CONSTANT, 0, 0);
        graph.setParam("k", "value", "0.5");
        graph.put("n", NOISE, 0, 100);
        graph.put("m", MULTIPLY, 200, 0);
        graph.link("n", "value", "m", "a");
        graph.link("k", "value", "m", "b");
        final Plane expected =
                new Evaluator(inline.getLibrary())
                        .output(graph, inline.settings(folder), "m", "result");

        final TextureGraph outer = placing(noiseTimes("times.texgraph.yml"));
        final Evaluator evaluator = new Evaluator(outer.getLibrary());
        final Plane placed =
                evaluator.output(outer.getGraph(), outer.settings(folder), "s", "result");
        assertEquals(expected, placed, "The noise keeps its ID inside, and so its seed");

        outer.getGraph().setParam("s", SubgraphNodes.VARIATION, "3");
        assertNotEquals(
                expected,
                evaluator.output(outer.getGraph(), outer.settings(folder), "s", "result"),
                "A seed variation moves it");
    }

    @Test
    void parametersComeFromTheNodeThenTheOuterGraphThenTheSubgraph() throws IOException {
        final TextureGraph sub = new TextureGraph(TextureLibraries.standard());
        sub.putParameter(GraphParameter.of("amount", GraphParameter.Kind.SCALAR).withValue("2"));
        final Graph inner = sub.getGraph();
        inner.put("p", ParameterNodes.typeOf(GraphParameter.Kind.SCALAR), 0, 0);
        inner.setParam("p", ParameterNodes.NAME, "amount");
        inner.put("out", SCALAR_OUT, 200, 0);
        inner.setParam("out", SubgraphNodes.NAME, "amount");
        inner.link("p", "value", "out", "value");
        final Path file = folder.resolve("amount.texgraph.yml");
        Files.writeString(file, sub.write());

        final TextureGraph outer = new TextureGraph(TextureLibraries.standard());
        final String type = SubgraphNodes.typeFor(folder, file);
        SubgraphNodes.refresh(type, folder, outer.getLibrary(), outer.getGraph());
        outer.getGraph().put("s", type, 0, 0);
        final Evaluator evaluator = new Evaluator(outer.getLibrary());
        assertEquals(2, read(evaluator, outer, "amount"), "The subgraph's own value");
        outer.putParameter(GraphParameter.of("amount", GraphParameter.Kind.SCALAR).withValue("3"));
        assertEquals(3, read(evaluator, outer, "amount"), "The outer graph's, by name");
        outer.getGraph().setParam("s", "amount", "5");
        assertEquals(5, read(evaluator, outer, "amount"), "The node's own");
    }

    private float read(Evaluator evaluator, TextureGraph outer, String pin) {
        final Plane plane = evaluator.output(outer.getGraph(), outer.settings(folder), "s", pin);
        assertNotNull(plane, evaluator.getErrors().toString());
        return plane.get(0, 0, 0);
    }

    @Test
    void aPinTheSubgraphLostStaysLinkedAndShowsAsAnError() throws IOException {
        final Path file = noiseTimes("lost.texgraph.yml");
        final TextureGraph outer = placing(file);
        outer.getGraph().put("abs", "math/abs", 400, 0);
        outer.getGraph().link("s", "result", "abs", "value");
        final String text = outer.write();

        // The output is renamed
        Files.writeString(file, Files.readString(file).replace("name: result", "name: renamed"));
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        final TextureGraph read = new TextureGraph(TextureLibraries.standard());
        assertEquals(List.of(), read.read(text, folder), "Nothing left out");
        assertTrue(
                read.getGraph().links().stream().anyMatch(link -> "result".equals(link.fromPin())),
                "The link stays");
        final Evaluator evaluator = new Evaluator(read.getLibrary());
        assertNull(evaluator.output(read.getGraph(), read.settings(folder), "abs", "result"));
        assertTrue(
                evaluator.getErrors().getOrDefault("s", "").contains("no longer an output"),
                evaluator.getErrors().toString());
    }

    @Test
    void aSubgraphPlacingItselfIsAnError() throws IOException {
        final Path file = folder.resolve("self.texgraph.yml");
        Files.writeString(
                file,
                "texture graph: 1\nnodes:\n"
                        + "  out: {type: 'subgraph/output/scalar', at: [200, 0], set: {name: result}}\n"
                        + "  s: {type: 'subgraph:self.texgraph.yml', at: [0, 0]}\n"
                        + "links:\n- s.result > out.value\n");
        final TextureGraph outer = new TextureGraph(TextureLibraries.standard());
        final String type = SubgraphNodes.typeFor(folder, file);
        SubgraphNodes.refresh(type, folder, outer.getLibrary(), outer.getGraph());
        outer.getGraph().put("s", type, 0, 0);
        final Evaluator evaluator = new Evaluator(outer.getLibrary());
        evaluator.output(outer.getGraph(), outer.settings(folder), "s", "result");
        assertTrue(
                evaluator.getErrors().getOrDefault("s", "").contains("places itself"),
                evaluator.getErrors().toString());
    }

    @Test
    void subgraphsNestAndAnEditInsideReachesTheOuterGraph() throws IOException {
        final Path inner = noiseTimes("inner.texgraph.yml");
        // A middle subgraph placing the inner one
        final TextureGraph middle = new TextureGraph(TextureLibraries.standard());
        final String innerType = SubgraphNodes.typeFor(folder, inner);
        SubgraphNodes.refresh(innerType, folder, middle.getLibrary(), middle.getGraph());
        final Graph graph = middle.getGraph();
        graph.put("k", SCALAR_IN, 0, 0);
        graph.setParam("k", SubgraphNodes.NAME, "k");
        graph.put("s", innerType, 200, 0);
        graph.put("out", SCALAR_OUT, 400, 0);
        graph.setParam("out", SubgraphNodes.NAME, "result");
        graph.link("k", "value", "s", "k");
        graph.link("s", "result", "out", "value");
        final Path middleFile = folder.resolve("middle.texgraph.yml");
        Files.writeString(middleFile, middle.write());

        final TextureGraph outer = placing(middleFile);
        final Evaluator evaluator = new Evaluator(outer.getLibrary());
        final Plane before =
                evaluator.output(outer.getGraph(), outer.settings(folder), "s", "result");
        assertNotNull(before, evaluator.getErrors().toString());

        Files.writeString(inner, Files.readString(inner).replace(MULTIPLY, "math/add"));
        Files.setLastModifiedTime(inner, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        final Plane after =
                evaluator.output(outer.getGraph(), outer.settings(folder), "s", "result");
        assertNotNull(after, evaluator.getErrors().toString());
        assertNotEquals(before, after, "The edit two levels down shows");
    }
}
