package com.ikalagaming.graphics.input;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecEvent;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * The Controls screen, where players rebind every registered {@link InputAction}. Any plugin's menu
 * can open it; it is owned by that plugin's graphics. Clicking a binding waits for the next key or
 * mouse button, with the Ctrl, Shift and Alt held at the time; pressing a modifier and letting go
 * binds the modifier itself, and Escape cancels. While it is open every action reads as up. Main
 * thread only.
 */
public final class ControlsScreen {

    /** The screen's surface. */
    public static final String SURFACE_ID = "graphics/controls";

    /** The spec, in the Graphics plugin's resources. */
    private static final String SPEC = "/ui/controls.yml";

    /**
     * A line in the list: a category heading or an action.
     *
     * @param key Finds the row again across refreshes.
     * @param header Whether this is a category heading.
     * @param action Whether this is an action.
     * @param id The action's ID, or blank for a heading.
     * @param label The category or the action's name.
     * @param binding The binding as shown.
     * @param changed Whether it isn't on its default.
     * @param conflict Whether another action has the same binding.
     */
    public record Row(
            @NonNull String key,
            boolean header,
            boolean action,
            @NonNull String id,
            @NonNull String label,
            @NonNull String binding,
            boolean changed,
            boolean conflict) {}

    /** The modifier keys themselves, which bind on release when pressed alone. */
    private static final List<Key> MODIFIER_KEYS =
            List.of(
                    Key.LEFT_CTRL,
                    Key.RIGHT_CTRL,
                    Key.LEFT_SHIFT,
                    Key.RIGHT_SHIFT,
                    Key.LEFT_ALT,
                    Key.RIGHT_ALT,
                    Key.LEFT_SUPER,
                    Key.RIGHT_SUPER);

    /** The graphics that own the surface. */
    private final GraphicsContext graphics;

    /** Run when the player presses Done. */
    private final Runnable onDone;

    /** The rows shown. */
    private final ObservableList<Row> rows = new ObservableList<>();

    /** What {@link #rows} holds. */
    private List<Row> current = List.of();

    /** The line under the title. */
    private final Observable<String> hint = Observable.of("");

    /** Watches the keys while waiting for one. */
    private final Runnable captureTask = this::capture;

    /** The open screen, or null. */
    private SpecInstance instance;

    /** The action waiting for a key, or null. */
    private String waiting;

    /** Whether the frame the wait began on has passed, so the click that began it is ignored. */
    private boolean armed;

    /** A modifier key pressed while waiting with nothing after it yet, or null. */
    private Key pendingModifier;

    /** The GUI frame a wait last ended on, so the key that ended it doesn't click a button. */
    private int endedFrame = -1;

    /**
     * A Controls screen.
     *
     * @param graphics The graphics of the plugin opening it.
     * @param onDone Run after the player presses Done and the screen closes, to show the menu they
     *     came from.
     */
    public ControlsScreen(@NonNull GraphicsContext graphics, @NonNull Runnable onDone) {
        this.graphics = graphics;
        this.onDone = onDone;
    }

    /** Show the screen, with the actions registered now. */
    public void open() {
        if (instance != null && !instance.isClosed()) {
            return;
        }
        refresh();
        hint.set(text("CONTROLS_HINT"));
        final ResourceBundle bundle = GraphicsPlugin.getResourceBundle();
        final SpecBindings bindings =
                new SpecBindings()
                        .list("rows", rows)
                        .value("hint", hint)
                        .handler("rebind", this::rebind)
                        .handler("reset", this::reset)
                        .handler("reset-all", this::resetAll)
                        .handler("done", this::done);
        if (bundle != null) {
            bindings.bundle(bundle);
        }
        instance =
                graphics.ui().open(SpecLoader.loadResource(ControlsScreen.class, SPEC), bindings);
        graphics.addFrameTask(captureTask);
    }

    /**
     * Whether the screen is showing.
     *
     * @return True if open.
     */
    public boolean isOpen() {
        return instance != null && !instance.isClosed();
    }

    /** Close the screen without running the Done callback. */
    public void close() {
        stopWaiting();
        graphics.removeFrameTask(captureTask);
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    /**
     * The rows shown, for tests.
     *
     * @return The rows.
     */
    List<Row> rows() {
        return current;
    }

    /** Rebuild the rows from the registered actions. */
    void refresh() {
        final Controls controls = GraphicsManager.getControls();
        final List<Row> list = new ArrayList<>();
        String category = null;
        for (InputAction action : controls.actions()) {
            if (!action.category().equals(category)) {
                category = action.category();
                list.add(
                        new Row(
                                "category/" + category,
                                true,
                                false,
                                "",
                                category,
                                "",
                                false,
                                false));
            }
            final String id = action.id();
            final String binding;
            if (id.equals(waiting)) {
                binding = text("CONTROLS_PRESS_KEY");
            } else if (controls.binding(id).isNone()) {
                binding = text("CONTROLS_UNBOUND");
            } else {
                binding = controls.binding(id).displayName();
            }
            list.add(
                    new Row(
                            "action/" + id,
                            false,
                            true,
                            id,
                            action.name(),
                            binding,
                            controls.isChanged(id),
                            !controls.conflicts(id).isEmpty()));
        }
        current = List.copyOf(list);
        rows.set(list);
    }

    /**
     * Start waiting for a key for the row whose binding was clicked.
     *
     * @param event The click.
     */
    private void rebind(@NonNull SpecEvent event) {
        if (!(event.item() instanceof Row row) || !row.action()) {
            return;
        }
        if (IkGui.getContext() != null && IkGui.getFrameCount() - endedFrame <= 1) {
            // The key that just bound an action also activated the focused button
            return;
        }
        waiting = row.id();
        armed = false;
        pendingModifier = null;
        GraphicsManager.getControls().setSuspended(true);
        hint.set(SafeResourceLoader.getStringFormatted("CONTROLS_WAITING", bundle(), row.label()));
        refresh();
    }

    /**
     * Put a row's action back on its default.
     *
     * @param event The click.
     */
    private void reset(@NonNull SpecEvent event) {
        if (event.item() instanceof Row row && row.action()) {
            GraphicsManager.getControls().reset(row.id());
            refresh();
        }
    }

    /** Put every action back on its default. */
    private void resetAll() {
        stopWaiting();
        GraphicsManager.getControls().resetAll();
        refresh();
    }

    /** Close and go back. */
    private void done() {
        close();
        onDone.run();
    }

    /** Watch for the key to bind, once a frame while waiting. */
    private void capture() {
        if (waiting == null || IkGui.getContext() == null) {
            return;
        }
        if (!armed) {
            armed = true;
            return;
        }
        if (IkGui.isKeyPressed(Key.ESCAPE, false)) {
            stopWaiting();
            return;
        }
        for (Key modifier : MODIFIER_KEYS) {
            if (IkGui.isKeyPressed(modifier, false)) {
                pendingModifier = modifier;
            }
        }
        for (int i = 1; i < Key.COUNT; ++i) {
            final Key key = Key.byOrdinal(i);
            if (bindable(key) && !MODIFIER_KEYS.contains(key) && IkGui.isKeyPressed(key, false)) {
                bind(
                        new KeyBinding(
                                key,
                                IkGui.isKeyDown(Key.MOD_CTRL),
                                IkGui.isKeyDown(Key.MOD_SHIFT),
                                IkGui.isKeyDown(Key.MOD_ALT)));
                return;
            }
        }
        if (pendingModifier != null && IkGui.isKeyReleased(pendingModifier)) {
            bind(KeyBinding.of(pendingModifier));
        }
    }

    /**
     * Whether a key can be bound by pressing it here.
     *
     * @param key The key.
     * @return True for keyboard keys and mouse buttons, not the wheel.
     */
    private static boolean bindable(@NonNull Key key) {
        return (key.isKeyboardKey() || key.isMouseKey())
                && key != Key.MOUSE_WHEEL_X
                && key != Key.MOUSE_WHEEL_Y
                && key != Key.ESCAPE;
    }

    /**
     * Bind the waiting action and stop waiting.
     *
     * @param binding The binding.
     */
    private void bind(@NonNull KeyBinding binding) {
        final String id = Objects.requireNonNull(waiting);
        GraphicsManager.getControls().rebind(id, binding);
        stopWaiting();
    }

    /** Stop waiting for a key, if waiting. */
    private void stopWaiting() {
        if (waiting == null) {
            return;
        }
        waiting = null;
        pendingModifier = null;
        GraphicsManager.getControls().setSuspended(false);
        if (IkGui.getContext() != null) {
            endedFrame = IkGui.getFrameCount();
        }
        hint.set(text("CONTROLS_HINT"));
        refresh();
    }

    /**
     * A string from the Graphics plugin's bundle.
     *
     * @param key The key.
     * @return The string.
     */
    private static String text(@NonNull String key) {
        return SafeResourceLoader.getString(key, bundle());
    }

    /**
     * The Graphics plugin's strings.
     *
     * @return The bundle.
     */
    private static ResourceBundle bundle() {
        return GraphicsPlugin.getResourceBundle();
    }
}
