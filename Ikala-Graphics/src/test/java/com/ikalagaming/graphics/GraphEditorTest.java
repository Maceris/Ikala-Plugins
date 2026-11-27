package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphHistory;
import com.ikalagaming.graphics.ui.graph.model.TestGraphTypes;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** The graph editor with real mouse and key input, on a headless IkGui context. */
class GraphEditorTest {
    private Context context;
    private UiManager manager;
    private GraphicsContext owner;
    private GraphEditor editor;
    private Graph graph;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD;
        context.io.backendFlags |= BackendFlags.HAS_GAMEPAD;
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
        graph = new Graph(TestGraphTypes.nodes(), TestGraphTypes.pins());
        editor = new GraphEditor("editor", new GraphHistory(graph));
        manager.add(
                owner,
                new Surface("graph")
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(900))
                        .height(Sizing.fixed(600))
                        .content(editor.getCanvas()));
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private void frame() {
        IkGui.newFrame();
        manager.draw();
        IkGui.render();
        manager.dispatchEvents();
    }

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            frame();
        }
    }

    private void drag(Vector2f from, Vector2f to) {
        context.io.addMousePosEvent(from.x, from.y);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        for (int step = 1; step <= 6; ++step) {
            float t = step / 6.0f;
            context.io.addMousePosEvent(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t);
            frame();
        }
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    private void click(Vector2f at) {
        context.io.addMousePosEvent(at.x, at.y);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    private void press(Key key, boolean ctrl) {
        if (ctrl) {
            context.io.addKeyEvent(Key.MOD_CTRL, true);
            context.io.addKeyEvent(Key.LEFT_CTRL, true);
        }
        context.io.addKeyEvent(key, true);
        frame();
        context.io.addKeyEvent(key, false);
        if (ctrl) {
            context.io.addKeyEvent(Key.MOD_CTRL, false);
            context.io.addKeyEvent(Key.LEFT_CTRL, false);
        }
        frame();
    }

    private static Vector2f middle(RectFloat rect) {
        return new Vector2f(
                (rect.getLeft() + rect.getRight()) / 2, (rect.getTop() + rect.getBottom()) / 2);
    }

    /** A constant, an add and an output, laid out left to right. */
    private void threeNodes() {
        editor.getHistory().edit("Set up", g -> g.add("math/constant", 0, 0));
        editor.getHistory().edit("Set up", g -> g.add("math/add", 260, 0));
        editor.getHistory().edit("Set up", g -> g.add("out/output", 520, 0));
        editor.getHistory().clear();
        frames(3);
    }

    @Test
    void draggingFromAnOutputToAnInputLinks() {
        threeNodes();
        drag(
                editor.pinOnScreen("constant1", "value", false),
                editor.pinOnScreen("add1", "a", true));
        assertEquals(1, graph.links().size());
        assertEquals("constant1", graph.linkInto("add1", "a").fromNode());
        assertEquals("Link", editor.getHistory().nextUndo());
    }

    @Test
    void linkingAcrossTypesAddsTheConversionNode() {
        threeNodes();
        drag(
                editor.pinOnScreen("add1", "sum", false),
                editor.pinOnScreen("output1", "color", true));
        assertNotNull(graph.node("gray1"), "A scalar into a color goes through gray");
        assertEquals("gray1", graph.linkInto("output1", "color").fromNode());
    }

    @Test
    void aRefusedLinkChangesNothing() {
        threeNodes();
        drag(editor.pinOnScreen("output1", "color", true), editor.pinOnScreen("add1", "a", true));
        assertTrue(graph.links().isEmpty(), "Two inputs don't link");
        assertNull(editor.getHistory().nextUndo());
    }

    @Test
    void aLinkedInputPicksItsLinkUp() {
        threeNodes();
        editor.link("constant1", "value", "add1", "a");
        frames(2);
        drag(editor.pinOnScreen("add1", "a", true), editor.pinOnScreen("add1", "b", true));
        assertNull(graph.linkInto("add1", "a"));
        assertEquals("constant1", graph.linkInto("add1", "b").fromNode(), "Moved to b");

        Vector2f empty = editor.getCanvas().toScreen(300, 300);
        drag(editor.pinOnScreen("add1", "b", true), empty);
        assertTrue(graph.links().isEmpty(), "Dropped on nothing, it is gone");
    }

    @Test
    void droppingOnEmptyCanvasOpensTheSearch() {
        threeNodes();
        drag(
                editor.pinOnScreen("constant1", "value", false),
                editor.getCanvas().toScreen(100, 300));
        frames(2);
        assertTrue(editor.isSearchOpen());
        press(Key.ESCAPE, false);
        frames(2);
        assertFalse(editor.isSearchOpen());

        String added =
                editor.placeFirst(
                        "add",
                        new Vector2f(100, 300),
                        new GraphEditor.PinRef("constant1", "value", false, "scalar"));
        assertEquals("add2", added);
        assertEquals(
                "constant1",
                graph.linkInto("add2", "a").fromNode(),
                "Linked to its first fitting pin");
    }

    @Test
    void clicksAndTheBoxSelect() {
        threeNodes();
        click(middle(editor.viewOf("add1").getRect()));
        assertEquals(Set.of("add1"), editor.getSelection());

        context.io.addKeyEvent(Key.MOD_CTRL, true);
        click(middle(editor.viewOf("output1").getRect()));
        context.io.addKeyEvent(Key.MOD_CTRL, false);
        frame();
        assertEquals(Set.of("add1", "output1"), editor.getSelection());

        Vector2f from = editor.getCanvas().toScreen(-40, -40);
        Vector2f to = editor.getCanvas().toScreen(300, 100);
        drag(from, to);
        assertEquals(Set.of("constant1", "add1"), editor.getSelection());
    }

    @Test
    void draggingMovesTheSelectionAsOneEdit() {
        threeNodes();
        editor.select(List.of("constant1", "add1"));
        frames(2);
        RectFloat rect = editor.viewOf("add1").getRect();
        Vector2f start = middle(rect);
        float unit = editor.getCanvas().getUnit();
        drag(start, new Vector2f(start.x + 80 * unit, start.y + 40 * unit));
        assertEquals(80, graph.node("constant1").getX(), 1);
        assertEquals(40, graph.node("constant1").getY(), 1);
        assertEquals(340, graph.node("add1").getX(), 1);
        assertEquals(520, graph.node("output1").getX(), 0.01, "Unselected nodes stay");
        assertEquals("Move nodes", editor.getHistory().nextUndo());
        press(Key.Z, true);
        assertEquals(0, graph.node("constant1").getX(), 0.01, "Undone in one step");
    }

    @Test
    void deleteUndoAndRedoFromTheKeyboard() {
        threeNodes();
        editor.link("constant1", "value", "add1", "a");
        frames(2);
        click(middle(editor.viewOf("add1").getRect()));
        press(Key.DELETE, false);
        assertNull(graph.node("add1"));
        assertTrue(graph.links().isEmpty());
        press(Key.Z, true);
        assertNotNull(graph.node("add1"));
        assertEquals(1, graph.links().size());
        press(Key.Y, true);
        assertNull(graph.node("add1"));
    }

    @Test
    void copyPasteAndDuplicate() {
        threeNodes();
        editor.link("constant1", "value", "add1", "a");
        editor.select(List.of("constant1", "add1"));
        String text = editor.copySelection();
        assertTrue(text.startsWith(GraphEditor.CLIPBOARD_HEADER));
        List<String> pasted = editor.paste(text, 0, 200);
        assertEquals(List.of("add2", "constant2"), pasted);
        assertEquals("constant2", graph.linkInto("add2", "a").fromNode());
        assertEquals(200, graph.node("constant2").getY(), 0.01);
        assertEquals(Set.of("add2", "constant2"), editor.getSelection());

        List<String> duplicated = editor.duplicateSelection();
        assertEquals(2, duplicated.size());
        assertTrue(editor.paste("not a graph", 0, 0).isEmpty());
    }

    @Test
    void doubleClickingALinkAddsAReroute() {
        threeNodes();
        editor.link("constant1", "value", "add1", "a");
        frames(2);
        Vector2f from = editor.pinOnScreen("constant1", "value", false);
        Vector2f to = editor.pinOnScreen("add1", "a", true);
        Vector2f mid = new Vector2f(from).add(to).mul(0.5f);
        click(mid);
        click(mid);
        frames(2);
        assertNotNull(graph.node("reroute1"), "A double-click on the link adds a reroute");
        assertEquals("reroute1", graph.linkInto("add1", "a").fromNode());
        assertEquals("constant1", graph.linkInto("reroute1", "in").fromNode());
        assertEquals("scalar", graph.effectiveType("reroute1", "out", false));
        assertEquals("Add reroute", editor.getHistory().nextUndo());
        Vector2f dot = middle(editor.viewOf("reroute1").getRect());
        assertEquals(mid.x, dot.x, 2, "The dot sits where the link was clicked");
        assertEquals(mid.y, dot.y, 2);
    }

    @Test
    void framesCarryTheirNodesAndSizeByTheirCorner() {
        threeNodes();
        editor.select(List.of("constant1", "add1"));
        String id = editor.frameSelection("Inputs", 0, 0);
        frames(2);
        Graph.Frame made = graph.frame(id);
        assertEquals(List.of("constant1", "add1"), graph.nodesIn(id));
        float unit = editor.getCanvas().getUnit();

        Vector2f title = editor.getCanvas().toScreen(made.getX() + 40, made.getY() + 8);
        drag(title, new Vector2f(title.x, title.y + 100 * unit));
        assertEquals(100, graph.node("constant1").getY(), 1, "The nodes in it come along");
        assertEquals(100, graph.node("add1").getY(), 1);
        assertEquals(0, graph.node("output1").getY(), 0.01, "Outside, it stays");
        assertEquals("Move frame", editor.getHistory().nextUndo());

        Graph.Frame moved = graph.frame(id);
        float width = moved.getWidth();
        Vector2f corner =
                editor.getCanvas()
                        .toScreen(
                                moved.getX() + moved.getWidth() - 3,
                                moved.getY() + moved.getHeight() - 3);
        drag(corner, new Vector2f(corner.x + 50 * unit, corner.y));
        assertEquals(width + 50, graph.frame(id).getWidth(), 1);
        assertEquals("Size frame", editor.getHistory().nextUndo());
        editor.undo();
        editor.undo();
        assertEquals(0, graph.node("constant1").getY(), 0.01, "Each drag is one edit");
    }

    @Test
    void collapsedAndTrimmedNodesShrink() {
        threeNodes();
        editor.link("constant1", "value", "add1", "a");
        frames(2);
        float full = editor.viewOf("add1").getRect().getHeight();
        editor.setHideUnused(List.of("add1"), true);
        frames(2);
        assertTrue(editor.viewOf("add1").getRect().getHeight() < full, "Unlinked b is hidden");
        assertNull(editor.pinOnScreen("add1", "b", true));
        assertNotNull(editor.pinOnScreen("add1", "a", true));

        editor.setCollapsed(List.of("add1"), true);
        frames(2);
        Vector2f a = editor.pinOnScreen("add1", "a", true);
        assertNotNull(a, "A collapsed node's links meet its side");
        assertEquals(1, graph.links().size(), "Collapsing keeps the links");
        editor.undo();
        editor.undo();
        frames(2);
        assertEquals(full, editor.viewOf("add1").getRect().getHeight(), 0.01);
    }

    @Test
    void holdingABoundKeyAndClickingPlacesANode() {
        threeNodes();
        graph.getNodeTypes().bindKey('m', "math/add");
        context.io.addKeyEvent(Key.M, true);
        frame();
        click(editor.getCanvas().toScreen(100, 300));
        context.io.addKeyEvent(Key.M, false);
        frame();
        assertNotNull(graph.node("add2"));
        assertEquals(100, graph.node("add2").getX(), 8);
        assertEquals(
                1, graph.nodes().stream().filter(n -> "math/add".equals(n.getType())).count() - 1);
    }

    @Test
    void findSelectsAndAlignLinesUp() {
        threeNodes();
        editor.setComment("output1", "The final color");
        assertEquals(List.of("output1"), editor.find("final"));
        assertEquals(List.of("add1"), editor.find("ADD"));
        editor.focus("add1");
        frames(2);
        assertEquals(Set.of("add1"), editor.getSelection());

        editor.getHistory().edit("Set up", g -> g.move("add1", 260, 70));
        frames(2);
        editor.select(List.of("constant1", "add1", "output1"));
        editor.align(GraphEditor.Align.TOP);
        assertEquals(0, graph.node("add1").getY(), 0.01);
        editor.align(GraphEditor.Align.LEFT);
        assertEquals(0, graph.node("output1").getX(), 0.01);
    }

    @Test
    void straightenLevelsTheLinkAndRemoveUnusedTrims() {
        threeNodes();
        editor.getHistory().edit("Set up", g -> g.move("add1", 260, 55));
        editor.link("constant1", "value", "add1", "a");
        editor.link("add1", "sum", "output1", "color");
        frames(2);
        assertTrue(editor.straighten("add1"));
        frames(2);
        assertEquals(
                editor.pinOnScreen("constant1", "value", false).y,
                editor.pinOnScreen("add1", "a", true).y,
                0.5f);
        editor.getHistory().edit("Set up", g -> g.add("math/constant", 0, 300));
        assertEquals(1, editor.removeUnused());
        assertNull(graph.node("constant2"));
    }

    @Test
    void clickingTheMinimapPans() {
        threeNodes();
        // Far enough out that the view can't show it all, so there is somewhere to pan
        editor.getHistory().edit("Set up", g -> g.add("math/constant", 2400, 1600));
        frames(3);
        Vector2f before = editor.getCanvas().toScreen(0, 0);
        RectFloat map = editor.getMinimapRect();
        assertTrue(map.getWidth() > 0, "It shows in the corner");
        Vector2f corner = new Vector2f(map.getRight() - 3, map.getBottom() - 3);
        click(corner);
        frames(2);
        Vector2f after = editor.getCanvas().toScreen(0, 0);
        assertTrue(before.distance(after) > 100, "The view moved to the far node");
        assertEquals(4, graph.nodes().size());
        assertTrue(editor.getSelection().isEmpty(), "No box selection either");

        editor.setMinimap(false);
        frames(2);
        assertEquals(0, editor.getMinimapRect().getWidth(), 0.01);
        Vector2f hidden = editor.getCanvas().toScreen(0, 0);
        click(new Vector2f(map.getLeft() + 3, map.getTop() + 3));
        frames(2);
        assertEquals(hidden.x, editor.getCanvas().toScreen(0, 0).x, 0.01, "Hidden, it doesn't pan");
    }
}
