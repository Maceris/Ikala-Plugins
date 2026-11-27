package com.ikalagaming.graphics.ui.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** Linking, conversions, undo, copy and paste, and the file format. */
class GraphModelTest {
    private Graph graph;

    @BeforeEach
    void setUp() {
        graph = new Graph(TestGraphTypes.nodes(), TestGraphTypes.pins());
    }

    @Test
    void idsComeFromTheTypeAndStayUnique() {
        assertEquals("constant1", graph.add("math/constant", 0, 0).getId());
        assertEquals("constant2", graph.add("math/constant", 0, 0).getId());
        assertThrows(IllegalArgumentException.class, () -> graph.add("nope", 0, 0));
    }

    @Test
    void linksFollowTheTypes() {
        graph.add("math/constant", 0, 0);
        graph.add("math/add", 100, 0);
        graph.add("out/output", 300, 0);
        assertEquals(Graph.LinkResult.LINKED, graph.link("constant1", "value", "add1", "a"));
        assertEquals(
                Graph.LinkResult.REFUSED,
                graph.link("add1", "sum", "add1", "b"),
                "No link from a node to itself");
        assertEquals(
                Graph.LinkResult.CONVERTED,
                graph.link("add1", "sum", "output1", "color"),
                "Scalar to color goes through a gray node");
        assertTrue(graph.node("gray1") != null);
        assertEquals(
                new Graph.Link("gray1", "color", "output1", "color"),
                graph.linkInto("output1", "color"));
        assertEquals(3, graph.links().size());
        assertEquals(
                Graph.LinkResult.REFUSED,
                graph.link("output1", "nope", "add1", "a"),
                "Missing pins are refused");
    }

    @Test
    void anInputHoldsOneLink() {
        graph.add("math/constant", 0, 0);
        graph.add("math/constant", 0, 50);
        graph.add("math/add", 100, 0);
        graph.link("constant1", "value", "add1", "a");
        graph.link("constant2", "value", "add1", "a");
        assertEquals(1, graph.links().size());
        assertEquals("constant2", graph.linkInto("add1", "a").fromNode());
    }

    @Test
    void parametersLeaveOutDefaults() {
        graph.add("math/add", 0, 0);
        graph.setParam("add1", "b", "2");
        assertEquals("2", graph.param("add1", "b"));
        graph.setParam("add1", "b", "0");
        assertTrue(graph.node("add1").getParams().isEmpty());
        assertEquals("0", graph.param("add1", "b"));
        graph.setParam("add1", "nope", "1");
        assertTrue(graph.node("add1").getParams().isEmpty(), "Unknown parameters are ignored");
    }

    @Test
    void undoAndRedoEveryEdit() {
        GraphHistory history = new GraphHistory(graph);
        history.edit("Add", g -> g.add("math/constant", 0, 0));
        history.edit("Add", g -> g.add("math/add", 100, 0));
        history.edit("Link", g -> g.link("constant1", "value", "add1", "a"));
        history.edit("Delete", g -> g.remove(List.of("constant1")));
        assertTrue(graph.links().isEmpty(), "Deleting a node drops its links");

        assertEquals("Delete", history.undo());
        assertEquals(1, graph.links().size());
        assertEquals("Link", history.undo());
        assertTrue(graph.links().isEmpty());
        assertEquals("Link", history.redo());
        assertEquals(1, graph.links().size());
        history.edit("Move", g -> g.move("add1", 50, 50));
        assertNull(history.nextRedo(), "A new edit forgets what was undone");
        assertTrue(history.isDirty());
        history.markSaved();
        assertFalse(history.isDirty());
    }

    @Test
    void copiedNodesPasteUnderNewIdsWithTheirLinks() {
        graph.add("math/constant", 0, 0);
        graph.add("math/add", 100, 0);
        graph.add("out/output", 300, 0);
        graph.link("constant1", "value", "add1", "a");
        graph.link("add1", "sum", "output1", "color");
        Graph part = graph.part(Set.of("constant1", "add1"));
        assertEquals(1, part.links().size(), "Only links inside the copied nodes");

        List<String> pasted = graph.paste(part, 20, 20);
        assertEquals(List.of("constant2", "add2"), pasted);
        assertEquals(20, graph.node("constant2").getX());
        assertEquals("constant2", graph.linkInto("add2", "a").fromNode());
    }

    @Test
    void theFileFormatRoundTrips() {
        graph.add("math/constant", 3, 5);
        graph.add("math/add", 101, 0);
        graph.setParam("constant1", "value", "0.25");
        graph.setParam("add1", "b", "it's: odd");
        graph.link("constant1", "value", "add1", "a");
        String text = GraphFile.write(graph);
        assertTrue(text.contains("at: [0, 8]"), "Positions on the grid");
        assertTrue(text.contains("  - constant1.value > add1.a"));
        assertTrue(text.contains("b: 'it''s: odd'"));

        Graph read = new Graph(TestGraphTypes.nodes(), TestGraphTypes.pins());
        List<String> problems = GraphFile.read(text, read, (type, from, params) -> {});
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(text, GraphFile.write(read), "Reading and writing again changes nothing");
    }

    @Test
    void readingUpgradesOldNodesAndNamesProblems() {
        String text =
                """
                nodes:
                  add1:
                    type: math/add
                    version: 1
                    at: [0, 0]
                    set:
                      old: '5'
                  thing1:
                    type: math/unknown
                links:
                  - add1.sum > thing1.x
                """;
        Graph read = new Graph(TestGraphTypes.nodes(), TestGraphTypes.pins());
        List<String> problems =
                GraphFile.read(
                        text,
                        read,
                        (type, from, params) -> {
                            if ("math/add".equals(type) && from < 2) {
                                params.put("b", params.remove("old"));
                            }
                        });
        assertEquals("5", read.param("add1", "b"), "Renamed by the upgrade");
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void searchFindsNodesWithAFittingPin() {
        NodeTypes types = TestGraphTypes.nodes();
        assertEquals(4, types.search("", TestGraphTypes.pins(), null, false).size());
        List<String> forScalarOutput =
                types.search("", TestGraphTypes.pins(), "scalar", true).stream()
                        .map(NodeTypes.NodeType::id)
                        .toList();
        assertEquals(List.of("math/gray", "math/add", "out/output"), forScalarOutput);
        assertEquals(
                "a",
                NodeTypes.fittingPin(types.get("math/add"), TestGraphTypes.pins(), "mask", true)
                        .name(),
                "A mask converts to a scalar directly");
        assertNull(
                NodeTypes.fittingPin(
                        types.get("math/add"), TestGraphTypes.pins(), "texture", true));
    }
}
