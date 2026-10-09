package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What a spec's names refer to on the Java side: handlers for events like {@code onClick},
 * observables for {@code {name}} bindings, lists for repeats, and a resource bundle for
 * {@code @KEY} text. Filled in before {@code UI.open}; opening fails if the spec names anything
 * missing here.
 *
 * <p>For specs a script drives, it also holds the script that opened the spec, for {@code
 * resume(...)} actions, and can make observables on demand.
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

    /** Makes observables for names that weren't added, or null to treat them as missing. */
    private Function<String, Observable<?>> valueSource;

    /** Makes lists for names that weren't added, or null to treat them as missing. */
    private Function<String, ObservableList<?>> listSource;

    /** The script that opened the spec, which {@code resume(...)} actions resume, or null. */
    private ScriptRuntime script;

    /**
     * Where {@code script(...)} actions find script files, or null for the plugin's scripts folder.
     */
    private Path scriptFolder;

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
        valueSource = from.valueSource;
        listSource = from.listSource;
        script = from.script;
        scriptFolder = from.scriptFolder;
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
     * Make observables on demand for names that weren't added, so any {@code {name}} in the spec
     * can be bound, like for a spec a script drives. The source should give the same observable
     * each time it is asked for a name.
     *
     * @param source Makes the observable for a name.
     * @return These bindings.
     */
    public SpecBindings values(@NonNull Function<String, Observable<?>> source) {
        valueSource = source;
        return this;
    }

    /**
     * Make lists on demand for repeats over names that weren't added, like for a spec a script
     * drives. The source should give the same list each time it is asked for a name.
     *
     * @param source Makes the list for a name.
     * @return These bindings.
     */
    public SpecBindings lists(@NonNull Function<String, ObservableList<?>> source) {
        listSource = source;
        return this;
    }

    /**
     * Set the script that opened the spec, which its {@code resume(tag, value)} actions resume.
     *
     * @param opener The script.
     * @return These bindings.
     */
    public SpecBindings script(@NonNull ScriptRuntime opener) {
        script = opener;
        return this;
    }

    /**
     * Set where {@code script(file)} actions find their files, instead of the plugin's scripts
     * folder.
     *
     * @param folder The folder.
     * @return These bindings.
     */
    public SpecBindings scriptFolder(@NonNull Path folder) {
        scriptFolder = folder;
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
        Observable<?> found = values.get(name);
        if (found == null && valueSource != null) {
            found = valueSource.apply(name);
        }
        return found;
    }

    /**
     * Find a list.
     *
     * @param name The name.
     * @return The list, or null.
     */
    ObservableList<?> list(String name) {
        ObservableList<?> found = lists.get(name);
        if (found == null && listSource != null) {
            found = listSource.apply(name);
        }
        return found;
    }

    /**
     * The resource bundle.
     *
     * @return The bundle, or null.
     */
    ResourceBundle bundle() {
        return bundle;
    }

    /**
     * The script that opened the spec.
     *
     * @return The script, or null.
     */
    ScriptRuntime script() {
        return script;
    }

    /**
     * Where script files are found.
     *
     * @return The folder, or null for the plugin's scripts folder.
     */
    Path scriptFolder() {
        return scriptFolder;
    }
}
