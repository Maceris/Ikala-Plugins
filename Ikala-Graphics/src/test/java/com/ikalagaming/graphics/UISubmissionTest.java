package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.CustomItem;
import com.ikalagaming.graphics.ui.ItemState;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;
import com.ikalagaming.graphics.ui.style.Theme;

import lombok.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Retained UI submitted to a headless IkGui context. */
class UISubmissionTest {

    private static final class Slot extends CustomItem<Slot> {
        ItemState lastState;
        int lastBackground;

        Slot(String id) {
            super(id);
            width(Sizing.fixed(40));
            height(Sizing.fixed(40));
        }

        @Override
        protected void draw(
                @NonNull DrawList drawList, @NonNull RectFloat bounds, @NonNull ItemState state) {
            lastState = state;
            lastBackground = color(StyleKey.BACKGROUND, state, 0);
            drawList.addRectFilled(
                    bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom(), -1);
        }
    }

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;
    private final int[] presses = new int[3];
    private Button first;
    private Button second;
    private Slot slot;

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

    private void frame(Runnable extra) {
        IkGui.newFrame();
        manager.draw();
        extra.run();
        IkGui.render();
        manager.dispatchEvents();
    }

    private void frame() {
        frame(() -> {});
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

    private void click(float x, float y) {
        context.io.addMousePosEvent(x, y);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    /** A menu of two buttons and a custom slot, with the first button taking focus. */
    private Surface menu(String id) {
        first = new Button("first", "First").autofocus(true).onClick(() -> ++presses[0]);
        second = new Button("second", "Second").onClick(() -> ++presses[1]);
        slot = new Slot("slot").onActivate(() -> ++presses[2]);
        Column column = new Column("buttons").gap(8);
        column.add(first, second, slot);
        return new Surface(id).content(column);
    }

    private void show(Surface surface) {
        manager.add(owner, surface);
    }

    @Test
    void surfacesArePlacedByTheirAnchors() {
        show(
                new Surface("placed")
                        .anchors(Anchors.topLeft().offset(Length.u(10), Length.u(20)))
                        .width(Sizing.fixed(200))
                        .height(Sizing.fixed(100)));
        frames(2);

        var window = IkGuiInternal.findWindowByName("###ui/placed");
        assertNotNull(window);
        assertEquals(10, window.position.x, 1e-3);
        assertEquals(20, window.position.y, 1e-3);
        assertEquals(200, window.size.x, 1e-3);
        assertEquals(100, window.size.y, 1e-3);
    }

    @Test
    void centeredSurfacesFitTheirContent() {
        show(menu("menu"));
        frames(2);

        var window = IkGuiInternal.findWindowByName("###ui/menu");
        assertEquals(640, window.position.x + window.size.x / 2, 1, "Centered horizontally");
        assertEquals(360, window.position.y + window.size.y / 2, 1, "Centered vertically");
        RectFloat content = second.getRect();
        assertTrue(content.getLeft() >= window.position.x);
        assertTrue(content.getBottom() <= window.position.y + window.size.y);
    }

    @Test
    void clickingAButtonRunsItsAction() {
        show(menu("menu"));
        frames(2);

        RectFloat rect = second.getRect();
        click(rect.getCenterX(), rect.getCenterY());

        assertEquals(0, presses[0]);
        assertEquals(1, presses[1]);
    }

    @Test
    void customItemsAreClickable() {
        show(menu("menu"));
        frames(2);

        RectFloat rect = slot.getRect();
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frame();
        assertTrue(slot.lastState.hovered());
        click(rect.getCenterX(), rect.getCenterY());

        assertEquals(1, presses[2]);
    }

    @Test
    void gamepadMovesFocusAndActivates() {
        show(menu("menu"));
        frame();
        IkGui.setWindowFocus("###ui/menu");
        frames(2);

        press(Key.GAMEPAD_DPAD_DOWN);
        press(Key.GAMEPAD_FACE_DOWN);
        assertEquals(0, presses[0], "The first button had focus, so down moved off it");
        assertEquals(1, presses[1]);

        press(Key.GAMEPAD_DPAD_DOWN);
        assertTrue(slot.lastState.focused());
        press(Key.GAMEPAD_FACE_DOWN);
        assertEquals(1, presses[2], "Custom items activate like buttons");
    }

    @Test
    void keyboardTabAndSpaceActivate() {
        show(menu("menu"));
        frame();
        IkGui.setWindowFocus("###ui/menu");
        frames(2);

        // As in Dear ImGui, the first Tab only shows the nav cursor on the focused item
        press(Key.TAB);
        press(Key.SPACE);
        assertEquals(1, presses[0]);

        press(Key.TAB);
        press(Key.SPACE);
        assertEquals(1, presses[1], "Tab moved on to the second button");
    }

    @Test
    void idsSurviveRebuildingTheTree() {
        show(menu("menu"));
        frame();
        IkGui.setWindowFocus("###ui/menu");
        frames(2);
        press(Key.GAMEPAD_DPAD_DOWN);
        int focused = context.navID;

        // A new tree with the same IDs keeps the focused item
        show(menu("menu"));
        frames(2);
        assertEquals(focused, context.navID);
        press(Key.GAMEPAD_FACE_DOWN);
        assertEquals(1, presses[1]);
    }

    private int displayIndex(String name) {
        return context.windowDisplayOrder.indexOf(IkGuiInternal.findWindowByName(name));
    }

    @Test
    void backgroundSurfacesStayBehindToolWindows() {
        show(
                new Surface("backdrop")
                        .anchors(Anchors.fill())
                        .layer(Layer.BACKGROUND)
                        .content(new Column("content")));
        Runnable tool =
                () -> {
                    IkGui.setNextWindowPos(100, 100);
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Tool");
                    IkGui.end();
                };
        frame(tool);
        frame(tool);

        // Clicking the backdrop focuses it, but it stays at the back
        context.io.addMousePosEvent(900, 600);
        frame(tool);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(tool);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(tool);

        assertTrue(displayIndex("###ui/backdrop") < displayIndex("Tool"));
    }

    @Test
    void overlaySurfacesStayInFrontOfFocusedWindows() {
        show(
                new Surface("toast")
                        .anchors(Anchors.topLeft())
                        .layer(Layer.OVERLAY)
                        .width(Sizing.fixed(300))
                        .height(Sizing.fixed(300)));
        Runnable tool =
                () -> {
                    IkGui.setNextWindowPos(100, 100);
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Tool");
                    IkGui.end();
                };
        frame(tool);
        IkGui.setWindowFocus("Tool");
        frame(tool);
        frame(tool);

        assertTrue(displayIndex("###ui/toast") > displayIndex("Tool"));
    }

    @Test
    void labelsAreMeasuredAtTheFontSizeTheyAreDrawnWith() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fonts/NotoSans.ttf")) {
            assertNotNull(stream);
            assertTrue(context.io.fonts.loadFont("NotoSans", stream.readAllBytes()));
        }
        IkGui.setFont("NotoSans", 12);
        Label label = new Label("label", "top right");
        show(new Surface("text").content(new Column("content").add(label)));
        frames(2);

        float[] drawn = new float[1];
        frame(() -> drawn[0] = IkGui.calcTextSize("top right").x);

        assertTrue(drawn[0] > 0);
        assertEquals(drawn[0], label.getRect().getWidth(), 0.5f);
    }

    @Test
    void hidingAFocusedSurfaceReleasesTheKeyboard() {
        Surface surface = menu("menu");
        show(surface);
        frame();
        IkGui.setWindowFocus("###ui/menu");
        frames(2);
        assertTrue(context.io.wantCaptureKeyboard, "Keyboard nav captures keys while focused");

        manager.setVisible(owner, "menu", false);
        frames(3);

        assertEquals(false, context.io.wantCaptureKeyboard, "Camera keys work again");
    }

    @Test
    void themesStyleNodesAndBalanceTheirPushes() {
        Theme theme =
                Theme.builder("test")
                        .type(
                                "custom",
                                Style.builder()
                                        .color(StyleKey.BACKGROUND, 0x112233FF)
                                        .state(
                                                StyleState.HOVERED,
                                                Style.builder()
                                                        .color(StyleKey.BACKGROUND, 0x445566FF)
                                                        .build())
                                        .build())
                        .type(
                                "button",
                                Style.builder().color(StyleKey.BACKGROUND, 0xFF0000FF).build())
                        .build();
        manager.useTheme(owner, theme);
        show(menu("menu"));
        frames(2);
        assertEquals(0x112233FF, slot.lastBackground);
        assertTrue(context.colorStack.isEmpty(), "Every pushed color was popped");

        RectFloat rect = slot.getRect();
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frames(2);

        assertEquals(0x445566FF, slot.lastBackground, "The hovered state's color");
        assertTrue(context.colorStack.isEmpty());
    }

    @Test
    void unloadingDropsThatPluginsTheme() {
        Theme theme = Theme.builder("test").build();
        manager.useTheme(owner, theme);
        show(menu("menu"));
        frame();
        assertEquals(theme, manager.getActiveTheme().getTheme());

        manager.removeAllOwnedBy(owner);
        frames(2);

        assertEquals(manager.getDefaultTheme(), manager.getActiveTheme().getTheme());
    }

    @Test
    void unloadingRemovesOnlyThatPluginsSurfaces() {
        GraphicsContext other = new GraphicsContext("Other-Plugin", null);
        show(new Surface("mine"));
        manager.add(other, new Surface("theirs"));

        manager.removeAllOwnedBy(owner);
        assertEquals(2, manager.surfaceCount(), "Removal waits for the next frame");
        frame();

        assertEquals(1, manager.surfaceCount());
        assertNotNull(manager.get("theirs"));
    }

    @Test
    void postedChangesRunInOrderOnTheNextFrame() {
        List<Integer> order = new ArrayList<>();
        manager.post(() -> order.add(1));
        manager.post(() -> order.add(2));
        assertTrue(order.isEmpty());

        frame();

        assertEquals(List.of(1, 2), order);
    }

    @Test
    void closedContextsCantShowSurfaces() {
        owner.close();
        show(new Surface("late"));
        assertEquals(0, manager.surfaceCount());
    }
}
