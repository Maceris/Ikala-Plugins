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
}
