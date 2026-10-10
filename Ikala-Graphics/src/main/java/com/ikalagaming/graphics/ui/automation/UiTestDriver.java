package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.ui.UiManager;

import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Runs automation in a test, without a window or renderer. The test makes the IkGui context and the
 * UI manager; the driver pumps frames itself with fake time, a 60th of a second each, so runs are
 * deterministic and timeouts don't take real time.
 *
 * <pre>
 * driver.run(steps -&gt; steps.type("login//name", "Ada", true).waitForText("login//greeting", "Hi Ada"));
 * </pre>
 */
public final class UiTestDriver {
    /** Fake time per frame, in milliseconds. */
    public static final long FRAME_MILLIS = 16;

    /** The most frames one run may take: ten minutes of fake time. */
    private static final int MAX_FRAMES = (int) (10 * 60 * 1000 / FRAME_MILLIS);

    /** The UI being driven. */
    private final UiManager manager;

    /** Owns the runs. */
    private final GraphicsContext owner;

    /** The fake time, in milliseconds. */
    private long now = 1;

    /**
     * Create a driver for the current IkGui context, which from now on uses the driver's fake time.
     *
     * @param manager The UI being driven.
     * @param owner The plugin context that owns the runs.
     */
    public UiTestDriver(@NonNull UiManager manager, @NonNull GraphicsContext owner) {
        this.manager = manager;
        this.owner = owner;
        Context context = IkGui.getContext();
        context.timeSource = () -> now;
    }

    /** Draw one frame: start it, draw the UI, render, then run the actions it fired. */
    public void frame() {
        now += FRAME_MILLIS;
        IkGui.newFrame();
        manager.draw();
        IkGui.render();
        manager.dispatchEvents();
    }

    /**
     * Draw some frames.
     *
     * @param count The number of frames.
     */
    public void frames(int count) {
        for (int i = 0; i < count; ++i) {
            frame();
        }
    }

    /**
     * Run steps, drawing frames until they finish.
     *
     * @param build Adds the steps.
     * @throws UiAutomationException If a step fails.
     */
    public void run(@NonNull Consumer<Steps> build) {
        await(manager.getAutomation().run(owner, build));
    }

    /**
     * Draw frames until a run finishes.
     *
     * @param run The run, started through the UI manager's automation.
     * @throws UiAutomationException If a step fails.
     */
    public void await(@NonNull CompletableFuture<Void> run) {
        for (int i = 0; i < MAX_FRAMES && !run.isDone(); ++i) {
            frame();
        }
        if (!run.isDone()) {
            throw new UiAutomationException("The run didn't finish in " + MAX_FRAMES + " frames");
        }
        try {
            run.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof UiAutomationException failure) {
                throw failure;
            }
            throw e;
        }
    }
}
