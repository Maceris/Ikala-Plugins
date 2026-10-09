package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiManager;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Text input, selectable rows, scrolling and immediate areas, on a headless IkGui context. */
class UIWidgetsTest {

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

    private void frame(Runnable during) {
        IkGui.newFrame();
        manager.draw();
        during.run();
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

    private void click(RectFloat rect) {
        context.io.addMousePosEvent(rect.getCenterX(), rect.getCenterY());
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    /** A fixed size surface in the top left, holding some content. */
    private void show(String id, Column content) {
        manager.add(
                owner,
                new Surface(id)
                        .anchors(Anchors.topLeft())
                        .width(Sizing.fixed(400))
                        .height(Sizing.fixed(300))
                        .content(content));
    }

    @Test
    void textInputSubmitsOnEnter() {
        List<String> submitted = new ArrayList<>();
        TextInput input = new TextInput("path", "").onSubmit(submitted::add);
        show("form", new Column("content").add(input));
        frames(2);

        click(input.getRect());
        context.io.addInputCharacters("abc");
        frame();
        assertEquals("abc", input.text());
        assertTrue(submitted.isEmpty(), "Typing alone doesn't submit");

        press(Key.ENTER);
        assertEquals(List.of("abc"), submitted, "Enter submits once, leaving doesn't repeat it");
    }

    @Test
    void textInputSubmitsWhenLeftAfterAnEdit() {
        List<String> submitted = new ArrayList<>();
        TextInput input = new TextInput("path", "start").onSubmit(submitted::add);
        Selectable other = new Selectable("other", "Other").width(Sizing.grow());
        show("form", new Column("content").add(input, other));
        frames(2);

        click(input.getRect());
        context.io.addInputCharacters("!");
        frame();
        click(other.getRect());

        assertEquals(List.of("start!"), submitted);
    }

    @Test
    void selectableRowsClickAndShowSelection() {
        int[] clicks = new int[1];
        Selectable row = new Selectable("row", "Row").width(Sizing.grow());
        row.onClick(() -> ++clicks[0]).selected(true);
        show("list", new Column("content").add(row));
        frames(2);

        assertEquals(400 - 2 * context.style.variable.windowPadding.x, row.getRect().getWidth(), 1);
        click(row.getRect());

        assertEquals(1, clicks[0]);
        assertTrue(row.isSelected());
    }

    /** A scroll holding 50 rows, each tall enough that most are out of view. */
    private List<Selectable> scrollingList(int[] clicks) {
        Column rows = new Column("rows").align(Align.STRETCH);
        List<Selectable> created = new ArrayList<>();
        for (int i = 0; i < 50; ++i) {
            final int index = i;
            Selectable row =
                    new Selectable("row" + i, "Row " + i)
                            .height(Sizing.fixed(20))
                            .onClick(() -> clicks[0] = index);
            rows.add(row);
            created.add(row);
        }
        show("scrolling", new Column("content").add(new Scroll("scroll").content(rows)));
        return created;
    }

    @Test
    void scrollingRevealsRowsBelowTheView() {
        int[] clicked = {-1};
        List<Selectable> rows = scrollingList(clicked);
        frames(2);

        Selectable last = rows.getLast();
        float bottomBefore = last.getRect().getBottom();
        assertTrue(bottomBefore > 300, "The last row starts out of view");

        context.io.addMousePosEvent(100, 100);
        frame();
        context.io.addMouseWheelEvent(0, -200);
        frames(3);

        assertTrue(last.getRect().getBottom() < bottomBefore, "Scrolled up into view");
        click(last.getRect());
        assertEquals(49, clicked[0]);
    }

    @Test
    void gamepadNavigationScrollsToTheFocusedRow() {
        int[] clicked = {-1};
        List<Selectable> rows = scrollingList(clicked);
        frame();
        IkGui.setWindowFocus("###ui/scrolling");
        frames(2);
        click(rows.getFirst().getRect());

        for (int i = 0; i < 20; ++i) {
            press(Key.GAMEPAD_DPAD_DOWN);
        }
        press(Key.GAMEPAD_FACE_DOWN);

        assertEquals(20, clicked[0]);
        assertTrue(rows.get(20).getRect().getBottom() <= 300, "The focused row was scrolled to");
    }

    @Test
    void immediateContentRunsAtItsRectangle() {
        Vector2f cursor = new Vector2f();
        Immediate area = new Immediate("table", frame -> cursor.set(IkGui.getCursorScreenPos()));
        show("tool", new Column("content").add(area));
        frames(2);

        RectFloat rect = area.getRect();
        assertTrue(rect.getWidth() > 300, "Grows to fill the surface");
        // As in Dear ImGui, child windows without a border have no padding
        assertEquals(rect.getLeft(), cursor.x, 1);
        assertEquals(rect.getTop(), cursor.y, 1);
    }
}
