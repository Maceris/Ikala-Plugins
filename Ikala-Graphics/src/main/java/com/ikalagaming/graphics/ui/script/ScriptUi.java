package com.ikalagaming.graphics.ui.script;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The UI as scripts see it, given to them as the global {@code ui}. Each plugin's scripts get their
 * own, so what they open is owned by that plugin and closed when it unloads.
 *
 * <pre>
 * dialogue = ui.open("ui/dialogue.yml");
 * dialogue.set("line", "Did you bring the crystal?");
 * int choice = await("dialogue/choice");
 * dialogue.close();
 * </pre>
 */
public final class ScriptUi {
    /** The plugin scripts open UI for. */
    private final GraphicsContext owner;

    /** Finds the UI manager. */
    private final Supplier<UiManager> manager;

    /** Loads specs by their path in the plugin's data folder. */
    private final Function<String, UiSpec> loader;

    /** Finds the text for {@code @KEY} values, which may give null for none. */
    private final Supplier<ResourceBundle> bundle;

    /** Where specs' {@code script(...)} actions find files, or null for the scripts folder. */
    private final Path scriptFolder;

    /**
     * Create the script UI for a plugin. Plugins get theirs from {@code UI.scripts()}.
     *
     * @param owner The plugin.
     * @param manager Finds the UI manager.
     * @param loader Loads specs by their path in the plugin's data folder.
     * @param bundle Finds the text for {@code @KEY} values, which may give null for none.
     * @param scriptFolder Where {@code script(...)} actions find files, or null for the plugin's
     *     scripts folder.
     */
    public ScriptUi(
            @NonNull GraphicsContext owner,
            @NonNull Supplier<UiManager> manager,
            @NonNull Function<String, UiSpec> loader,
            @NonNull Supplier<ResourceBundle> bundle,
            Path scriptFolder) {
        this.owner = owner;
        this.manager = manager;
        this.loader = loader;
        this.bundle = bundle;
        this.scriptFolder = scriptFolder;
    }

    /**
     * Open a spec from the plugin's data folder. Its {@code {name}} bindings show values the script
     * sets on the returned handle, and its {@code resume(tag, value)} actions resume the script
     * that opened it.
     *
     * @param path The spec's path in the plugin's data folder.
     * @return The open spec.
     * @throws com.ikalagaming.graphics.ui.spec.SpecException If the spec is broken.
     */
    public ScriptSpec open(@NonNull String path) {
        final UiSpec spec = loader.apply(path);
        final UiManager current = manager.get();
        final ScriptSpec handle = new ScriptSpec(owner, current);
        final SpecBindings bindings =
                new SpecBindings().values(handle::observable).lists(handle::list);
        ScriptRuntime.current().ifPresent(bindings::script);
        final ResourceBundle text = bundle.get();
        if (text != null) {
            bindings.bundle(text);
        }
        if (scriptFolder != null) {
            bindings.scriptFolder(scriptFolder);
        }
        handle.attach(SpecInstance.open(current, owner, spec, bindings));
        return handle;
    }

    @Override
    public String toString() {
        return "ScriptUi[" + owner.getOwner() + "]";
    }
}
