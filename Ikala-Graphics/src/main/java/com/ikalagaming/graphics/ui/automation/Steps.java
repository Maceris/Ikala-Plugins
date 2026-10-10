package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
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
 */
public final class Steps {
    /** The default time a step waits, in milliseconds. */
    public static final long DEFAULT_TIMEOUT = 5000;

    /** How many frames the mouse may sit over a node without it being hovered. */
    private static final int HOVER_FRAMES = 10;

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
}
