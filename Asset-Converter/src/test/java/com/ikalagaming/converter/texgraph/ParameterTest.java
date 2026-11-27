package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Graph parameters, the nodes reading them, Switch, shared parameter files and plane views. */
class ParameterTest {
    private final TextureLibrary library = TextureLibraries.standard();

    /**
     * A scalar parameter with a range, a step, a group and an order.
     *
     * @return The parameter.
     */
    private static GraphParameter wear() {
        return new GraphParameter(
                "wear", GraphParameter.Kind.SCALAR, "0.4", 0, 2, 0.1f, List.of(), "Surface", 1);
    }

    @Test
    void aParameterIsOneLineWithoutItsDefaults() {
        assertEquals(
                "{type: scalar, value: 0.4, max: 2, step: 0.1, group: 'Surface', order: 1}",
                wear().write());
        assertEquals(
                "{type: color, value: '#808080'}",
                GraphParameter.of("tint", GraphParameter.Kind.COLOR).write());
        assertThrows(
                IllegalArgumentException.class,
                () -> GraphParameter.of("2bad", GraphParameter.Kind.SCALAR));
        assertNotNull(GraphParameter.of("on", GraphParameter.Kind.SWITCH).check("maybe"));
        assertNull(wear().check("1.5"));
    }

    @Test
    void parametersAndSharedFilesSurviveTheGraphFile() {
        TextureGraph texture = new TextureGraph(library);
        texture.putParameter(wear());
        texture.putParameter(
                new GraphParameter(
                        "style",
                        GraphParameter.Kind.CHOICE,
                        "rough",
                        0,
                        1,
                        0,
                        List.of("smooth", "rough"),
                        "",
                        0));
        texture.setShared(List.of("../shared/wear.texparams.yml"));
        String text = texture.write();
        assertTrue(text.contains("  wear: {type: scalar"), text);

        TextureGraph read = new TextureGraph(library);
        assertEquals(List.of(), read.read(text));
        assertEquals(texture.getParameters().size(), read.getParameters().size());
        assertEquals(wear(), read.parameter("wear"));
        assertEquals(List.of("smooth", "rough"), read.parameter("style").choices());
        assertEquals(texture.getShared(), read.getShared());
        assertEquals(text, read.write(), "Written the same way again");
    }

    @Test
    void parameterNodesReadTheirValueAndEvaluateAgainWhenItChanges() {
        TextureGraph texture = new TextureGraph(library);
        texture.putParameter(wear());
        Graph graph = texture.getGraph();
        String node = graph.add(ParameterNodes.typeOf(GraphParameter.Kind.SCALAR), 0, 0).getId();
        graph.setParam(node, ParameterNodes.NAME, "wear");
        Evaluator evaluator = new Evaluator(library);

        Plane first = evaluator.output(graph, texture.settings(null), node, "value");
        assertNotNull(first);
        assertEquals(0.4f, first.get(3, 3, 0), 1e-6f);
        evaluator.output(graph, texture.settings(null), node, "value");
        assertEquals(1, evaluator.getRuns(), "Unchanged, so cached");

        texture.putParameter(wear().withValue("1.5"));
        Plane second = evaluator.output(graph, texture.settings(null), node, "value");
        assertEquals(1.5f, second.get(3, 3, 0), 1e-6f);
        assertEquals(2, evaluator.getRuns());
    }

    @Test
    void aMissingOrWrongKindOfParameterIsTheNodesError() {
        TextureGraph texture = new TextureGraph(library);
        texture.putParameter(GraphParameter.of("tint", GraphParameter.Kind.COLOR));
        Graph graph = texture.getGraph();
        String node = graph.add(ParameterNodes.typeOf(GraphParameter.Kind.SCALAR), 0, 0).getId();
        Evaluator evaluator = new Evaluator(library);

        graph.setParam(node, ParameterNodes.NAME, "wear");
        assertNull(evaluator.output(graph, texture.settings(null), node, "value"));
        assertEquals("The graph has no parameter wear", evaluator.getErrors().get(node));
        graph.setParam(node, ParameterNodes.NAME, "tint");
        assertNull(evaluator.output(graph, texture.settings(null), node, "value"));
        assertEquals("tint is a color, not a scalar", evaluator.getErrors().get(node));
    }

    @Test
    void colorChoiceAndSeedParametersMakeTheirPlanes() {
        TextureGraph texture = new TextureGraph(library);
        texture.putParameter(
                GraphParameter.of("tint", GraphParameter.Kind.COLOR).withValue("#ff000080"));
        texture.putParameter(
                new GraphParameter(
                        "style",
                        GraphParameter.Kind.CHOICE,
                        "c",
                        0,
                        1,
                        0,
                        List.of("a", "b", "c"),
                        "",
                        0));
        texture.putParameter(GraphParameter.of("pick", GraphParameter.Kind.SEED).withValue("42"));
        Graph graph = texture.getGraph();
        String color = graph.add(ParameterNodes.typeOf(GraphParameter.Kind.COLOR), 0, 0).getId();
        graph.setParam(color, ParameterNodes.NAME, "tint");
        String choice = graph.add(ParameterNodes.typeOf(GraphParameter.Kind.CHOICE), 0, 0).getId();
        graph.setParam(choice, ParameterNodes.NAME, "style");
        String seed = graph.add(ParameterNodes.typeOf(GraphParameter.Kind.SEED), 0, 0).getId();
        graph.setParam(seed, ParameterNodes.NAME, "pick");
        Evaluator evaluator = new Evaluator(library);
        Evaluator.Settings settings = texture.settings(null);

        Plane red = evaluator.output(graph, settings, color, "color");
        assertEquals(1, red.get(0, 0, 0), 1e-6f);
        assertEquals(0, red.get(0, 0, 1), 1e-6f);
        assertEquals(128 / 255f, red.get(0, 0, 3), 1e-6f);
        assertEquals(2, evaluator.output(graph, settings, choice, "index").get(0, 0, 0));
        float value = evaluator.output(graph, settings, seed, "value").get(0, 0, 0);
        assertTrue(value >= 0 && value < 1);
    }

    @Test
    void aSwitchNeverEvaluatesTheBranchItTurnsOff() {
        TextureGraph texture = new TextureGraph(library);
        texture.putParameter(GraphParameter.of("on", GraphParameter.Kind.SWITCH).withValue("true"));
        Graph graph = texture.getGraph();
        String one = graph.add(CoreNodes.CONSTANT, 0, 0).getId();
        graph.setParam(one, "value", "1");
        String noise = graph.add("noise/value", 0, 100).getId();
        String toggle =
                graph.add(ParameterNodes.SWITCH_PREFIX + TexturePins.SCALAR, 200, 0).getId();
        graph.setParam(toggle, ParameterNodes.NAME, "on");
        graph.link(one, "value", toggle, "on");
        graph.link(noise, "value", toggle, "off");
        Evaluator evaluator = new Evaluator(library);

        Plane result = evaluator.output(graph, texture.settings(null), toggle, "result");
        assertEquals(1, result.get(5, 5, 0));
        assertFalse(evaluator.getCosts().containsKey(noise), "The off branch never ran");

        texture.putParameter(texture.parameter("on").withValue("false"));
        Plane off = evaluator.output(graph, texture.settings(null), toggle, "result");
        assertTrue(evaluator.getCosts().containsKey(noise));
        assertEquals(evaluator.cached(noise).get("value").get(5, 5, 0), off.get(5, 5, 0));
    }

    @Test
    void sharedFilesAreReadAndTheGraphsOwnParametersWin(@TempDir Path temp) throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared")).resolve("wear.texparams.yml");
        SharedParameters.save(
                shared,
                List.of(
                        wear().withValue("0.8"),
                        GraphParameter.of("tint", GraphParameter.Kind.COLOR)));
        Path folder = Files.createDirectories(temp.resolve("block"));
        TextureGraph texture = new TextureGraph(library);
        texture.setShared(List.of("../shared/wear.texparams.yml"));
        texture.putParameter(
                GraphParameter.of("tint", GraphParameter.Kind.COLOR).withValue("#ffffff"));

        List<String> problems = new ArrayList<>();
        Map<String, GraphParameter> all = texture.resolveParameters(folder, problems);
        assertEquals(List.of(), problems);
        assertEquals("0.8", all.get("wear").value());
        assertEquals("#ffffff", all.get("tint").value(), "The graph's own wins");

        texture.setSharedOverrides(Map.of("wear", wear().withValue("0.2")));
        assertEquals("0.2", texture.resolveParameters(folder, problems).get("wear").value());

        // A changed file is read again
        SharedParameters.save(shared, List.of(wear().withValue("1.2")));
        texture.setSharedOverrides(Map.of());
        assertEquals("1.2", texture.resolveParameters(folder, problems).get("wear").value());

        texture.setShared(List.of("missing.texparams.yml"));
        texture.resolveParameters(folder, problems);
        assertEquals(1, problems.size());
    }

    @Test
    void planeViewsMarkClippingAndMeasure() {
        Plane plane = Plane.filled(4, 4, 2);
        float[] rgba = PlaneViews.rgba(plane, TexturePins.SCALAR, true);
        assertEquals(1, rgba[0], "Striped red where above 1");
        assertEquals(0.15f, rgba[1], 1e-6f);
        assertEquals(2, PlaneViews.rgba(plane, TexturePins.SCALAR, false)[0]);
        assertEquals("2.000 to 2.000", PlaneViews.range(plane));

        Plane color = Plane.filled(2, 2, 0.5f, 0.25f, 0, 1);
        float[] average = PlaneViews.average(color);
        assertEquals(0.5f, average[0], 1e-6f);
        assertEquals(0.2126f * 0.5f + 0.7152f * 0.25f, PlaneViews.luminance(average), 1e-6f);
        assertEquals(
                "r 0.500 to 0.500, g 0.250 to 0.250, b 0.000 to 0.000, a 1.000 to 1.000",
                PlaneViews.range(color));
    }

    @Test
    void costsAreKeptForNodesThatRan() {
        Graph graph = library.newGraph();
        String noise = graph.add("noise/value", 0, 0).getId();
        Evaluator evaluator = new Evaluator(library);
        assertNull(evaluator.cached(noise));
        evaluator.output(graph, new Evaluator.Settings(16, 8, 1, null), noise, "value");
        assertNotNull(evaluator.cached(noise));
        assertEquals(16 * 8 * Float.BYTES, evaluator.getCosts().get(noise).bytes());
    }
}
