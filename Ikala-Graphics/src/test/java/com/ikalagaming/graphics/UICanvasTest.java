package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Canvas;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Canvases on a headless IkGui context: placement, zoom, panning and dragging. */
class UICanvasTest {

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.NAV_ENABLE_GAMEPAD;
        context.io.backendFlags |= BackendFlags.HAS_GAMEPAD;
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
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

    private void press(Key key) {
        context.io.addKeyEvent(key, true);
        frame();
        context.io.addKeyEvent(key, false);
        frame();
    }

    /** Move the mouse, a button down, move again in steps, and the button up. */
    private void drag(MouseButton button, float fromX, float fromY, float toX, float toY) {
        context.io.addMousePosEvent(fromX, fromY);
        frame();
        context.io.addMouseButtonEvent(button, true);
        frame();
        for (int step = 1; step <= 5; ++step) {
            float t = step / 5.0f;
            context.io.addMousePosEvent(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t);
            frame();
        }
        context.io.addMouseButtonEvent(button, false);
        frame();
    }

    /** Show a canvas filling a 600 by 400 surface in the top left. */
    private Canvas show(Canvas canvas) {
        manager.add(
                owner,
                new Surface("graph")
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(600))
                        .height(Sizing.fixed(400))
                        .content(canvas));
        return canvas;
    }

    private static Button node(String id, float x, float y) {
        return new Button(id, id).width(Sizing.fixed(60)).height(Sizing.fixed(30)).position(x, y);
    }

    @Test
    void childrenArePlacedAtTheirPositions() {
        Button a = node("a", 0, 0);
        Button b = node("b", 100, 50);
        Canvas canvas = show(new Canvas("canvas").add(a, b));
        frames(3);

        Vector2f expected = canvas.toScreen(100, 50);
        assertEquals(expected.x, b.getRect().getLeft(), 0.5f);
        assertEquals(expected.y, b.getRect().getTop(), 0.5f);
        // Neighbors keep their distance
        assertEquals(100, b.getRect().getLeft() - a.getRect().getLeft(), 0.5f);
        assertEquals(50, b.getRect().getTop() - a.getRect().getTop(), 0.5f);
    }

    @Test
    void theViewStartsCenteredOnTheChildren() {
        Button a = node("a", 0, 0);
        Button b = node("b", 140, 70);
        show(new Canvas("canvas").add(a, b));
        frames(3);

        // The children cover 0 to 200 by 0 to 100, so their middle is in the middle of the view
        float middleX = (a.getRect().getLeft() + b.getRect().getRight()) / 2;
        float middleY = (a.getRect().getTop() + b.getRect().getBottom()) / 2;
        assertEquals(300, middleX, 10);
        assertEquals(200, middleY, 10);
    }

    @Test
    void zoomingScalesSizesAndFonts() {
        Button button = node("button", 0, 0);
        Label label = new Label("label", "Text").position(0, 100);
        Canvas canvas = show(new Canvas("canvas").add(button, label));
        frames(3);
        float width = button.getRect().getWidth();
        float font = label.getFontPixels();

        canvas.zoom(2);
        frames(2);

        assertEquals(width * 2, button.getRect().getWidth(), 0.5f);
        assertEquals(font * 2, label.getFontPixels(), 0.01f);
        // The UI scale is 1, so a canvas unit is the zoom in pixels
        assertEquals(2, canvas.getUnit(), 1e-3);
    }

    @Test
    void theWheelZoomsAroundTheMouse() {
        Button a = node("a", 0, 0);
        Button b = node("b", 300, 200);
        Canvas canvas = show(new Canvas("canvas").add(a, b));
        frames(3);

        // A point between the buttons, over empty space
        float mouseX = 250;
        float mouseY = 180;
        context.io.addMousePosEvent(mouseX, mouseY);
        frame();
        Vector2f origin = canvas.toScreen(0, 0);
        float canvasX = (mouseX - origin.x) / canvas.getUnit();
        float canvasY = (mouseY - origin.y) / canvas.getUnit();

        context.io.addMouseWheelEvent(0, 3);
        frames(3);

        assertTrue(canvas.getZoom() > 1.2f, "Zoomed in, to " + canvas.getZoom());
        Vector2f after = canvas.toScreen(canvasX, canvasY);
        assertEquals(mouseX, after.x, 1.0f);
        assertEquals(mouseY, after.y, 1.0f);
    }

    @Test
    void zoomStaysInItsRange() {
        Canvas canvas = show(new Canvas("canvas").zoomRange(0.5f, 2).add(node("a", 0, 0)));
        frames(3);
        context.io.addMousePosEvent(300, 200);
        frame();
        for (int i = 0; i < 20; ++i) {
            context.io.addMouseWheelEvent(0, 5);
            frame();
        }
        assertEquals(2, canvas.getZoom(), 1e-4);
        assertEquals(0.5f, canvas.zoom(0.1f).getZoom(), 1e-4);
    }

    @Test
    void middleDraggingPans() {
        Button a = node("a", 0, 0);
        show(new Canvas("canvas").add(a, node("b", 200, 100)));
        frames(3);
        RectFloat before = a.getRect();

        drag(MouseButton.MIDDLE, 300, 300, 260, 250);
        frames(2);

        assertEquals(before.getLeft() - 40, a.getRect().getLeft(), 1.0f);
        assertEquals(before.getTop() - 50, a.getRect().getTop(), 1.0f);
    }

    @Test
    void leftDraggingEmptySpacePans() {
        Button a = node("a", 0, 0);
        show(new Canvas("canvas").add(a, node("b", 200, 100)));
        frames(3);
        RectFloat before = a.getRect();

        drag(MouseButton.LEFT, 500, 350, 530, 370);
        frames(2);

        assertEquals(before.getLeft() + 30, a.getRect().getLeft(), 1.0f);
        assertEquals(before.getTop() + 20, a.getRect().getTop(), 1.0f);
    }

    @Test
    void draggingAChildMovesItInsteadOfClickingIt() {
        int[] clicks = new int[1];
        Button a = node("a", 0, 0);
        a.onClick(() -> ++clicks[0]);
        List<float[]> moves = new ArrayList<>();
        List<Node<?>> moved = new ArrayList<>();
        Canvas canvas =
                show(
                        new Canvas("canvas")
                                .draggable(true)
                                .onMoved(
                                        (node, x, y) -> {
                                            moved.add(node);
                                            moves.add(new float[] {x, y});
                                        })
                                .add(a, node("b", 200, 100)));
        frames(3);
        RectFloat start = a.getRect();

        drag(
                MouseButton.LEFT,
                start.getCenterX(),
                start.getCenterY(),
                start.getCenterX() + 50,
                start.getCenterY() + 20);
        frames(2);

        assertEquals(1, moves.size(), "One move, reported once on release");
        assertSame(a, moved.getFirst());
        assertEquals(50 / canvas.getUnit(), moves.getFirst()[0], 1.0f);
        assertEquals(20 / canvas.getUnit(), moves.getFirst()[1], 1.0f);
        assertEquals(moves.getFirst()[0], a.getPositionX(), 1e-4);
        assertEquals(0, clicks[0], "Dragging isn't a click");
        assertNull(canvas.getDragging());
    }

    @Test
    void childrenCanStillBeClickedWhenDraggable() {
        int[] clicks = new int[1];
        Button a = node("a", 0, 0);
        a.onClick(() -> ++clicks[0]);
        show(new Canvas("canvas").draggable(true).add(a, node("b", 200, 100)));
        frames(3);

        RectFloat rect = a.getRect();
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(2);

        assertEquals(1, clicks[0]);
        assertEquals(0, a.getPositionX(), 1e-4, "A click doesn't move it");
    }

    @Test
    void gamepadNavigationPansToTheFocusedChild() {
        int[] clicked = {-1};
        Button near = node("near", 0, 0);
        Button far = node("far", 1500, 0);
        near.onClick(() -> clicked[0] = 0);
        far.onClick(() -> clicked[0] = 1);
        Canvas canvas = show(new Canvas("canvas").add(near, far));
        canvas.centerOn(30, 15);
        frame();
        IkGui.setWindowFocus("###ui/graph");
        frames(3);
        assertTrue(far.getRect().getLeft() > 600, "The far child starts out of view");

        RectFloat nearRect = near.getRect();
        context.io.addMousePosEvent(nearRect.getCenterX(), nearRect.getCenterY());
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
        assertEquals(0, clicked[0]);

        press(Key.GAMEPAD_DPAD_RIGHT);
        frames(3);
        press(Key.GAMEPAD_FACE_DOWN);

        assertEquals(1, clicked[0]);
        assertTrue(
                far.getRect().getRight() <= 600,
                "Panned to the focused child, which is at "
                        + far.getRect().getLeft()
                        + " to "
                        + far.getRect().getRight());
    }
}
