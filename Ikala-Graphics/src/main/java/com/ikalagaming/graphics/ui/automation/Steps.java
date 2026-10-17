package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.InputTextState;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.VirtualGrid;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The steps of an automation run, built in order: {@code
 * steps.click("main-menu//about").waitForVisible("about").assertText("about//title", "About")}.
 *
 * <p>Steps find nodes by {@link Selector} and drive them with real IkGui input aimed at their
 * rectangles, so they test what a player would do: whether the node is on screen, whether something
 * covers it, and how the widget responds. A step that waits gives up after the timeout, 5 seconds
 * of IkGui time unless {@link #timeout(double)} changes it.
 *
 * <p>Two kinds of steps reach past the retained UI. Mouse steps like {@link #mouseClick(float,
 * float)} and {@link #mouseDrag(float, float, float, float)} send input to a point in the window,
 * for code that reads IkGui's input itself, like a 3D view. Item steps like {@link
 * #clickItem(String, String)} find IkGui items drawn in immediate mode by label, from the
 * rectangles IkGui recorded as they were submitted; see {@link ItemTarget} for how they are picked.
 */
public final class Steps {
    /** The default time a step waits, in milliseconds. */
    public static final long DEFAULT_TIMEOUT = 5000;

    /** How many frames the mouse may sit over a node without it being hovered. */
    private static final int HOVER_FRAMES = 10;

    /** How many frames a drag moves the mouse over. */
    private static final int DRAG_FRAMES = 10;

    /** The steps so far. */
    private final List<Step> list = new ArrayList<>();

    /** The time steps added from now on wait, in milliseconds. */
    private long timeout = DEFAULT_TIMEOUT;

    /**
     * The steps, in order.
     *
     * @return The steps.
     */
    List<Step> steps() {
        return List.copyOf(list);
    }

    /**
     * Change how long the steps added after this wait before failing.
     *
     * @param seconds Seconds of IkGui time.
     * @return These steps.
     */
    public Steps timeout(double seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("A timeout must be positive, not " + seconds);
        }
        timeout = Math.round(seconds * 1000);
        return this;
    }

    /**
     * Click a node with the left mouse button, once it is on screen. A node inside a scroll or a
     * canvas is scrolled into view first.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps click(@NonNull String selector) {
        list.add(new Click(Selector.parse(selector), timeout, 1));
        return this;
    }

    /**
     * Double click a node with the left mouse button.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps doubleClick(@NonNull String selector) {
        list.add(new Click(Selector.parse(selector), timeout, 2));
        return this;
    }

    /**
     * Move the mouse over a node, and check it is hovered.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps hover(@NonNull String selector) {
        list.add(new Click(Selector.parse(selector), timeout, 0));
        return this;
    }

    /**
     * Click a text field and replace its text.
     *
     * @param selector Picks out the field.
     * @param text The text to type.
     * @return These steps.
     */
    public Steps type(@NonNull String selector, @NonNull String text) {
        return type(selector, text, false);
    }

    /**
     * Click a text field and replace its text, then press Enter if asked.
     *
     * @param selector Picks out the field.
     * @param text The text to type.
     * @param enter Whether to press Enter afterwards.
     * @return These steps.
     */
    public Steps type(@NonNull String selector, @NonNull String text, boolean enter) {
        list.add(new Type(Selector.parse(selector), timeout, text, enter));
        return this;
    }

    /**
     * Press and release a key, holding modifiers like {@link Key#MOD_CTRL} around it.
     *
     * @param key The key.
     * @param modifiers The modifier keys to hold.
     * @return These steps.
     */
    public Steps key(@NonNull Key key, @NonNull Key... modifiers) {
        list.add(new Press(key, modifiers.clone()));
        return this;
    }

    /**
     * Scroll a node into view, if it is inside a scroll or a canvas.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps scrollTo(@NonNull String selector) {
        Selector parsed = Selector.parse(selector);
        long wait = timeout;
        list.add(
                new Wait(
                        "scrollTo(" + selector + ")",
                        wait,
                        run -> {
                            Run.Lookup lookup = run.find(parsed);
                            if (lookup.shown() != null) {
                                return null;
                            }
                            if (lookup.hidden() != null) {
                                scrollIntoView(lookup.hidden());
                            }
                            return lookup.problem();
                        }));
        return this;
    }

    /**
     * Scroll a virtual grid to one of its cells, which makes that cell's node.
     *
     * @param selector Picks out the grid.
     * @param index The cell index.
     * @return These steps.
     */
    public Steps scrollToCell(@NonNull String selector, int index) {
        Selector parsed = Selector.parse(selector);
        list.add(
                new Wait(
                        "scrollToCell(" + selector + ", " + index + ")",
                        timeout,
                        run -> {
                            Run.Lookup lookup = run.find(parsed);
                            if (lookup.shown() == null) {
                                return lookup.problem();
                            }
                            if (!(lookup.shown() instanceof VirtualGrid grid)) {
                                throw new UiAutomationException(
                                        "'"
                                                + run.pathOf(lookup.shown())
                                                + "' is a "
                                                + lookup.shown().getClass().getSimpleName()
                                                + ", not a virtual grid");
                            }
                            Node<?> cell = grid.getCell(index);
                            if (cell != null && cell.isShown()) {
                                return null;
                            }
                            if (!grid.scrollToCell(index)) {
                                throw new UiAutomationException(
                                        "the grid has no cell " + index + " of " + grid.getCount());
                            }
                            return "cell " + index + " isn't on screen";
                        }));
        return this;
    }

    /**
     * Wait until a node is on screen.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps waitForVisible(@NonNull String selector) {
        list.add(new Wait("waitForVisible(" + selector + ")", timeout, visible(selector)));
        return this;
    }

    /**
     * Wait until no node a selector picks out is on screen.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps waitForGone(@NonNull String selector) {
        list.add(new Wait("waitForGone(" + selector + ")", timeout, gone(selector)));
        return this;
    }

    /**
     * Wait until a node on screen shows some text.
     *
     * @param selector Picks out the node.
     * @param text The text.
     * @return These steps.
     */
    public Steps waitForText(@NonNull String selector, @NonNull String text) {
        list.add(
                new Wait(
                        "waitForText(" + selector + ", " + text + ")",
                        timeout,
                        hasText(selector, text)));
        return this;
    }

    /**
     * Check that a node is on screen now.
     *
     * @param selector Picks out the node.
     * @return These steps.
     */
    public Steps assertVisible(@NonNull String selector) {
        list.add(new Check("assertVisible(" + selector + ")", visible(selector)));
        return this;
    }

    /**
     * Check that a node on screen shows some text now.
     *
     * @param selector Picks out the node.
     * @param text The text.
     * @return These steps.
     */
    public Steps assertText(@NonNull String selector, @NonNull String text) {
        list.add(new Check("assertText(" + selector + ", " + text + ")", hasText(selector, text)));
        return this;
    }

    /**
     * Report whether a node is on screen now, without failing either way.
     *
     * @param selector Picks out the node.
     * @param result Told true if exactly one node it picks out is on screen.
     * @return These steps.
     */
    public Steps exists(@NonNull String selector, @NonNull Consumer<Boolean> result) {
        Selector parsed = Selector.parse(selector);
        list.add(
                new Check(
                        "exists(" + selector + ")",
                        run -> {
                            boolean found;
                            try {
                                found = run.find(parsed).shown() != null;
                            } catch (UiAutomationException ambiguous) {
                                found = false;
                            }
                            result.accept(found);
                            return null;
                        }));
        return this;
    }

    /**
     * Wait for some frames.
     *
     * @param count The number of frames.
     * @return These steps.
     */
    public Steps frames(int count) {
        list.add(new Frames(count));
        return this;
    }

    /**
     * Wait for some time.
     *
     * @param seconds Seconds of IkGui time.
     * @return These steps.
     */
    public Steps seconds(double seconds) {
        long millis = Math.round(seconds * 1000);
        list.add(
                new Step() {
                    @Override
                    public boolean advance(Run run) {
                        return run.elapsed() >= millis;
                    }

                    @Override
                    public String describe() {
                        return "seconds(" + seconds + ")";
                    }
                });
        return this;
    }

    /**
     * Move the mouse to a point in the window, in IkGui pixels from its top left corner, which is
     * where the window's own mouse input lands. Code that reads IkGui's input directly, like a 3D
     * view behind the UI, sees it as the player's mouse.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return These steps.
     */
    public Steps mouseMove(float x, float y) {
        list.add(
                new Sequence(
                        String.format(Locale.ROOT, "mouseMove(%.1f, %.1f)", x, y),
                        List.of(run -> move(run, x, y))));
        return this;
    }

    /**
     * Click the left mouse button at a point in the window, without looking for anything there.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseClick(float x, float y) {
        return mouseClick(x, y, MouseButton.LEFT);
    }

    /**
     * Click a mouse button at a point in the window, without looking for anything there.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @param button The button.
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseClick(float x, float y, @NonNull MouseButton button) {
        checkButton(button);
        list.add(
                new Sequence(
                        String.format(Locale.ROOT, "mouseClick(%.1f, %.1f, %s)", x, y, button),
                        List.of(
                                run -> move(run, x, y),
                                run -> run.io().addMouseButtonEvent(button, true),
                                run -> run.io().addMouseButtonEvent(button, false))));
        return this;
    }

    /**
     * Double click the left mouse button at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseDoubleClick(float x, float y) {
        list.add(
                new Sequence(
                        String.format(Locale.ROOT, "mouseDoubleClick(%.1f, %.1f)", x, y),
                        List.of(
                                run -> move(run, x, y),
                                run -> run.io().addMouseButtonEvent(MouseButton.LEFT, true),
                                run -> run.io().addMouseButtonEvent(MouseButton.LEFT, false),
                                run -> run.io().addMouseButtonEvent(MouseButton.LEFT, true),
                                run -> run.io().addMouseButtonEvent(MouseButton.LEFT, false))));
        return this;
    }

    /**
     * Drag with the left mouse button from one point in the window to another.
     *
     * @param fromX Where to press, in pixels from the left.
     * @param fromY Where to press, in pixels from the top.
     * @param toX Where to release, in pixels from the left.
     * @param toY Where to release, in pixels from the top.
     * @return These steps.
     * @see #mouseDrag(float, float, float, float, MouseButton)
     */
    public Steps mouseDrag(float fromX, float fromY, float toX, float toY) {
        return mouseDrag(fromX, fromY, toX, toY, MouseButton.LEFT);
    }

    /**
     * Drag with a mouse button from one point in the window to another. The mouse moves in even
     * steps over {@value #DRAG_FRAMES} frames with the button held, so code that follows the
     * mouse's movement each frame, like a camera orbit, sees a steady drag.
     *
     * @param fromX Where to press, in pixels from the left.
     * @param fromY Where to press, in pixels from the top.
     * @param toX Where to release, in pixels from the left.
     * @param toY Where to release, in pixels from the top.
     * @param button The button.
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseDrag(
            float fromX, float fromY, float toX, float toY, @NonNull MouseButton button) {
        checkButton(button);
        List<Consumer<Run>> actions = new ArrayList<>();
        actions.add(run -> move(run, fromX, fromY));
        actions.add(run -> run.io().addMouseButtonEvent(button, true));
        for (int i = 1; i <= DRAG_FRAMES; ++i) {
            float along = i / (float) DRAG_FRAMES;
            float x = fromX + (toX - fromX) * along;
            float y = fromY + (toY - fromY) * along;
            actions.add(run -> move(run, x, y));
        }
        actions.add(run -> run.io().addMouseButtonEvent(button, false));
        list.add(
                new Sequence(
                        String.format(
                                Locale.ROOT,
                                "mouseDrag(%.1f, %.1f, %.1f, %.1f, %s)",
                                fromX,
                                fromY,
                                toX,
                                toY,
                                button),
                        actions));
        return this;
    }

    /**
     * Turn the mouse wheel with the mouse at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @param amount How far, in wheel notches, positive away from the player (scrolling up).
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseWheel(float x, float y, float amount) {
        return mouseWheel(x, y, 0, amount);
    }

    /**
     * Turn the mouse wheel, or tilt it, with the mouse at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @param horizontal How far across, in notches, positive to the left as GLFW reports it.
     * @param vertical How far, in wheel notches, positive away from the player (scrolling up).
     * @return These steps.
     * @see #mouseMove(float, float)
     */
    public Steps mouseWheel(float x, float y, float horizontal, float vertical) {
        list.add(
                new Sequence(
                        String.format(
                                Locale.ROOT,
                                "mouseWheel(%.1f, %.1f, %.2f, %.2f)",
                                x,
                                y,
                                horizontal,
                                vertical),
                        List.of(
                                run -> move(run, x, y),
                                run -> run.io().addMouseWheelEvent(horizontal, vertical))));
        return this;
    }

    /**
     * Click an IkGui item drawn in immediate mode with the left mouse button, once it is on screen.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label, like {@code Roughness}.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps clickItem(@NonNull String scope, @NonNull String label) {
        return clickItem(scope, label, MouseButton.LEFT);
    }

    /**
     * Click an IkGui item drawn in immediate mode, once it is on screen.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label, like {@code Roughness}.
     * @param button The mouse button.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps clickItem(
            @NonNull String scope, @NonNull String label, @NonNull MouseButton button) {
        checkButton(button);
        list.add(new ItemClick(new ItemTarget(scope, label), timeout, 1, button, false));
        return this;
    }

    /**
     * Double click an IkGui item drawn in immediate mode with the left mouse button.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps doubleClickItem(@NonNull String scope, @NonNull String label) {
        list.add(new ItemClick(new ItemTarget(scope, label), timeout, 2, MouseButton.LEFT, false));
        return this;
    }

    /**
     * Move the mouse over an IkGui item drawn in immediate mode, and check it is hovered.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps hoverItem(@NonNull String scope, @NonNull String label) {
        list.add(new ItemClick(new ItemTarget(scope, label), timeout, 0, MouseButton.LEFT, false));
        return this;
    }

    /**
     * Type into an IkGui item drawn in immediate mode, replacing its text. The item is Ctrl
     * clicked, which turns a slider or drag into a text field, and selects all of a text field's
     * text.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @param text The text to type.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps typeItem(@NonNull String scope, @NonNull String label, @NonNull String text) {
        return typeItem(scope, label, text, false);
    }

    /**
     * Type into an IkGui item drawn in immediate mode, replacing its text, then press Enter if
     * asked. The item is Ctrl clicked, which turns a slider or drag into a text field, and selects
     * all of a text field's text.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @param text The text to type.
     * @param enter Whether to press Enter afterwards.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps typeItem(
            @NonNull String scope, @NonNull String label, @NonNull String text, boolean enter) {
        list.add(new ItemType(new ItemTarget(scope, label), timeout, text, enter));
        return this;
    }

    /**
     * Wait until an IkGui item drawn in immediate mode is on screen.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps waitForItem(@NonNull String scope, @NonNull String label) {
        ItemTarget target = new ItemTarget(scope, label);
        list.add(
                new Wait(
                        "waitForItem(" + target + ")", timeout, run -> target.find(run).problem()));
        return this;
    }

    /**
     * Check that a check box, or another item that can be checked, is checked or not. Waits for the
     * item to be on screen first.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @param checked Whether it should be checked.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps assertItemChecked(@NonNull String scope, @NonNull String label, boolean checked) {
        ItemTarget target = new ItemTarget(scope, label);
        long wait = timeout;
        list.add(
                new Step() {
                    @Override
                    public boolean advance(Run run) {
                        ItemTarget.Lookup lookup = target.find(run);
                        if (lookup.item() == null) {
                            if (run.elapsed() >= wait) {
                                throw new UiAutomationException(
                                        "timed out after "
                                                + seconds(wait)
                                                + ": "
                                                + lookup.problem());
                            }
                            return false;
                        }
                        int flags = lookup.item().statusFlags;
                        if ((flags & ItemStatusFlags.CHECKABLE) == 0) {
                            throw new UiAutomationException(
                                    ItemTarget.describe(lookup.item()) + " can't be checked");
                        }
                        boolean is = (flags & ItemStatusFlags.CHECKED) != 0;
                        if (is != checked) {
                            throw new UiAutomationException(
                                    ItemTarget.describe(lookup.item())
                                            + (is ? " is checked" : " isn't checked"));
                        }
                        return true;
                    }

                    @Override
                    public String describe() {
                        return "assertItemChecked(" + target + ", " + checked + ")";
                    }
                });
        return this;
    }

    /**
     * Report whether an IkGui item drawn in immediate mode is on screen, without failing either
     * way. Items are recorded from the first frame of a run, so this waits a frame before looking.
     *
     * @param scope Where to look, like {@code tools//materials}, {@code window=Name} or {@code *}.
     * @param label The item's label.
     * @param result Told true if exactly one item matches and is on screen.
     * @return These steps.
     * @see ItemTarget
     */
    public Steps itemExists(
            @NonNull String scope, @NonNull String label, @NonNull Consumer<Boolean> result) {
        ItemTarget target = new ItemTarget(scope, label);
        list.add(
                new Step() {
                    /** Whether a frame has been recorded. */
                    private boolean recorded;

                    @Override
                    public boolean advance(Run run) {
                        if (!recorded) {
                            recorded = true;
                            return false;
                        }
                        boolean found;
                        try {
                            found = target.find(run).item() != null;
                        } catch (UiAutomationException ambiguous) {
                            found = false;
                        }
                        result.accept(found);
                        return true;
                    }

                    @Override
                    public String describe() {
                        return "itemExists(" + target + ")";
                    }
                });
        return this;
    }

    /**
     * Send the mouse to a point in the main window. With IkGui viewports on, mouse positions are on
     * the desktop, so the point is offset by where the main window is.
     *
     * @param run The run.
     * @param x Pixels from the left of the main window.
     * @param y Pixels from the top of the main window.
     */
    private static void move(Run run, float x, float y) {
        final Viewport mainViewport = IkGui.getMainViewport();
        run.io().addMousePosEvent(mainViewport.position.x + x, mainViewport.position.y + y);
    }

    /**
     * Check a mouse button can be pressed.
     *
     * @param button The button.
     * @throws IllegalArgumentException If it is {@link MouseButton#NONE}.
     */
    private static void checkButton(MouseButton button) {
        if (button == MouseButton.NONE) {
            throw new IllegalArgumentException("There is no mouse button to press");
        }
    }

    /**
     * A condition that a node is on screen.
     *
     * @param selector Picks out the node.
     * @return The condition, giving null when met or the problem.
     */
    private static Function<Run, String> visible(String selector) {
        Selector parsed = Selector.parse(selector);
        return run -> run.find(parsed).problem();
    }

    /**
     * A condition that nothing a selector picks out is on screen.
     *
     * @param selector Picks out the node.
     * @return The condition, giving null when met or the problem.
     */
    private static Function<Run, String> gone(String selector) {
        Selector parsed = Selector.parse(selector);
        return run -> {
            for (Node<?> node : parsed.find(run.surfaces()).nodes()) {
                if (node.isShown()) {
                    return "'" + run.pathOf(node) + "' is still on screen";
                }
            }
            return null;
        };
    }

    /**
     * A condition that a node on screen shows some text.
     *
     * @param selector Picks out the node.
     * @param text The text.
     * @return The condition, giving null when met or the problem.
     */
    private static Function<Run, String> hasText(String selector, String text) {
        Selector parsed = Selector.parse(selector);
        return run -> {
            Run.Lookup lookup = run.find(parsed);
            if (lookup.shown() == null) {
                return lookup.problem();
            }
            String shown = lookup.shown().getText();
            if (!text.equals(shown)) {
                return "'"
                        + run.pathOf(lookup.shown())
                        + "' shows "
                        + (shown == null ? "no text" : "'" + shown + "'")
                        + ", not '"
                        + text
                        + "'";
            }
            return null;
        };
    }

    /**
     * Ask every scrolling ancestor of a node to bring it into view.
     *
     * @param node The node.
     * @return True if one will scroll.
     */
    private static boolean scrollIntoView(Node<?> node) {
        boolean scrolled = false;
        for (Node<?> parent = node.getParent(); parent != null; parent = parent.getParent()) {
            scrolled |= parent.scrollIntoView(node);
        }
        return scrolled;
    }

    /**
     * A timeout, for messages.
     *
     * @param millis Milliseconds.
     * @return The time in seconds, like {@code 5 s}.
     */
    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3g s", millis / 1000.0);
    }

    /**
     * Find the shown node a selector picks out, scrolling a hidden one into view.
     *
     * @param run The run.
     * @param selector Picks out the node.
     * @param timeout How long to wait, in milliseconds.
     * @return The node, or null to keep waiting.
     * @throws UiAutomationException If the wait timed out.
     */
    private static Node<?> waitShown(Run run, Selector selector, long timeout) {
        Run.Lookup lookup = run.find(selector);
        if (lookup.shown() != null) {
            return lookup.shown();
        }
        if (lookup.hidden() != null) {
            scrollIntoView(lookup.hidden());
        }
        if (run.elapsed() >= timeout) {
            throw new UiAutomationException(
                    "timed out after " + seconds(timeout) + ": " + lookup.problem());
        }
        return null;
    }

    /** Waits until a condition is met. */
    private static final class Wait implements Step {
        /** What the step does. */
        private final String description;

        /** How long to wait, in milliseconds. */
        private final long timeout;

        /** Gives null once met, or what is still wrong. */
        private final Function<Run, String> condition;

        /**
         * Create the step.
         *
         * @param description What it does.
         * @param timeout How long to wait, in milliseconds.
         * @param condition Gives null once met, or what is still wrong.
         */
        Wait(String description, long timeout, Function<Run, String> condition) {
            this.description = description;
            this.timeout = timeout;
            this.condition = condition;
        }

        @Override
        public boolean advance(Run run) {
            String problem = condition.apply(run);
            if (problem == null) {
                return true;
            }
            if (run.elapsed() >= timeout) {
                throw new UiAutomationException(
                        "timed out after " + seconds(timeout) + ": " + problem);
            }
            return false;
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /**
     * Checks a condition once.
     *
     * @param description What it checks.
     * @param condition Gives null if met, or what is wrong.
     */
    private record Check(String description, Function<Run, String> condition) implements Step {
        @Override
        public boolean advance(Run run) {
            String problem = condition.apply(run);
            if (problem != null) {
                throw new UiAutomationException(problem);
            }
            return true;
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /** Waits for some frames. */
    private static final class Frames implements Step {
        /** How many frames are left. */
        private int left;

        /** The number of frames. */
        private final int count;

        /**
         * Create the step.
         *
         * @param count The number of frames.
         */
        Frames(int count) {
            this.count = count;
            left = count;
        }

        @Override
        public boolean advance(Run run) {
            return --left <= 0;
        }

        @Override
        public String describe() {
            return "frames(" + count + ")";
        }
    }

    /** Presses and releases a key. */
    private static final class Press implements Step {
        /** The key. */
        private final Key key;

        /** Modifiers held around it. */
        private final Key[] modifiers;

        /** 0 before pressing, 1 once pressed, 2 once released. */
        private int phase;

        /**
         * Create the step.
         *
         * @param key The key.
         * @param modifiers Modifiers held around it.
         */
        Press(Key key, Key[] modifiers) {
            this.key = key;
            this.modifiers = modifiers;
        }

        @Override
        public boolean advance(Run run) {
            switch (phase++) {
                case 0 -> {
                    for (Key modifier : modifiers) {
                        run.io().addKeyEvent(modifier, true);
                    }
                    run.io().addKeyEvent(key, true);
                    return false;
                }
                case 1 -> {
                    run.io().addKeyEvent(key, false);
                    for (Key modifier : modifiers) {
                        run.io().addKeyEvent(modifier, false);
                    }
                    return false;
                }
                default -> {
                    // The release has been seen
                    return true;
                }
            }
        }

        @Override
        public String describe() {
            StringBuilder text = new StringBuilder("key(");
            for (Key modifier : modifiers) {
                text.append(modifier).append('+');
            }
            return text.append(key).append(')').toString();
        }
    }

    /** Moves the mouse onto a node and clicks it some number of times. */
    private static final class Click implements Step {
        /** Where the click is in its sequence. */
        private enum Phase {
            /** Waiting for the node to be on screen. */
            FIND,
            /** The mouse has moved; waiting for the node to be hovered. */
            HOVER,
            /** The button is down. */
            DOWN,
            /** The button is up between clicks. */
            UP,
            /** The last release was sent; waiting a frame for it to be seen. */
            SETTLE
        }

        /** Picks out the node. */
        private final Selector selector;

        /** How long to wait for the node, in milliseconds. */
        private final long timeout;

        /** The number of clicks, or 0 to only hover. */
        private final int clicks;

        /** Where the click is. */
        private Phase phase = Phase.FIND;

        /** The node being clicked, once found. */
        private Node<?> node;

        /** Where the mouse was sent, in screen pixels. */
        private float mouseX;

        /** Where the mouse was sent, in screen pixels. */
        private float mouseY;

        /** Frames the mouse has been over the node without it being hovered. */
        private int hoverFrames;

        /** Clicks finished so far. */
        private int done;

        /**
         * Create the step.
         *
         * @param selector Picks out the node.
         * @param timeout How long to wait for the node, in milliseconds.
         * @param clicks The number of clicks, or 0 to only hover.
         */
        Click(Selector selector, long timeout, int clicks) {
            this.selector = selector;
            this.timeout = timeout;
            this.clicks = clicks;
        }

        /**
         * The node clicked, once found.
         *
         * @return The node, or null.
         */
        Node<?> node() {
            return node;
        }

        @Override
        public boolean advance(Run run) {
            switch (phase) {
                case FIND -> {
                    node = waitShown(run, selector, timeout);
                    if (node == null) {
                        return false;
                    }
                    if (node.getItemID() == 0) {
                        throw new UiAutomationException(
                                "'"
                                        + run.pathOf(node)
                                        + "' is a "
                                        + node.getClass().getSimpleName()
                                        + ", which can't be clicked");
                    }
                    moveTo(run, node);
                    phase = Phase.HOVER;
                    hoverFrames = 0;
                    return false;
                }
                case HOVER -> {
                    Node<?> current = run.find(selector).shown();
                    if (current == null) {
                        // It went away or moved out of view; look again until the timeout
                        phase = Phase.FIND;
                        return false;
                    }
                    node = current;
                    if (run.gui().hoveredID == node.getItemID() && node.getItemID() != 0) {
                        if (clicks == 0) {
                            return true;
                        }
                        run.io().addMouseButtonEvent(MouseButton.LEFT, true);
                        phase = Phase.DOWN;
                        return false;
                    }
                    RectFloat rect = node.getRect();
                    if (rect.getCenterX() != mouseX || rect.getCenterY() != mouseY) {
                        // It moved, like a canvas panning to it
                        moveTo(run, node);
                    }
                    if (++hoverFrames > HOVER_FRAMES) {
                        throw new UiAutomationException(covered(run, node));
                    }
                    return false;
                }
                case DOWN -> {
                    run.io().addMouseButtonEvent(MouseButton.LEFT, false);
                    ++done;
                    phase = done < clicks ? Phase.UP : Phase.SETTLE;
                    return false;
                }
                case UP -> {
                    run.io().addMouseButtonEvent(MouseButton.LEFT, true);
                    phase = Phase.DOWN;
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }

        /**
         * Send the mouse to the middle of a node.
         *
         * @param run The run.
         * @param target The node.
         */
        private void moveTo(Run run, Node<?> target) {
            RectFloat rect = target.getRect();
            mouseX = rect.getCenterX();
            mouseY = rect.getCenterY();
            run.io().addMousePosEvent(mouseX, mouseY);
        }

        /**
         * Explain why a node isn't hovered with the mouse over it.
         *
         * @param run The run.
         * @param target The node.
         * @return The explanation.
         */
        private String covered(Run run, Node<?> target) {
            Window hovered = run.gui().windowHovered;
            String over = hovered == null ? "no window" : "window '" + hovered.name + "'";
            return String.format(
                    Locale.ROOT,
                    "the mouse at (%.0f, %.0f) is over %s, but '%s' isn't hovered%s",
                    mouseX,
                    mouseY,
                    over,
                    run.pathOf(target),
                    run.gui().hoveredID != 0 ? " (another item is)" : "");
        }

        @Override
        public String describe() {
            return switch (clicks) {
                case 0 -> "hover(" + selector + ")";
                case 1 -> "click(" + selector + ")";
                default -> "doubleClick(" + selector + ")";
            };
        }
    }

    /** Clicks a text field and replaces its text. */
    private static final class Type implements Step {
        /** Where typing is in its sequence. */
        private enum Phase {
            /** Clicking the field. */
            CLICK,
            /** Waiting for the field to take the keyboard. */
            ACTIVE,
            /** Select all is held down. */
            SELECTED,
            /** The text has been sent; waiting for the field to show it. */
            TYPED,
            /** Enter is down. */
            ENTER,
            /** Everything has been sent; waiting a frame for it to be seen. */
            SETTLE
        }

        /** Clicks the field first. */
        private final Click click;

        /** How long to wait, in milliseconds. */
        private final long timeout;

        /** The text to type. */
        private final String text;

        /** Whether to press Enter afterwards. */
        private final boolean enter;

        /** Where typing is. */
        private Phase phase = Phase.CLICK;

        /** The modifier for select all on this platform. */
        private Key shortcut;

        /**
         * Create the step.
         *
         * @param selector Picks out the field.
         * @param timeout How long to wait, in milliseconds.
         * @param text The text to type.
         * @param enter Whether to press Enter afterwards.
         */
        Type(Selector selector, long timeout, String text, boolean enter) {
            click = new Click(selector, timeout, 1);
            this.timeout = timeout;
            this.text = text;
            this.enter = enter;
        }

        @Override
        public boolean advance(Run run) {
            switch (phase) {
                case CLICK -> {
                    if (click.advance(run)) {
                        phase = Phase.ACTIVE;
                    }
                    return false;
                }
                case ACTIVE -> {
                    Node<?> field = click.node();
                    if (run.gui().activeID != field.getItemID()) {
                        if (run.elapsed() >= timeout) {
                            throw new UiAutomationException(
                                    "'" + run.pathOf(field) + "' didn't take the keyboard");
                        }
                        return false;
                    }
                    shortcut = run.io().configMacOSXBehaviors ? Key.MOD_SUPER : Key.MOD_CTRL;
                    run.io().addKeyEvent(shortcut, true);
                    run.io().addKeyEvent(Key.A, true);
                    phase = Phase.SELECTED;
                    return false;
                }
                case SELECTED -> {
                    run.io().addKeyEvent(Key.A, false);
                    run.io().addKeyEvent(shortcut, false);
                    if (text.isEmpty()) {
                        run.io().addKeyEvent(Key.DELETE, true);
                        run.io().addKeyEvent(Key.DELETE, false);
                    } else {
                        // Typing over the selection replaces it
                        run.io().addInputCharacters(text);
                    }
                    phase = Phase.TYPED;
                    return false;
                }
                case TYPED -> {
                    Node<?> field = click.node();
                    String shown = field.getText();
                    if (!Objects.equals(text, shown)) {
                        if (run.elapsed() >= timeout) {
                            throw new UiAutomationException(
                                    "'"
                                            + run.pathOf(field)
                                            + "' shows '"
                                            + shown
                                            + "' after typing '"
                                            + text
                                            + "'");
                        }
                        return false;
                    }
                    if (enter) {
                        run.io().addKeyEvent(Key.ENTER, true);
                        phase = Phase.ENTER;
                    } else {
                        return true;
                    }
                    return false;
                }
                case ENTER -> {
                    run.io().addKeyEvent(Key.ENTER, false);
                    phase = Phase.SETTLE;
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }

        @Override
        public String describe() {
            return "type(" + click.selector + ", " + text + (enter ? ", enter" : "") + ")";
        }
    }

    /** Does one thing a frame, then waits a frame for the last of it to be seen. */
    private static final class Sequence implements Step {
        /** What it does. */
        private final String description;

        /** What is left to do, one a frame. */
        private final List<Consumer<Run>> actions;

        /**
         * Create the step.
         *
         * @param description What it does.
         * @param actions What to do, one a frame.
         */
        Sequence(String description, List<Consumer<Run>> actions) {
            this.description = description;
            this.actions = new ArrayList<>(actions);
        }

        @Override
        public boolean advance(Run run) {
            if (actions.isEmpty()) {
                return true;
            }
            actions.removeFirst().accept(run);
            return false;
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /** Moves the mouse onto an immediate mode item and clicks it some number of times. */
    private static final class ItemClick implements Step {
        /** Where the click is in its sequence. */
        private enum Phase {
            /** Waiting for the item to be on screen. */
            FIND,
            /** The mouse has moved; waiting for the item to be hovered. */
            HOVER,
            /** The button is down. */
            DOWN,
            /** The button is up between clicks. */
            UP,
            /** The last release was sent; waiting a frame for it to be seen. */
            SETTLE
        }

        /** Picks out the item. */
        private final ItemTarget target;

        /** How long to wait for the item, in milliseconds. */
        private final long timeout;

        /** The number of clicks, or 0 to only hover. */
        private final int clicks;

        /** The button to click with. */
        private final MouseButton button;

        /** Whether to hold Ctrl while clicking. */
        private final boolean ctrl;

        /** Where the click is. */
        private Phase phase = Phase.FIND;

        /** The item being clicked, once found. */
        private ItemRecorder.Item item;

        /** Where the mouse was sent, in screen pixels. */
        private float mouseX;

        /** Where the mouse was sent, in screen pixels. */
        private float mouseY;

        /** Frames the mouse has been over the item without it being hovered. */
        private int hoverFrames;

        /** Clicks finished so far. */
        private int done;

        /**
         * Create the step.
         *
         * @param target Picks out the item.
         * @param timeout How long to wait for the item, in milliseconds.
         * @param clicks The number of clicks, or 0 to only hover.
         * @param button The button to click with.
         * @param ctrl Whether to hold Ctrl while clicking.
         */
        ItemClick(ItemTarget target, long timeout, int clicks, MouseButton button, boolean ctrl) {
            this.target = target;
            this.timeout = timeout;
            this.clicks = clicks;
            this.button = button;
            this.ctrl = ctrl;
        }

        /**
         * The item clicked, once found.
         *
         * @return The item, or null.
         */
        ItemRecorder.Item item() {
            return item;
        }

        @Override
        public boolean advance(Run run) {
            switch (phase) {
                case FIND -> {
                    ItemTarget.Lookup lookup = target.find(run);
                    if (lookup.item() == null) {
                        if (run.elapsed() >= timeout) {
                            throw new UiAutomationException(
                                    "timed out after "
                                            + seconds(timeout)
                                            + ": "
                                            + lookup.problem());
                        }
                        return false;
                    }
                    item = lookup.item();
                    moveTo(run);
                    phase = Phase.HOVER;
                    hoverFrames = 0;
                    return false;
                }
                case HOVER -> {
                    ItemRecorder.Item current = run.items().get(item.id);
                    if (current == null || !current.isVisible()) {
                        // It went away or out of view; look again until the timeout
                        phase = Phase.FIND;
                        return false;
                    }
                    item = current;
                    if (run.gui().hoveredID == item.id
                            || run.gui().hoveredIDPreviousFrame == item.id) {
                        if (clicks == 0) {
                            return true;
                        }
                        press(run);
                        phase = Phase.DOWN;
                        return false;
                    }
                    if (item.visible.getCenterX() != mouseX
                            || item.visible.getCenterY() != mouseY) {
                        // It moved, like a window being laid out
                        moveTo(run);
                    }
                    if (++hoverFrames > HOVER_FRAMES) {
                        throw new UiAutomationException(covered(run));
                    }
                    return false;
                }
                case DOWN -> {
                    run.io().addMouseButtonEvent(button, false);
                    if (ctrl) {
                        run.io().addKeyEvent(Key.MOD_CTRL, false);
                    }
                    ++done;
                    phase = done < clicks ? Phase.UP : Phase.SETTLE;
                    return false;
                }
                case UP -> {
                    press(run);
                    phase = Phase.DOWN;
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }

        /**
         * Press the button, and Ctrl if asked.
         *
         * @param run The run.
         */
        private void press(Run run) {
            if (ctrl) {
                run.io().addKeyEvent(Key.MOD_CTRL, true);
            }
            run.io().addMouseButtonEvent(button, true);
        }

        /**
         * Send the mouse to the middle of the visible part of the item.
         *
         * @param run The run.
         */
        private void moveTo(Run run) {
            mouseX = item.visible.getCenterX();
            mouseY = item.visible.getCenterY();
            run.io().addMousePosEvent(mouseX, mouseY);
        }

        /**
         * Explain why the item isn't hovered with the mouse over it.
         *
         * @param run The run.
         * @return The explanation.
         */
        private String covered(Run run) {
            Window hovered = run.gui().windowHovered;
            String over = hovered == null ? "no window" : "window '" + hovered.name + "'";
            return String.format(
                    Locale.ROOT,
                    "the mouse at (%.0f, %.0f) is over %s, but %s isn't hovered%s",
                    mouseX,
                    mouseY,
                    over,
                    ItemTarget.describe(item),
                    run.gui().hoveredID != 0 ? " (another item is)" : "");
        }

        @Override
        public String describe() {
            String name =
                    switch (clicks) {
                        case 0 -> "hoverItem";
                        case 1 -> "clickItem";
                        default -> "doubleClickItem";
                    };
            return name + "(" + target + (button == MouseButton.LEFT ? "" : ", " + button) + ")";
        }
    }

    /** Starts editing an immediate mode item's text and replaces it. */
    private static final class ItemType implements Step {
        /** Where typing is in its sequence. */
        private enum Phase {
            /** Clicking the item. */
            CLICK,
            /** Waiting for the item to start editing text. */
            EDITING,
            /** Select all is held down. */
            SELECTED,
            /** The text has been sent; waiting a frame for it to be seen. */
            TYPED,
            /** Enter is down. */
            ENTER,
            /** Everything has been sent; waiting a frame for it to be seen. */
            SETTLE
        }

        /** Picks out the item. */
        private final ItemTarget target;

        /** How long to wait, in milliseconds. */
        private final long timeout;

        /** The text to type. */
        private final String text;

        /** Whether to press Enter afterwards. */
        private final boolean enter;

        /**
         * Ctrl clicks the item. Sliders and drags only turn into text fields when Ctrl clicked, and
         * a text field selects all its text.
         */
        private final ItemClick click;

        /** Where typing is. */
        private Phase phase = Phase.CLICK;

        /** The modifier for select all on this platform. */
        private Key shortcut;

        /**
         * Create the step.
         *
         * @param target Picks out the item.
         * @param timeout How long to wait, in milliseconds.
         * @param text The text to type.
         * @param enter Whether to press Enter afterwards.
         */
        ItemType(ItemTarget target, long timeout, String text, boolean enter) {
            this.target = target;
            this.timeout = timeout;
            this.text = text;
            this.enter = enter;
            click = new ItemClick(target, timeout, 1, MouseButton.LEFT, true);
        }

        @Override
        public boolean advance(Run run) {
            switch (phase) {
                case CLICK -> {
                    if (click.advance(run)) {
                        phase = Phase.EDITING;
                    }
                    return false;
                }
                case EDITING -> {
                    int id = click.item().id;
                    boolean editing =
                            run.gui().activeID == id
                                    && run.gui().inputTextState != null
                                    && run.gui().inputTextState.id == id;
                    if (!editing) {
                        if (run.elapsed() >= timeout) {
                            throw new UiAutomationException(
                                    ItemTarget.describe(click.item())
                                            + " didn't start editing text");
                        }
                        return false;
                    }
                    shortcut = run.io().configMacOSXBehaviors ? Key.MOD_SUPER : Key.MOD_CTRL;
                    run.io().addKeyEvent(shortcut, true);
                    run.io().addKeyEvent(Key.A, true);
                    phase = Phase.SELECTED;
                    return false;
                }
                case SELECTED -> {
                    run.io().addKeyEvent(Key.A, false);
                    run.io().addKeyEvent(shortcut, false);
                    if (text.isEmpty()) {
                        run.io().addKeyEvent(Key.DELETE, true);
                        run.io().addKeyEvent(Key.DELETE, false);
                    } else {
                        // Typing over the selection replaces it
                        run.io().addInputCharacters(text);
                    }
                    phase = Phase.TYPED;
                    return false;
                }
                case TYPED -> {
                    InputTextState state = run.gui().inputTextState;
                    String shown =
                            state != null && state.id == click.item().id
                                    ? state.text.toString()
                                    : null;
                    if (!text.equals(shown)) {
                        if (run.elapsed() >= timeout) {
                            throw new UiAutomationException(
                                    ItemTarget.describe(click.item())
                                            + (shown == null
                                                    ? " stopped editing text"
                                                    : " shows '" + shown + "'")
                                            + " after typing '"
                                            + text
                                            + "'");
                        }
                        return false;
                    }
                    if (!enter) {
                        return true;
                    }
                    run.io().addKeyEvent(Key.ENTER, true);
                    phase = Phase.ENTER;
                    return false;
                }
                case ENTER -> {
                    run.io().addKeyEvent(Key.ENTER, false);
                    phase = Phase.SETTLE;
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }

        @Override
        public String describe() {
            return "typeItem(" + target + ", " + text + (enter ? ", enter" : "") + ")";
        }
    }
}
