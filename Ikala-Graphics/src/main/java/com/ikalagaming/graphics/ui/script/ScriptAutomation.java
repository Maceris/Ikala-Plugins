package com.ikalagaming.graphics.ui.script;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.Steps;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
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
 * await(ui.test().click("main-menu//about"));
 * await(ui.test().waitForVisible("about"));
 * await(ui.test().assertText("about//title", "About"));
 * boolean open = await(ui.test().exists("about"));
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
        final ScriptRuntime runtime =
                ScriptRuntime.current()
                        .orElseThrow(
                                () -> new IllegalStateException("ui.test() is only for scripts"));
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
                            ScriptManager.terminate(runtime);
                        });
        return tag;
    }

    @Override
    public String toString() {
        return "ScriptAutomation[" + owner.getOwner() + "]";
    }
}
