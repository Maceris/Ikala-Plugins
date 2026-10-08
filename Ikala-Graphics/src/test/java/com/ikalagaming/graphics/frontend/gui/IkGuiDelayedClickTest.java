package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for telling single and double clicks apart, and the user guide. */
class IkGuiDelayedClickTest {
    /** How long each faked frame takes, in milliseconds. */
    private static final long FRAME_TIME = 50;

    private Context context;

    /** The rectangle of the item, captured while drawing. */
    private final Vector2f itemMin = new Vector2f();

    private final Vector2f itemMax = new Vector2f();

    /** The click count returned for the item, for each frame. */
    private final List<Integer> counts = new ArrayList<>();

    /** Whether the selectable is selected. */
    private boolean selected;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /** Run a frame that takes FRAME_TIME milliseconds. */
    private void frame(Runnable ui) {
        // Fake the elapsed time since the last frame
        context.frameStartTime -= FRAME_TIME;
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private static void beginHost() {
        IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(300, 300, Condition.FIRST_USE_EVER);
        IkGui.begin("Host", null, WindowFlags.NONE);
    }

    /** A window with one item, recording the click count of the item every frame. */
    private Runnable itemUI(Runnable item) {
        return () -> {
            beginHost();
            item.run();
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            counts.add(IkGui.getItemClickedCountWithSingleClickDelay(MouseButton.LEFT));
            IkGui.end();
        };
    }

    private void press(Runnable ui) {
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
    }

    private void hoverItem(Runnable ui) {
        context.io.addMousePosEvent((itemMin.x + itemMax.x) * 0.5f, (itemMin.y + itemMax.y) * 0.5f);
        frame(ui);
    }

    /** The frames, counted from the release, where the count was not 0. */
    private List<String> nonZeroCounts(int releaseFrame) {
        final List<String> result = new ArrayList<>();
        for (int i = releaseFrame; i < counts.size(); ++i) {
            if (counts.get(i) != 0) {
                result.add((i - releaseFrame) + ":" + counts.get(i));
            }
        }
        return result;
    }

    @Test
    void testSingleClickIsDelayed() {
        final Runnable ui = itemUI(() -> IkGui.button("Button"));
        frames(2, ui);
        hoverItem(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        final int releaseFrame = counts.size() - 1;
        frames(20, ui);

        // Reported once, on the first frame at least mouseSingleClickDelay after the release
        final long delayFrames = context.io.mouseSingleClickDelay / FRAME_TIME;
        assertEquals(List.of(delayFrames + ":1"), nonZeroCounts(releaseFrame));
    }

    @Test
    void testDoubleClickIsImmediate() {
        final Runnable ui = itemUI(() -> IkGui.button("Button"));
        frames(2, ui);
        hoverItem(ui);
        press(ui);
        final int firstRelease = counts.size() - 1;
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        assertEquals(2, counts.getLast(), "The second click is reported right away");
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(20, ui);
        // And there's no delayed single click afterward
        assertEquals(List.of("1:2"), nonZeroCounts(firstRelease));
    }

    @Test
    void testCustomDelay() {
        final List<Integer> custom = new ArrayList<>();
        final Runnable ui =
                () -> {
                    beginHost();
                    IkGui.button("Button");
                    IkGui.getItemRectMin(itemMin);
                    IkGui.getItemRectMax(itemMax);
                    custom.add(
                            IkGui.getItemClickedCountWithSingleClickDelay(MouseButton.LEFT, 1000));
                    IkGui.end();
                };
        frames(2, ui);
        hoverItem(ui);
        press(ui);
        final int releaseFrame = custom.size() - 1;
        frames(30, ui);
        assertEquals(1000 / FRAME_TIME, custom.indexOf(1) - releaseFrame);
        assertEquals(1, custom.stream().filter(count -> count == 1).count());
    }

    @Test
    void testItemWithoutAnID() {
        // Text has no ID, so a throwaway one is made from its rectangle
        final Runnable ui = itemUI(() -> IkGui.text("Some text"));
        frames(2, ui);
        hoverItem(ui);
        press(ui);
        final int releaseFrame = counts.size() - 1;
        frames(20, ui);
        assertEquals(1, nonZeroCounts(releaseFrame).size());
        assertTrue(nonZeroCounts(releaseFrame).getFirst().endsWith(":1"));
    }

    @Test
    void testSelectionAtTheTimeOfTheClick() {
        final Runnable ui =
                itemUI(
                        () -> {
                            if (IkGui.selectable("Item", selected)) {
                                selected = !selected;
                            }
                        });
        frames(2, ui);
        hoverItem(ui);
        press(ui);
        assertTrue(selected);
        // It wasn't selected yet when it was clicked
        assertFalse(context.lastActiveIDWasSelected);

        frames(20, ui);
        press(ui);
        assertFalse(selected);
        // This time it was already selected, and was the only selection
        assertTrue(context.lastActiveIDWasSelected);
        assertTrue(context.lastActiveIDWasSoleSelected);
    }

    @Test
    void testNoDelayedReleaseWithoutARelease() {
        final List<Boolean> released = new ArrayList<>();
        frames(30, () -> released.add(IkGui.isMouseReleasedWithDelay(MouseButton.LEFT)));
        assertFalse(released.contains(true));
    }

    @Test
    void testDelayMustBeLongerThanDoubleClickTime() {
        context.io.mouseSingleClickDelay = context.io.mouseDoubleClickTime;
        frame(() -> {});
        assertTrue(context.io.mouseSingleClickDelay > context.io.mouseDoubleClickTime);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("io.mouseSingleClickDelay")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testUserGuide() {
        frames(
                2,
                () -> {
                    beginHost();
                    IkGui.showUserGuide();
                    IkGui.end();
                });
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }
}
