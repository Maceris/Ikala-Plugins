package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.MainToolbar;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;

import lombok.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A plugin's drawing that throws stops being drawn, without ending the frame or the drawing of
 * anything else.
 */
class UIDrawFailureTest {

    /** The surface whose drawing throws. */
    private static final String BAD = "bad";

    /** The surface whose drawing works. */
    private static final String GOOD = "good";

    private Context context;
    private UiManager manager;
    private GraphicsContext owner;

    /** How many times each surface's immediate content ran. */
    private final int[] runs = new int[2];

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
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

    /**
     * A surface whose immediate content counts its runs, and if asked, leaves an ID, a color and a
     * child window open and throws, as a plugin's bug would.
     *
     * @param id The surface ID.
     * @param index Where its runs are counted.
     * @param fail Whether it throws.
     * @return The surface.
     */
    private Surface surface(String id, int index, boolean fail) {
        final Column column = new Column("column");
        column.add(
                new Label("label", id),
                new Immediate(
                        "content",
                        frame -> {
                            ++runs[index];
                            if (fail) {
                                IkGui.pushID("open");
                                IkGui.pushStyleColor(ColorType.TEXT, 0xFF00FF00);
                                IkGui.beginChild("inner", 50, 50, 0);
                                throw new NullPointerException("A plugin's bug");
                            }
                            IkGui.text("Fine");
                        }));
        return new Surface(id).content(column);
    }

    /**
     * Whether a surface's window was drawn in the frame just ended.
     *
     * @param id The surface ID.
     * @return True if its window was active.
     */
    private boolean drawn(String id) {
        final Window window = IkGuiInternal.findWindowByName("###ui/" + id);
        return window != null && window.lastFrameActive == context.frameCount;
    }

    /**
     * A throwing immediate node finishes the frame, its surface is hidden after drawing once, and
     * the other surface keeps drawing.
     */
    @Test
    void throwingSurfaceIsHiddenAndOthersDraw() {
        manager.add(owner, surface(BAD, 0, true));
        manager.add(owner, surface(GOOD, 1, false));
        for (int i = 0; i < 3; ++i) {
            frame();
        }
        assertEquals(1, runs[0], "Drawn once, then no more");
        assertEquals(3, runs[1]);
        assertTrue(drawn(GOOD));
        assertFalse(drawn(BAD));
        assertEquals(1, manager.getShownSurfaces().size());
        assertEquals(GOOD, manager.getShownSurfaces().getFirst().getId());
        assertTrue(context.colorStack.isEmpty(), "The pushed color was popped");
        assertTrue(context.windowStack.isEmpty(), "The child window was ended");
    }

    /** A hidden surface draws again once its plugin shows it again. */
    @Test
    void shownAgainDrawsAgain() {
        manager.add(owner, surface(BAD, 0, true));
        frame();
        frame();
        manager.setVisible(owner, BAD, true);
        frame();
        frame();
        assertEquals(2, runs[0]);
    }

    /**
     * A plugin's window or toolbar that throws stops being drawn, and the windows around it keep
     * drawing.
     */
    @Test
    void throwingWindowAndToolbarStopDrawing() {
        final WindowManager windows = new WindowManager();
        final int[] drawsGood = {0};
        final GuiWindow bad =
                new GuiWindow("Bad", WindowFlags.NONE) {
                    @Override
                    public void draw(int width, int height) {
                        IkGui.begin(title, windowOpen, windowFlags);
                        throw new IllegalStateException("A plugin's bug");
                    }
                };
        final GuiWindow good =
                new GuiWindow("Good", WindowFlags.NONE) {
                    @Override
                    public void draw(int width, int height) {
                        ++drawsGood[0];
                        super.draw(width, height);
                    }
                };
        bad.setVisible(true);
        good.setVisible(true);
        windows.addWindow(owner, "bad", bad);
        windows.addWindow(owner, "good", good);
        windows.setToolbar(
                owner,
                new MainToolbar() {
                    @Override
                    public void draw(int width, int height) {
                        throw new IllegalStateException("A plugin's bug");
                    }

                    @Override
                    public boolean handleGuiInput(@NonNull Scene scene) {
                        return false;
                    }

                    @Override
                    public void updateValues(@NonNull Scene scene) {}
                });
        for (int i = 0; i < 3; ++i) {
            windows.drawGui(1280, 720, () -> {});
        }
        assertFalse(bad.isVisible());
        assertTrue(good.isVisible());
        assertEquals(3, drawsGood[0]);
        assertNull(windows.getToolbar());
        assertNotNull(IkGuiInternal.findWindowByName("Good"));
    }
}
