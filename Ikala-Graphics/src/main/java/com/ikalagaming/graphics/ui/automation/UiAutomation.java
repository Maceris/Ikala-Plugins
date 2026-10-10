package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Drives the retained UI like a player would, for tests and scripted checks. Runs of {@link Steps}
 * find nodes by {@link Selector} and send real IkGui input to them, one run at a time; later runs
 * wait their turn. While a run drives, the window's own mouse and keyboard input is held off, so a
 * player's mouse doesn't fight it.
 *
 * <p>Runs belong to the plugin that started them and are cancelled when it unloads.
 */
@Slf4j
public final class UiAutomation {
    /** Posts runs to the render thread. */
    private final UiManager manager;

    /** Runs waiting their turn. Render thread only. */
    private final Deque<Run> waiting = new ArrayDeque<>();

    /** The run driving input, or null. Render thread only. */
    private Run current;

    /** Runs that finished this frame, completed once the actions they fired have run. */
    private final List<Run> finished = new ArrayList<>();

    /** Runs started and not finished yet, so input is held off from the moment one starts. */
    private final AtomicInteger unfinished = new AtomicInteger();

    /**
     * Create the automation for a UI manager, which owns it.
     *
     * @param manager The manager.
     */
    public UiAutomation(@NonNull UiManager manager) {
        this.manager = manager;
    }

    /**
     * Start a run. Safe from any thread; the steps start on a later frame, once earlier runs end.
     * Don't wait for the result on the render thread, which is the thread that runs the steps.
     *
     * @param owner The plugin starting the run, which owns it.
     * @param build Adds the steps.
     * @return Finishes when the run does, or fails with a {@link UiAutomationException}.
     */
    public CompletableFuture<Void> run(
            @NonNull GraphicsContext owner, @NonNull Consumer<Steps> build) {
        Steps steps = new Steps();
        build.accept(steps);
        return run(owner, steps);
    }

    /**
     * Start a run. Safe from any thread; the steps start on a later frame, once earlier runs end.
     *
     * @param owner The plugin starting the run, which owns it.
     * @param steps The steps.
     * @return Finishes when the run does, or fails with a {@link UiAutomationException}.
     */
    public CompletableFuture<Void> run(@NonNull GraphicsContext owner, @NonNull Steps steps) {
        final Run run = new Run(owner, steps.steps());
        // Counted now, so input a player sends from here on can't slip in before the first step
        unfinished.incrementAndGet();
        run.getFuture().whenComplete((done, error) -> unfinished.decrementAndGet());
        manager.post(
                () -> {
                    if (owner.isClosed()) {
                        run.cancel(owner.getOwner() + " has unloaded");
                    } else {
                        waiting.add(run);
                    }
                });
        return run.getFuture();
    }

    /**
     * Whether a run is driving input, so the window's own input should be held off. Safe from any
     * thread.
     *
     * @return True from when a run is started until it finishes.
     */
    public boolean isDriving() {
        return unfinished.get() > 0;
    }

    /**
     * Advance the current run, after the surfaces were laid out and submitted. Called by the UI
     * manager on the render thread, every frame.
     *
     * @param shown The surfaces shown this frame.
     */
    public void afterDraw(@NonNull List<Surface> shown) {
        while (current == null && !waiting.isEmpty()) {
            Run next = waiting.poll();
            if (!next.getFuture().isDone()) {
                current = next;
            }
        }
        if (current == null) {
            return;
        }
        if (current.advance(shown)) {
            finished.add(current);
            // Leave nothing held down for the player or the next run
            IkGui.getIO().addMouseButtonEvent(MouseButton.LEFT, false);
            current = null;
        }
    }

    /**
     * Complete the runs that finished this frame, after the actions their input fired have run.
     * Called by the UI manager on the render thread, every frame.
     */
    public void afterEvents() {
        if (finished.isEmpty()) {
            return;
        }
        List<Run> done = new ArrayList<>(finished);
        finished.clear();
        for (Run run : done) {
            run.complete();
        }
    }

    /**
     * Cancel every run a plugin started. Called by the UI manager on the render thread when the
     * plugin unloads.
     *
     * @param owner The plugin's context.
     */
    public void cancelAllOwnedBy(@NonNull GraphicsContext owner) {
        String reason = owner.getOwner() + " has unloaded";
        waiting.removeIf(
                run -> {
                    if (run.getOwner() == owner) {
                        run.cancel(reason);
                        return true;
                    }
                    return false;
                });
        if (current != null && current.getOwner() == owner) {
            log.debug("Cancelled the UI automation run from {}", owner.getOwner());
            current.cancel(reason);
            IkGui.getIO().addMouseButtonEvent(MouseButton.LEFT, false);
            current = null;
        }
    }
}
