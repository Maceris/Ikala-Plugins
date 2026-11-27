package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.UiTestDriver;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.TestGraphTypes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The graph editor's menus, popups, tooltips and held keys, driven through UI automation steps at
 * the points the editor names, as scripts drive them.
 */
class GraphAutomationTest {
    private static final String CANVAS = "graph//editor";

    private UiManager manager;
    private GraphicsContext owner;
    private UiTestDriver driver;
    private GraphEditor editor;
    private Graph graph;

    @BeforeEach
    void setUp() {
        Context context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
        driver = new UiTestDriver(manager, owner);
        graph = new Graph(TestGraphTypes.nodes(), TestGraphTypes.pins());
        editor =
                new GraphEditor("editor", new GraphHistory(graph))
                        .validator(
                                g -> {
                                    Map<String, String> errors = new LinkedHashMap<>();
                                    if (g.node("add1") != null && g.linkInto("add1", "a") == null) {
                                        errors.put("add1", "Input a isn't linked");
                                    }
                                    return errors;
                                });
        manager.add(
                owner,
                new Surface("graph")
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(900))
                        .height(Sizing.fixed(600))
                        .content(editor.getCanvas()));
        editor.getHistory().edit("Set up", g -> g.add("math/constant", 0, 0));
        editor.getHistory().edit("Set up", g -> g.add("math/add", 260, 0));
        editor.getHistory().edit("Set up", g -> g.add("out/output", 520, 0));
        editor.getHistory().clear();
        driver.frames(3);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    @Test
    void theNodeMenuCollapsesAndComments() {
        driver.run(
                steps ->
                        steps.mouseClickAt(CANVAS, "node add1", MouseButton.RIGHT)
                                .clickItem("*", "Collapse"));
        assertTrue(graph.node("add1").isCollapsed());
        assertEquals("Collapse", editor.getHistory().nextUndo());

        driver.run(
                steps ->
                        steps.mouseClickAt(CANVAS, "node add1", MouseButton.RIGHT)
                                .clickItem("*", "Comment...")
                                .typeItem("*", "##comment", "Sums two", true));
        driver.frames(2);
        assertEquals("Sums two", graph.node("add1").getComment());
    }

    @Test
    void framesAreMadeAndEditedFromTheirMenus() {
        driver.run(
                steps ->
                        steps.mouseClickAt(CANVAS, "node add1", MouseButton.LEFT)
                                .mouseClickAt(CANVAS, "at 300 300", MouseButton.RIGHT)
                                .clickItem("*", "Frame selection"));
        assertEquals(1, graph.frames().size());
        String frame = graph.frames().iterator().next().getId();

        driver.run(
                steps ->
                        steps.mouseClickAt(CANVAS, "frame " + frame, MouseButton.RIGHT)
                                .clickItem("*", "Edit...")
                                .typeItem("*", "##frame", "Math", true));
        driver.frames(2);
        assertEquals("Math", graph.frame(frame).getText());
    }

    @Test
    void ctrlFFindsAndSelects() {
        driver.run(
                steps ->
                        steps.mouseMoveTo(CANVAS, "at 300 300")
                                .key(Key.F, Key.MOD_CTRL)
                                .typeItem("*", "##find", "output", true));
        driver.frames(2);
        assertEquals(Set.of("output1"), editor.getSelection());
    }

    @Test
    void anErrorShowsInATooltip() {
        AtomicReference<String> shown = new AtomicReference<>();
        driver.run(
                steps ->
                        steps.mouseMoveTo(CANVAS, "node add1")
                                .waitForTooltip("isn't linked")
                                .tooltip(shown::set));
        assertEquals("Input a isn't linked", shown.get());

        driver.run(steps -> steps.mouseMoveTo(CANVAS, "node constant1").frames(3));
        AtomicReference<String> none = new AtomicReference<>("x");
        driver.run(steps -> steps.tooltip(none::set));
        assertNull(none.get(), "No error, no tooltip");
    }

    @Test
    void aHeldKeyPlacesItsNode() {
        graph.getNodeTypes().bindKey('M', "math/add");
        driver.run(
                steps ->
                        steps.mouseMoveTo(CANVAS, "at 100 300")
                                .keyDown(Key.M)
                                .mouseClickAt(CANVAS, "at 100 300", MouseButton.LEFT)
                                .keyUp(Key.M));
        assertNotNull(graph.node("add2"));
        assertEquals(100, graph.node("add2").getX(), 8);
    }

    @Test
    void doubleClickingALinkAndDraggingAFrameByPoints() {
        editor.link("constant1", "value", "add1", "a");
        driver.frames(2);
        driver.run(steps -> steps.mouseDoubleClickAt(CANVAS, "link constant1.value add1.a"));
        assertNotNull(graph.node("reroute1"));
        assertEquals("reroute1", graph.linkInto("add1", "a").fromNode());

        editor.select(java.util.List.of("output1"));
        String frame = editor.frameSelection("Out", 0, 0);
        driver.frames(2);
        float before = graph.node("output1").getY();
        driver.run(steps -> steps.mouseDragAt(CANVAS, "frame " + frame, "at 640 200"));
        assertTrue(graph.node("output1").getY() > before + 50, "The node moved with its frame");
    }
}
