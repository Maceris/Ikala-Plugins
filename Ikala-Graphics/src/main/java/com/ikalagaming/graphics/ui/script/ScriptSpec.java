package com.ikalagaming.graphics.ui.script;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecInstance;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A spec a script opened, as the script sees it. A handle, never a node: the script sets values by
 * name and closes it, and once it is closed or its plugin unloads, every method does nothing. Safe
 * to call from the script thread; changes reach the UI on the next frame.
 */
public final class ScriptSpec {
    /** The values the spec's {@code {name}} bindings show, made on first use. */
    private final Map<String, Observable<Object>> values = new ConcurrentHashMap<>();

    /** The lists the spec's repeats show, made on first use. */
    private final Map<String, ObservableList<Object>> lists = new ConcurrentHashMap<>();

    /** The plugin that owns the spec. */
    private final GraphicsContext owner;

    /** The manager it is shown through. */
    private final UiManager manager;

    /** The open spec, set once it is opened. */
    private volatile SpecInstance instance;

    /**
     * Create a handle, before the spec is opened.
     *
     * @param owner The plugin that owns the spec.
     * @param manager The manager it is shown through.
     */
    ScriptSpec(@NonNull GraphicsContext owner, @NonNull UiManager manager) {
        this.owner = owner;
        this.manager = manager;
    }

    /**
     * The observable for a binding name, made the first time it is asked for.
     *
     * @param name The binding name.
     * @return The observable.
     */
    Observable<Object> observable(@NonNull String name) {
        return values.computeIfAbsent(name, ignored -> new Observable<>(null));
    }

    /**
     * The list for a repeat, made empty the first time it is asked for.
     *
     * @param name The list name.
     * @return The list.
     */
    ObservableList<Object> list(@NonNull String name) {
        return lists.computeIfAbsent(name, ignored -> new ObservableList<>());
    }

    /**
     * Connect the handle to its spec once it is open.
     *
     * @param opened The open spec.
     */
    void attach(@NonNull SpecInstance opened) {
        instance = opened;
    }

    /**
     * Set a value the spec shows through a {@code {name}} binding.
     *
     * @param name The binding name.
     * @param value The value.
     */
    public void set(@NonNull String name, Object value) {
        if (isOpen()) {
            observable(name).set(value);
        }
    }

    /**
     * Set the items a repeat in the spec shows, like {@code List.of("a", "b")}.
     *
     * @param name The list name the repeat uses.
     * @param items The items, which must be a list.
     * @throws IllegalArgumentException If the items aren't a list.
     */
    public void setList(@NonNull String name, @NonNull Object items) {
        if (!(items instanceof List<?> given)) {
            throw new IllegalArgumentException("Expected a list for " + name + ", got " + items);
        }
        if (isOpen()) {
            list(name).set(new ArrayList<>(given));
        }
    }

    /**
     * Read a value set with {@link #set(String, Object)}.
     *
     * @param name The binding name.
     * @return The value, or null if it was never set or the spec is closed.
     */
    public Object get(@NonNull String name) {
        if (!isOpen()) {
            return null;
        }
        final Observable<Object> value = values.get(name);
        return value == null ? null : value.get();
    }

    /**
     * Show or hide the spec's surface, without closing it.
     *
     * @param visible Whether to draw it.
     */
    public void setVisible(boolean visible) {
        final SpecInstance current = instance;
        if (current == null || current.isClosed()) {
            return;
        }
        final String id = current.getSpec().surface().id();
        manager.post(() -> manager.setVisible(owner, id, visible));
    }

    /**
     * Whether the spec is still open.
     *
     * @return False once it is closed, or its plugin unloaded.
     */
    public boolean isOpen() {
        final SpecInstance current = instance;
        return current != null && !current.isClosed();
    }

    /** Close the spec, removing its surface. Closing again does nothing. */
    public void close() {
        final SpecInstance current = instance;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public String toString() {
        final SpecInstance current = instance;
        return "ScriptSpec["
                + (current == null ? "opening" : current.getSpec().surface().id())
                + (isOpen() ? "" : ", closed")
                + "]";
    }
}
