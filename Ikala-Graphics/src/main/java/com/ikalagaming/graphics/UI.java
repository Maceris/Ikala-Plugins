package com.ikalagaming.graphics;

import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.Tool;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.Steps;
import com.ikalagaming.graphics.ui.script.ScriptUi;
import com.ikalagaming.graphics.ui.spec.NodeFactory;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecException;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.graphics.ui.style.Theme;
import com.ikalagaming.graphics.ui.style.ThemeException;
import com.ikalagaming.graphics.ui.style.ThemeLoader;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;

import lombok.NonNull;

import java.io.File;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Retained UI owned by one plugin. Surfaces shown through it are removed when the plugin unloads.
 *
 * <p>Build a surface's tree on any thread, then {@link #show(Surface) show} it. Once shown, change
 * its nodes only on the render thread: inside node callbacks like button clicks, or through {@link
 * #post(Runnable)}.
 *
 * @see com.ikalagaming.graphics.ui
 */
public final class UI {
    /** The context that owns everything shown through this. */
    private final GraphicsContext context;

    /** Finds the UI manager, looked up when needed so contexts can exist before graphics starts. */
    private final Supplier<UiManager> manager;

    /** The UI this plugin's scripts get as {@code ui}, made when first asked for. */
    private final AtomicReference<ScriptUi> scripts = new AtomicReference<>();

    /** Text for {@code @KEY} values in specs scripts open, or null for none. */
    private volatile ResourceBundle scriptBundle;

    /**
     * Create the UI API for a context.
     *
     * @param context The owning context.
     * @param manager Finds the UI manager.
     */
    UI(@NonNull GraphicsContext context, @NonNull Supplier<UiManager> manager) {
        this.context = context;
        this.manager = manager;
    }

    /**
     * Create a surface to fill in and show. It isn't drawn until it is shown.
     *
     * @param id The surface ID, unique among all surfaces.
     * @return A new surface with an empty column as its content.
     */
    public Surface surface(@NonNull String id) {
        context.checkOpen();
        return new Surface(id);
    }

    /**
     * Show a surface from the next frame on, replacing any shown surface with the same ID.
     *
     * @param surface The surface.
     */
    public void show(@NonNull Surface surface) {
        context.checkOpen();
        manager.get().post(() -> manager.get().add(context, surface));
    }

    /**
     * Show or hide a surface this plugin showed, without removing it.
     *
     * @param id The surface ID.
     * @param visible Whether to draw it.
     */
    public void setVisible(@NonNull String id, boolean visible) {
        context.checkOpen();
        manager.get().post(() -> manager.get().setVisible(context, id, visible));
    }

    /**
     * Offer a tool for an editor's menus to list, like the asset editor's main menu. It goes away
     * when this plugin unloads, or when it is removed.
     *
     * @param id The tool's ID, unique among all tools.
     * @param label What menus show for it.
     * @param open Opens the tool, on the render thread.
     */
    public void registerTool(@NonNull String id, @NonNull String label, @NonNull Runnable open) {
        context.checkOpen();
        final Tool tool = new Tool(id, label, open);
        manager.get().post(() -> manager.get().addTool(context, tool));
    }

    /**
     * Withdraw a tool this plugin offered.
     *
     * @param id The tool's ID.
     */
    public void removeTool(@NonNull String id) {
        context.checkOpen();
        manager.get().post(() -> manager.get().removeTool(context, id));
    }

    /**
     * Every tool plugins offer, for a menu to list with a spec repeat.
     *
     * @return The tools, updated as plugins register and unload them.
     */
    public ObservableList<Tool> tools() {
        return manager.get().getTools();
    }

    /**
     * Remove a surface this plugin showed.
     *
     * @param id The surface ID.
     */
    public void remove(@NonNull String id) {
        context.checkOpen();
        manager.get().post(() -> manager.get().remove(context, id));
    }

    /**
     * Replace the theme for all retained UI, until this plugin unloads or chooses another. Meant
     * for the plugin that owns the game's look, like a game client.
     *
     * @param theme The theme, or null to go back to the default.
     */
    public void useTheme(Theme theme) {
        context.checkOpen();
        manager.get().post(() -> manager.get().useTheme(context, theme));
    }

    /**
     * Add this plugin's own tokens and classes on top of the theme, until it unloads. Prefix the
     * names with the plugin, like {@code converter.error}, so they don't clash with others.
     *
     * @param extension A theme holding the tokens and classes.
     */
    public void addStyles(@NonNull Theme extension) {
        context.checkOpen();
        manager.get().post(() -> manager.get().addStyles(context, extension));
    }

    /**
     * Load a theme from this plugin's data folder. Its styles may use the default theme's tokens.
     *
     * @param path The path within the plugin's data folder.
     * @return The theme.
     * @throws ThemeException If the theme is broken.
     * @throws java.io.UncheckedIOException If it can't be read.
     */
    public Theme loadTheme(@NonNull String path) {
        context.checkOpen();
        File file = PluginFolder.getResource(context.getOwner(), ResourceType.DATA, path);
        return ThemeLoader.load(file.toPath(), manager.get().getDefaultTheme());
    }

    /**
     * Open a UI spec: build it now and show it from the next frame. Its handlers and observables
     * come from the bindings. It is closed when this plugin unloads.
     *
     * @param spec The spec.
     * @param bindings The handlers, observables, lists and resource bundle the spec names.
     * @return The open spec, which can find nodes and be closed.
     * @throws SpecException If the spec can't be built with these bindings, such as a missing
     *     handler; nothing is shown then.
     */
    public SpecInstance open(@NonNull UiSpec spec, @NonNull SpecBindings bindings) {
        context.checkOpen();
        return SpecInstance.open(manager.get(), context, spec, bindings);
    }

    /**
     * Load a UI spec from this plugin's data folder, so it can be hot reloaded in the editor.
     *
     * @param path The path within the plugin's data folder.
     * @return The spec.
     * @throws SpecException If the spec is broken.
     * @throws java.io.UncheckedIOException If it can't be read.
     */
    public UiSpec loadSpec(@NonNull String path) {
        context.checkOpen();
        File file = PluginFolder.getResource(context.getOwner(), ResourceType.DATA, path);
        return SpecLoader.load(file.toPath());
    }

    /**
     * Add a node type that UI specs can use, owned by this plugin. When it unloads, the type is
     * removed and every open spec using it is closed, from any plugin.
     *
     * @param name The type name; prefix it with the plugin, like {@code converter.slot}.
     * @param factory Makes nodes of the type.
     * @throws IllegalArgumentException If the name is already taken.
     */
    public void registerNodeType(@NonNull String name, @NonNull NodeFactory factory) {
        context.checkOpen();
        manager.get().getNodeTypes().register(context, name, factory);
    }

    /**
     * The UI this plugin's scripts get as the global {@code ui}. Specs they open are owned by this
     * plugin.
     *
     * @return The script UI.
     */
    public ScriptUi scripts() {
        context.checkOpen();
        return scripts.updateAndGet(
                existing ->
                        existing != null
                                ? existing
                                : new ScriptUi(
                                        context,
                                        manager,
                                        this::loadSpec,
                                        () -> scriptBundle,
                                        null));
    }

    /**
     * Set the text for {@code @KEY} values in specs this plugin's scripts open.
     *
     * @param bundle The resource bundle, or null for none.
     */
    public void setScriptBundle(ResourceBundle bundle) {
        scriptBundle = bundle;
    }

    /**
     * Drive the UI like a player would, for tests and scripted checks: find nodes and click them,
     * type into them, wait for them and check their text. The run is owned by this plugin and
     * cancelled when it unloads. Don't wait for the result on the render thread, which runs it.
     *
     * <pre>
     * ui.automate(steps -&gt; steps.click("main-menu//about")
     *         .waitForVisible("about")
     *         .assertText("about//title", "About"));
     * </pre>
     *
     * @param build Adds the steps.
     * @return Finishes when the run does, or fails with a {@link
     *     com.ikalagaming.graphics.ui.automation.UiAutomationException}.
     */
    public CompletableFuture<Void> automate(@NonNull Consumer<Steps> build) {
        context.checkOpen();
        return manager.get().getAutomation().run(context, build);
    }

    /**
     * Run a change to shown UI on the render thread, at the start of the next frame.
     *
     * @param change The change.
     */
    public void post(@NonNull Runnable change) {
        context.checkOpen();
        manager.get().post(change);
    }
}
