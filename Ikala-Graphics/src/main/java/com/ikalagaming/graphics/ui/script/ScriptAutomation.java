package com.ikalagaming.graphics.ui.script;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.Steps;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFW;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * UI automation as scripts see it, through {@code ui.test()}. Each call starts one step and returns
 * a tag to await, which resumes the script with true once the step is done. A step that fails logs
 * why and stops the script, like a failed assertion.
 *
 * <pre>
 * await(ui.test().click("main-menu//benchmarks"));
 * await(ui.test().waitForVisible("benchmarks"));
 * await(ui.test().assertText("benchmarks//title", "Benchmarks"));
 * boolean open = await(ui.test().exists("benchmarks"));
 * await(ui.test().mouseDrag(640, 360, 700, 360));
 * await(ui.test().clickItem("tools//lod", "Show cells"));
 * await(ui.test().typeItem("tools//materials", "Roughness", "0.5", true));
 * await(ui.test().saveFrame("shots/front.png"));
 * await(ui.test().saveWindow("shots/editor.png"));
 * await(ui.test().mouseClickAt("graph//graph-canvas", "node add1", "right"));
 * await(ui.test().waitForTooltip("isn't linked"));
 * </pre>
 *
 * <p>Selectors are written as for {@link com.ikalagaming.graphics.ui.automation.Selector}.
 */
@Slf4j
public final class ScriptAutomation {
    /** Prefix of the tags steps resume with. */
    private static final String TAG_PREFIX = "ui.test/";

    /** Numbers the tags, so each step's is unique. */
    private static final AtomicInteger NEXT_TAG = new AtomicInteger();

    /** The plugin the runs are owned by. */
    private final GraphicsContext owner;

    /** Finds the UI manager. */
    private final Supplier<UiManager> manager;

    /**
     * Create the automation for a plugin's scripts.
     *
     * @param owner The plugin.
     * @param manager Finds the UI manager.
     */
    ScriptAutomation(@NonNull GraphicsContext owner, @NonNull Supplier<UiManager> manager) {
        this.owner = owner;
        this.manager = manager;
    }

    /**
     * Click a node.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String click(@NonNull String selector) {
        return start(steps -> steps.click(selector));
    }

    /**
     * Double click a node.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String doubleClick(@NonNull String selector) {
        return start(steps -> steps.doubleClick(selector));
    }

    /**
     * Move the mouse over a node.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String hover(@NonNull String selector) {
        return start(steps -> steps.hover(selector));
    }

    /**
     * Replace the text in a text field.
     *
     * @param selector Picks out the field.
     * @param text The text.
     * @return The tag to await.
     */
    public String type(@NonNull String selector, @NonNull String text) {
        return start(steps -> steps.type(selector, text));
    }

    /**
     * Replace the text in a text field, then press Enter if asked.
     *
     * @param selector Picks out the field.
     * @param text The text.
     * @param enter Whether to press Enter.
     * @return The tag to await.
     */
    public String type(@NonNull String selector, @NonNull String text, boolean enter) {
        return start(steps -> steps.type(selector, text, enter));
    }

    /**
     * Press a key, like {@code "enter"}, {@code "arrow_down"} or {@code "ctrl+s"}.
     *
     * @param keys The key, after any modifiers ({@code ctrl}, {@code shift}, {@code alt}, {@code
     *     super}) joined with {@code +}.
     * @return The tag to await.
     * @throws IllegalArgumentException If a key isn't known.
     */
    public String key(@NonNull String keys) {
        String[] parts = keys.split("\\+");
        Key[] modifiers = new Key[parts.length - 1];
        for (int i = 0; i < modifiers.length; ++i) {
            modifiers[i] = keyNamed(parts[i], true);
        }
        Key key = keyNamed(parts[parts.length - 1], false);
        return start(steps -> steps.key(key, modifiers));
    }

    /**
     * Type characters into whatever has the keyboard, without clicking or selecting first.
     *
     * @param text The characters.
     * @return The tag to await.
     */
    public String chars(@NonNull String text) {
        return start(steps -> steps.chars(text));
    }

    /**
     * Scroll a node into view.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String scrollTo(@NonNull String selector) {
        return start(steps -> steps.scrollTo(selector));
    }

    /**
     * Scroll a virtual grid to a cell.
     *
     * @param selector Picks out the grid.
     * @param index The cell index.
     * @return The tag to await.
     */
    public String scrollToCell(@NonNull String selector, int index) {
        return start(steps -> steps.scrollToCell(selector, index));
    }

    /**
     * Wait until a node is on screen.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String waitForVisible(@NonNull String selector) {
        return start(steps -> steps.waitForVisible(selector));
    }

    /**
     * Wait until a node is off screen.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String waitForGone(@NonNull String selector) {
        return start(steps -> steps.waitForGone(selector));
    }

    /**
     * Wait until a node shows some text.
     *
     * @param selector Picks out the node.
     * @param text The text.
     * @return The tag to await.
     */
    public String waitForText(@NonNull String selector, @NonNull String text) {
        return start(steps -> steps.waitForText(selector, text));
    }

    /**
     * Check a node is on screen.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String assertVisible(@NonNull String selector) {
        return start(steps -> steps.assertVisible(selector));
    }

    /**
     * Check a node shows some text.
     *
     * @param selector Picks out the node.
     * @param text The text.
     * @return The tag to await.
     */
    public String assertText(@NonNull String selector, @NonNull String text) {
        return start(steps -> steps.assertText(selector, text));
    }

    /**
     * Find out whether a node is on screen. Awaiting the tag gives true or false.
     *
     * @param selector Picks out the node.
     * @return The tag to await.
     */
    public String exists(@NonNull String selector) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        return start(steps -> steps.exists(selector, found::set), found::get);
    }

    /**
     * Wait for some frames.
     *
     * @param count The number of frames.
     * @return The tag to await.
     */
    public String frames(int count) {
        return start(steps -> steps.frames(count));
    }

    /**
     * Move the mouse to a point in the window, in IkGui pixels from its top left corner.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return The tag to await.
     */
    public String mouseMove(float x, float y) {
        return start(steps -> steps.mouseMove(x, y));
    }

    /**
     * Click the left mouse button at a point in the window, like on a mesh in a 3D view.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return The tag to await.
     */
    public String mouseClick(float x, float y) {
        return start(steps -> steps.mouseClick(x, y));
    }

    /**
     * Click a mouse button at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @param button {@code left}, {@code right} or {@code middle}.
     * @return The tag to await.
     * @throws IllegalArgumentException If the button isn't known.
     */
    public String mouseClick(float x, float y, @NonNull String button) {
        MouseButton pressed = buttonNamed(button);
        return start(steps -> steps.mouseClick(x, y, pressed));
    }

    /**
     * Double click the left mouse button at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @return The tag to await.
     */
    public String mouseDoubleClick(float x, float y) {
        return start(steps -> steps.mouseDoubleClick(x, y));
    }

    /**
     * Drag with the left mouse button from one point in the window to another.
     *
     * @param fromX Where to press, in pixels from the left.
     * @param fromY Where to press, in pixels from the top.
     * @param toX Where to release, in pixels from the left.
     * @param toY Where to release, in pixels from the top.
     * @return The tag to await.
     */
    public String mouseDrag(float fromX, float fromY, float toX, float toY) {
        return start(steps -> steps.mouseDrag(fromX, fromY, toX, toY));
    }

    /**
     * Drag with a mouse button from one point in the window to another.
     *
     * @param fromX Where to press, in pixels from the left.
     * @param fromY Where to press, in pixels from the top.
     * @param toX Where to release, in pixels from the left.
     * @param toY Where to release, in pixels from the top.
     * @param button {@code left}, {@code right} or {@code middle}.
     * @return The tag to await.
     * @throws IllegalArgumentException If the button isn't known.
     */
    public String mouseDrag(
            float fromX, float fromY, float toX, float toY, @NonNull String button) {
        MouseButton pressed = buttonNamed(button);
        return start(steps -> steps.mouseDrag(fromX, fromY, toX, toY, pressed));
    }

    /**
     * Turn the mouse wheel with the mouse at a point in the window.
     *
     * @param x Pixels from the left.
     * @param y Pixels from the top.
     * @param amount Wheel notches, positive scrolling up.
     * @return The tag to await.
     */
    public String mouseWheel(float x, float y, float amount) {
        return start(steps -> steps.mouseWheel(x, y, amount));
    }

    /**
     * Click an IkGui item drawn in immediate mode.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @return The tag to await.
     * @see com.ikalagaming.graphics.ui.automation.ItemTarget
     */
    public String clickItem(@NonNull String scope, @NonNull String label) {
        return start(steps -> steps.clickItem(scope, label));
    }

    /**
     * Click an IkGui item drawn in immediate mode with a mouse button.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @param button {@code left}, {@code right} or {@code middle}.
     * @return The tag to await.
     * @throws IllegalArgumentException If the button isn't known.
     */
    public String clickItem(@NonNull String scope, @NonNull String label, @NonNull String button) {
        MouseButton pressed = buttonNamed(button);
        return start(steps -> steps.clickItem(scope, label, pressed));
    }

    /**
     * Double click an IkGui item drawn in immediate mode.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @return The tag to await.
     */
    public String doubleClickItem(@NonNull String scope, @NonNull String label) {
        return start(steps -> steps.doubleClickItem(scope, label));
    }

    /**
     * Move the mouse over an IkGui item drawn in immediate mode.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @return The tag to await.
     */
    public String hoverItem(@NonNull String scope, @NonNull String label) {
        return start(steps -> steps.hoverItem(scope, label));
    }

    /**
     * Replace the text of an IkGui text field, slider or drag drawn in immediate mode.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @param text The text.
     * @return The tag to await.
     */
    public String typeItem(@NonNull String scope, @NonNull String label, @NonNull String text) {
        return start(steps -> steps.typeItem(scope, label, text));
    }

    /**
     * Replace the text of an IkGui text field, slider or drag drawn in immediate mode, then press
     * Enter if asked.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @param text The text.
     * @param enter Whether to press Enter.
     * @return The tag to await.
     */
    public String typeItem(
            @NonNull String scope, @NonNull String label, @NonNull String text, boolean enter) {
        return start(steps -> steps.typeItem(scope, label, text, enter));
    }

    /**
     * Wait until an IkGui item drawn in immediate mode is on screen.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @return The tag to await.
     */
    public String waitForItem(@NonNull String scope, @NonNull String label) {
        return start(steps -> steps.waitForItem(scope, label));
    }

    /**
     * Check whether a check box drawn in immediate mode is checked.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @param checked Whether it should be checked.
     * @return The tag to await.
     */
    public String assertItemChecked(@NonNull String scope, @NonNull String label, boolean checked) {
        return start(steps -> steps.assertItemChecked(scope, label, checked));
    }

    /**
     * Find out whether an IkGui item drawn in immediate mode is on screen. Awaiting the tag gives
     * true or false.
     *
     * @param scope Where to look: a selector, {@code window=Name}, or {@code *}.
     * @param label The item's label.
     * @return The tag to await.
     */
    public String itemExists(@NonNull String scope, @NonNull String label) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        return start(steps -> steps.itemExists(scope, label, found::set), found::get);
    }

    /**
     * Press a key and keep it held, as IkGui sees it, until {@link #keyUp}: for the GUI, like
     * holding a key while clicking. {@link #holdKey} is for the scene's own keys instead.
     *
     * @param name The key's IkGui name, like {@code "m"}, {@code "one"} or {@code "left_shift"}.
     * @return The tag to await.
     * @throws IllegalArgumentException If the key isn't known.
     */
    public String keyDown(@NonNull String name) {
        final Key key = keyNamed(name, false);
        return start(steps -> steps.keyDown(key));
    }

    /**
     * Let go of a key {@link #keyDown} held.
     *
     * @param name The key's IkGui name.
     * @return The tag to await.
     * @throws IllegalArgumentException If the key isn't known.
     */
    public String keyUp(@NonNull String name) {
        final Key key = keyNamed(name, false);
        return start(steps -> steps.keyUp(key));
    }

    /**
     * Move the mouse to a point a node named, like a graph editor's pin, and leave it there.
     *
     * @param selector Picks out the node.
     * @param point The point's name, as the node understands it.
     * @return The tag to await.
     */
    public String mouseMoveTo(@NonNull String selector, @NonNull String point) {
        return start(steps -> steps.mouseMoveTo(selector, point));
    }

    /**
     * Click the left mouse button at a point a node named.
     *
     * @param selector Picks out the node.
     * @param point The point's name, as the node understands it.
     * @return The tag to await.
     */
    public String mouseClickAt(@NonNull String selector, @NonNull String point) {
        return start(steps -> steps.mouseClickAt(selector, point, MouseButton.LEFT));
    }

    /**
     * Click a mouse button at a point a node named.
     *
     * @param selector Picks out the node.
     * @param point The point's name, as the node understands it.
     * @param button {@code left}, {@code right} or {@code middle}.
     * @return The tag to await.
     */
    public String mouseClickAt(
            @NonNull String selector, @NonNull String point, @NonNull String button) {
        final MouseButton pressed = buttonNamed(button);
        return start(steps -> steps.mouseClickAt(selector, point, pressed));
    }

    /**
     * Double click at a point a node named.
     *
     * @param selector Picks out the node.
     * @param point The point's name, as the node understands it.
     * @return The tag to await.
     */
    public String mouseDoubleClickAt(@NonNull String selector, @NonNull String point) {
        return start(steps -> steps.mouseDoubleClickAt(selector, point));
    }

    /**
     * Drag with the left mouse button from one point a node named to another.
     *
     * @param selector Picks out the node.
     * @param from The point to press at.
     * @param to The point to release at.
     * @return The tag to await.
     */
    public String mouseDragAt(@NonNull String selector, @NonNull String from, @NonNull String to) {
        return start(steps -> steps.mouseDragAt(selector, from, to));
    }

    /**
     * Wait for a tooltip holding some text.
     *
     * @param text The text, matched anywhere in the tooltip.
     * @return The tag to await.
     */
    public String waitForTooltip(@NonNull String text) {
        return start(steps -> steps.waitForTooltip(text));
    }

    /**
     * Read the tooltip showing now. Awaiting the tag gives its text, or an empty string if none.
     *
     * @return The tag to await.
     */
    public String tooltip() {
        AtomicReference<String> shown = new AtomicReference<>("");
        return start(
                steps -> steps.tooltip(text -> shown.set(text == null ? "" : text)), shown::get);
    }

    /**
     * Hold a key down in the scene window, as code that polls keys sees it (movement, not GUI
     * text), until {@link #releaseKey}. Takes effect from the next frame.
     *
     * @param name The key's GLFW name without {@code GLFW_KEY_}, like {@code "w"}, {@code "space"}
     *     or {@code "left_shift"}.
     * @throws IllegalArgumentException If the key isn't known.
     */
    public void holdKey(@NonNull String name) {
        final int code = glfwKey(name);
        GraphicsManager.runOnMainThread(
                () -> {
                    if (GraphicsManager.getSceneWindow() != null) {
                        GraphicsManager.getSceneWindow().holdKey(code);
                    }
                });
    }

    /**
     * Stop holding a key that {@link #holdKey} held. Takes effect from the next frame.
     *
     * @param name The key's GLFW name without {@code GLFW_KEY_}.
     * @throws IllegalArgumentException If the key isn't known.
     */
    public void releaseKey(@NonNull String name) {
        final int code = glfwKey(name);
        GraphicsManager.runOnMainThread(
                () -> {
                    if (GraphicsManager.getSceneWindow() != null) {
                        GraphicsManager.getSceneWindow().releaseKey(code);
                    }
                });
    }

    /**
     * A key's GLFW code by name.
     *
     * @param name The name without {@code GLFW_KEY_}, in any case.
     * @return The code.
     * @throws IllegalArgumentException If there is no such key.
     */
    private static int glfwKey(String name) {
        final String field = "GLFW_KEY_" + name.trim().toUpperCase(Locale.ROOT);
        try {
            return GLFW.class.getField(field).getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("There is no key called '" + name + "'", e);
        }
    }

    /**
     * Draw the GUI or not, by the pipeline config, as {@link GraphicsManager#swapPipeline} does:
     * frames saved headless show what is drawn, so captures of the scene alone turn it off. Posted
     * UI changes and frame waits still run without it, but steps that need a surface on screen wait
     * until it is back. Takes effect from the next frame.
     *
     * @param show Whether to draw the GUI.
     */
    public void showGui(boolean show) {
        GraphicsManager.runOnMainThread(
                () ->
                        GraphicsManager.swapPipeline(
                                GraphicsManager.getPipelineConfig().withGui(show)));
    }

    /**
     * Save the next frame that draws the scene to a PNG file. Headless, it is the frame as a window
     * would show it, with the GUI when the pipeline draws GUI (see {@link #showGui}); in a window,
     * the scene without the GUI over it. The window doesn't have to be in front, but a minimized
     * window draws nothing to save.
     *
     * @param path Where the file goes, a .png file on this machine; folders are made as needed.
     * @return The tag to await, which resumes once the file is written.
     * @throws IllegalArgumentException If the path isn't a local .png file.
     */
    public String saveFrame(@NonNull String path) {
        final Path file = frameFile(path);
        return awaitSaved(path, GraphicsManager.getRenderInstance().saveFrame(file));
    }

    /**
     * Save the main window as it is next shown to a PNG file: the GUI's panels, popups and tooltips
     * over the scene, as on screen. Windows popped out of the main one aren't in it. The window
     * doesn't have to be in front, but a minimized window draws nothing to save. Headless, it is
     * the same as {@link #saveFrame} with the GUI drawn.
     *
     * @param path Where the file goes, a .png file on this machine; folders are made as needed.
     * @return The tag to await, which resumes once the file is written.
     * @throws IllegalArgumentException If the path isn't a local .png file.
     */
    public String saveWindow(@NonNull String path) {
        final Path file = frameFile(path);
        return awaitSaved(path, GraphicsManager.getRenderInstance().saveWindow(file));
    }

    /**
     * Save the main window as it is next shown to a PNG file in a folder, as {@link
     * #saveWindow(String)} does, for scripts given a folder as their arguments.
     *
     * @param folder The folder; made as needed.
     * @param name The file's name in it, a .png file.
     * @return The tag to await, which resumes once the file is written.
     * @throws IllegalArgumentException If the path isn't a local .png file.
     */
    public String saveWindow(@NonNull String folder, @NonNull String name) {
        return saveWindow(Path.of(folder, name).toString());
    }

    /**
     * A tag that resumes the calling script once a frame is saved, or stops it if saving fails.
     *
     * @param path The path the script gave, for messages.
     * @param saving The save.
     * @return The tag to await.
     */
    private String awaitSaved(String path, CompletableFuture<Path> saving) {
        final ScriptRuntime runtime = currentRuntime();
        final String tag = TAG_PREFIX + NEXT_TAG.incrementAndGet();
        saving.whenComplete(
                (saved, error) -> {
                    if (error == null) {
                        ScriptManager.resume(runtime, tag, true);
                        return;
                    }
                    log.warn(
                            "Saving a frame to {} failed in a script from {}, stopping it:" + " {}",
                            path,
                            owner.getOwner(),
                            error.getMessage());
                    StartupScripts.failed(owner.getOwner());
                    ScriptManager.terminate(runtime);
                });
        return tag;
    }

    /**
     * Check where a script wants a frame saved. Scripts can come with downloaded content, so they
     * may only write PNG files, and never to a network share, where just opening the path would
     * send the user's Windows credentials to that host.
     *
     * @param path The path the script gave.
     * @return The absolute path.
     * @throws IllegalArgumentException If it isn't a local .png file.
     */
    static Path frameFile(String path) {
        final String slashes = path.replace('\\', '/');
        if (slashes.startsWith("//")
                || slashes.indexOf(':', 2) >= 0
                || !slashes.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("Frames save to local .png files, not " + path);
        }
        try {
            return Path.of(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Not a valid path: " + path, e);
        }
    }

    /**
     * The script calling, for steps that resume it.
     *
     * @return The script's runtime.
     * @throws IllegalStateException If not called from a script.
     */
    private static ScriptRuntime currentRuntime() {
        return ScriptRuntime.current()
                .orElseThrow(() -> new IllegalStateException("ui.test() is only for scripts"));
    }

    /**
     * Read a mouse button name.
     *
     * @param name {@code left}, {@code right} or {@code middle}, in any case.
     * @return The button.
     * @throws IllegalArgumentException If no button has that name.
     */
    private static MouseButton buttonNamed(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> MouseButton.LEFT;
            case "right" -> MouseButton.RIGHT;
            case "middle" -> MouseButton.MIDDLE;
            default ->
                    throw new IllegalArgumentException(
                            "There is no mouse button called '" + name + "'");
        };
    }

    /**
     * Read a key name.
     *
     * @param name The name, in any case.
     * @param modifier Whether it is a modifier, so {@code ctrl} means {@link Key#MOD_CTRL}.
     * @return The key.
     * @throws IllegalArgumentException If no key has that name.
     */
    private static Key keyNamed(String name, boolean modifier) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        if (modifier && !upper.startsWith("MOD_")) {
            upper = "MOD_" + ("CONTROL".equals(upper) ? "CTRL" : upper);
        }
        try {
            return Key.valueOf(upper);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("There is no key called '" + name + "'", e);
        }
    }

    /**
     * Start a one-step run for the calling script.
     *
     * @param build Adds the step.
     * @return The tag to await.
     */
    private String start(Consumer<Steps> build) {
        return start(build, () -> true);
    }

    /**
     * Start a one-step run for the calling script.
     *
     * @param build Adds the step.
     * @param result The value the await returns once the step is done.
     * @return The tag to await.
     * @throws IllegalStateException If not called from a script.
     */
    private String start(Consumer<Steps> build, Supplier<Object> result) {
        final ScriptRuntime runtime = currentRuntime();
        final String tag = TAG_PREFIX + NEXT_TAG.incrementAndGet();
        manager.get()
                .getAutomation()
                .run(owner, build)
                .whenComplete(
                        (done, error) -> {
                            if (error == null) {
                                ScriptManager.resume(runtime, tag, result.get());
                                return;
                            }
                            Throwable cause =
                                    error instanceof CompletionException && error.getCause() != null
                                            ? error.getCause()
                                            : error;
                            log.warn(
                                    "UI check failed in a script from {}, stopping it: {}",
                                    owner.getOwner(),
                                    cause.getMessage());
                            StartupScripts.failed(owner.getOwner());
                            ScriptManager.terminate(runtime);
                        });
        return tag;
    }

    @Override
    public String toString() {
        return "ScriptAutomation[" + owner.getOwner() + "]";
    }
}
