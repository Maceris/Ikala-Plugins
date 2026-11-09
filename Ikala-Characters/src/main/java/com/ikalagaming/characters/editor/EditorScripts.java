package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * What scripts can do with the character editor, through the global {@code characters}, for
 * captures and checks that shouldn't click through its panels:
 *
 * <pre>
 * converter.openTool("characters/editor");
 * await(characters.settle());
 * characters.showDesign("characters:design/fighter");
 * await(characters.settle());
 * characters.bare();
 * characters.wear("gen:part/body/robot_leg_l");
 * characters.still(true);
 * characters.frame(null, 0.1, 0);
 * converter.log(characters.worn());
 * await(ui.test().saveFrame("shots/front.png"));
 * </pre>
 *
 * <p>Static methods only, so the global is the class. Scripts run on their own thread, so each call
 * runs on the render thread and waits for it. A call that can't do what it was asked throws, which
 * stops the script.
 */
@Slf4j
public final class EditorScripts {
    /** The name scripts use. */
    public static final String GLOBAL = "characters";

    /** How long a call waits for the render thread. */
    private static final long CALL_TIMEOUT_SECONDS = 30;

    /** How often {@link #settle()} looks, in milliseconds. */
    private static final long SETTLE_POLL_MS = 50;

    /** How long {@link #settle()} waits before giving up, in seconds. */
    private static final long SETTLE_TIMEOUT_SECONDS = 120;

    /** Prefix of the tags {@link #settle()} resumes with. */
    private static final String TAG_PREFIX = "characters/";

    /** Numbers the tags, so each wait's is unique. */
    private static final AtomicInteger NEXT_TAG = new AtomicInteger();

    /** Looks for {@link #settle()}, on one daemon thread. */
    private static final ScheduledExecutorService POLLER =
            Executors.newSingleThreadScheduledExecutor(
                    work -> {
                        Thread thread = new Thread(work, "characters-script-settle");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * The editor and its plugin's graphics, while the plugin is enabled.
     *
     * @param editor The editor.
     * @param graphics The plugin's graphics.
     */
    private record Target(CharacterEditor editor, GraphicsContext graphics) {}

    /** What calls go to, or null while the plugin is disabled. */
    private static volatile Target target;

    private EditorScripts() {}

    /**
     * Point script calls at an editor, or at nothing.
     *
     * @param editor The editor, or null when the plugin is disabled.
     * @param graphics The plugin's graphics, or null with it.
     */
    public static void attach(CharacterEditor editor, GraphicsContext graphics) {
        target = editor == null ? null : new Target(editor, graphics);
    }

    /**
     * Show a design by its ID. It loads on another thread; {@link #settle()} waits for it.
     *
     * @param id The design's ID, like "characters:design/fighter".
     * @throws IllegalStateException If the editor is closed or no design has that ID.
     */
    public static void showDesign(@NonNull String id) {
        if (!call(editor -> editor.showDesignNamed(id))) {
            throw new IllegalStateException("No design " + id + " to show, or the editor is shut");
        }
    }

    /**
     * Wait until the design asked for last is on the stage, or failed to load.
     *
     * @return The tag to await.
     * @throws IllegalStateException If not called from a script.
     */
    public static String settle() {
        final ScriptRuntime runtime =
                ScriptRuntime.current()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "characters.settle() is only for scripts"));
        final String tag = TAG_PREFIX + NEXT_TAG.incrementAndGet();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_TIMEOUT_SECONDS);
        POLLER.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        boolean settled;
                        try {
                            settled = call(CharacterEditor::isSettled);
                        } catch (IllegalStateException e) {
                            settled = false;
                        }
                        if (settled) {
                            ScriptManager.resume(runtime, tag, true);
                        } else if (System.nanoTime() > deadline) {
                            log.warn("Script: the character editor never settled, stopping it");
                            ScriptManager.terminate(runtime);
                        } else {
                            POLLER.schedule(this, SETTLE_POLL_MS, TimeUnit.MILLISECONDS);
                        }
                    }
                },
                SETTLE_POLL_MS,
                TimeUnit.MILLISECONDS);
        return tag;
    }

    /** Take off everything that can be taken off, keeping the body. */
    public static void bare() {
        call(
                editor -> {
                    editor.bare();
                    return true;
                });
    }

    /**
     * Wear a part, filling the slot that offers it.
     *
     * @param partId The part's ID, like "gen:part/body/robot_leg_l".
     * @throws IllegalStateException If nothing is shown or no slot offers the part.
     */
    public static void wear(@NonNull String partId) {
        if (!call(editor -> editor.wear(partId))) {
            throw new IllegalStateException("The shown design offers no part " + partId);
        }
    }

    /**
     * Wear a decal, like an emblem on the chest, or none.
     *
     * @param decalId The decal's ID, like "ikala:decal/star", or an empty string for none.
     * @param place The body plan's decal place, like "chest", or an empty string for its first.
     * @param first The palette channel its first color takes, or an empty string for its default.
     * @param second The channel its second color takes, or an empty string.
     * @throws IllegalStateException If nothing is shown or the body has no decal places.
     */
    public static void decal(
            @NonNull String decalId,
            @NonNull String place,
            @NonNull String first,
            @NonNull String second) {
        if (!call(editor -> editor.wearDecal(decalId, place, first, second))) {
            throw new IllegalStateException("The shown design takes no decals");
        }
    }

    /**
     * The parts worn, as the game would show them, for checks.
     *
     * @return The part IDs in slot order, joined by commas, or an empty string.
     */
    public static String worn() {
        String[] found = {""};
        call(
                editor -> {
                    found[0] = String.join(",", editor.wornIds());
                    return true;
                });
        return found[0];
    }

    /**
     * Put pairs, like both claws, on together, or choose sides alone.
     *
     * @param both Whether pairs go on together.
     */
    public static void bothSides(boolean both) {
        call(
                editor -> {
                    editor.setBothSides(both);
                    return true;
                });
    }

    /**
     * Draw debug shapes, like the Align tab's markers, or not, for captures without them.
     *
     * @param show Whether to draw them.
     */
    public static void markers(boolean show) {
        call(
                editor -> {
                    editor.showMarkers(show);
                    return true;
                });
    }

    /**
     * Stand the character at rest, or let it move again.
     *
     * @param still Whether it stands at rest.
     */
    public static void still(boolean still) {
        call(
                editor -> {
                    editor.setStill(still);
                    return true;
                });
    }

    /**
     * Move in place at a speed, as the Motion tab's slider does, which swings chains like a cape
     * back.
     *
     * @param metersPerSecond The speed, 0 to stand.
     */
    public static void move(double metersPerSecond) {
        call(
                editor -> {
                    editor.setSpeed((float) metersPerSecond);
                    return true;
                });
    }

    /**
     * Fold, spread, glide or flap the worn wings, as the Motion tab does.
     *
     * @param state What they do: "fold", "spread", "glide" or "flap".
     * @throws IllegalArgumentException If there is no such state.
     */
    public static void wings(@NonNull String state) {
        WingMotion.State chosen = WingMotion.State.of(state);
        call(
                editor -> {
                    editor.setWingState(chosen);
                    return true;
                });
    }

    /**
     * Look at the whole character or one part from far enough away to see it whole.
     *
     * @param partId The part to frame, or null for the whole character.
     * @param pitch How far the camera looks down, in radians.
     * @param yaw How far the camera has turned around the character, in radians; 0 looks at its
     *     front, pi/2 at its left side.
     * @throws IllegalStateException If nothing is shown or the part isn't offered.
     */
    public static void frame(String partId, double pitch, double yaw) {
        if (!call(editor -> editor.frame(partId, pitch, yaw))) {
            throw new IllegalStateException(
                    "Can't frame " + (partId == null ? "the character" : partId));
        }
    }

    /**
     * Run something on the render thread and wait for it.
     *
     * @param work What to run with the editor.
     * @return What it returned.
     * @throws IllegalStateException If the plugin is disabled, or the render thread didn't run it.
     */
    private static boolean call(Function<CharacterEditor, Boolean> work) {
        final Target to = target;
        if (to == null || to.graphics().isClosed()) {
            throw new IllegalStateException("The character editor isn't loaded");
        }
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        to.graphics()
                .ui()
                .post(
                        () -> {
                            try {
                                done.complete(work.apply(to.editor()));
                            } catch (RuntimeException e) {
                                done.completeExceptionally(e);
                            }
                        });
        return waitFor(done);
    }

    /**
     * Wait for the render thread to finish a call.
     *
     * @param done The call's result.
     * @return Its value.
     * @throws IllegalStateException If it failed or took too long.
     */
    private static boolean waitFor(CompletableFuture<Boolean> done) {
        try {
            return done.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for the character editor", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("The character editor didn't answer", e);
        }
    }
}
