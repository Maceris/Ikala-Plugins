package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * What a spec's names refer to on the Java side: handlers for events like {@code onClick},
 * observables for {@code {name}} bindings, lists for repeats, and a resource bundle for
 * {@code @KEY} text. Filled in before {@code UI.open}; opening fails if the spec names anything
 * missing here.
 */
public final class SpecBindings {
    /** Handlers by name. */
    private final Map<String, Consumer<SpecEvent>> handlers = new HashMap<>();

    /** Observables by name. */
    private final Map<String, Observable<?>> values = new HashMap<>();

    /** Lists by name. */
    private final Map<String, ObservableList<?>> lists = new HashMap<>();

    /** Text for {@code @KEY} values, or null for none. */
    private ResourceBundle bundle;

    /** Create empty bindings to fill in. */
    public SpecBindings() {
        // Filled in through the builder methods
    }

    /**
     * Copy bindings into immutable maps.
     *
     * @param from The bindings to copy.
     */
    private SpecBindings(SpecBindings from) {
        handlers.putAll(from.handlers);
        values.putAll(from.values);
        lists.putAll(from.lists);
        bundle = from.bundle;
    }

    /**
     * An unchanging copy, taken when a spec is opened, so later changes by the plugin don't race
     * with the render thread reading them.
     *
     * @return The copy.
     */
    SpecBindings snapshot() {
        return new SpecBindings(this);
    }

    /**
     * Add a handler that wants the event.
     *
     * @param name The name the spec uses, like {@code start-game}.
     * @param handler Runs on the render thread after the GUI is drawn.
     * @return These bindings.
     */
    public SpecBindings handler(@NonNull String name, @NonNull Consumer<SpecEvent> handler) {
        handlers.put(name, handler);
        return this;
    }

    /**
     * Add a handler that doesn't need the event.
     *
     * @param name The name the spec uses, like {@code start-game}.
     * @param handler Runs on the render thread after the GUI is drawn.
     * @return These bindings.
     */
    public SpecBindings handler(@NonNull String name, @NonNull Runnable handler) {
        return handler(name, event -> handler.run());
    }

    /**
     * Add an observable for {@code {name}} bindings.
     *
     * @param name The name the spec uses, like {@code player.health}.
     * @param value The observable.
     * @return These bindings.
     */
    public SpecBindings value(@NonNull String name, @NonNull Observable<?> value) {
        values.put(name, value);
        return this;
    }

    /**
     * Add a list for repeats.
     *
     * @param name The name the spec's repeat uses.
     * @param list The list.
     * @return These bindings.
     */
    public SpecBindings list(@NonNull String name, @NonNull ObservableList<?> list) {
        lists.put(name, list);
        return this;
    }

    /**
     * Set the resource bundle for {@code @KEY} text.
     *
     * @param resources The bundle.
     * @return These bindings.
     */
    public SpecBindings bundle(@NonNull ResourceBundle resources) {
        bundle = resources;
        return this;
    }

    /**
     * Find a handler.
     *
     * @param name The name.
     * @return The handler, or null.
     */
    Consumer<SpecEvent> handler(String name) {
        return handlers.get(name);
    }

    /**
     * Find an observable.
     *
     * @param name The name.
     * @return The observable, or null.
     */
    Observable<?> value(String name) {
        return values.get(name);
    }

    /**
     * Find a list.
     *
     * @param name The name.
     * @return The list, or null.
     */
    ObservableList<?> list(String name) {
        return lists.get(name);
    }

    /**
     * The resource bundle.
     *
     * @return The bundle, or null.
     */
    ResourceBundle bundle() {
        return bundle;
    }
}
