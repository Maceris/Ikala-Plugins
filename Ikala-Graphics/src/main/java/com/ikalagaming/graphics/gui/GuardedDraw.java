package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.ErrorRecoveryState;
import com.ikalagaming.graphics.gui.data.IkIO;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a plugin's IkGui drawing so that an exception from it can't end the frame, or the render
 * thread with it. What threw is left half drawn: IkGui's windows, IDs and style pushes are unwound
 * to where they were before it started, the error is logged, and the caller stops drawing it.
 */
@Slf4j
public final class GuardedDraw {

    private GuardedDraw() {}

    /**
     * Run a plugin's drawing, recovering if it throws. Render thread only, between IkGui's new
     * frame and render.
     *
     * @param plugin The plugin that owns what is drawn, for the log.
     * @param kind What is drawn, like "Surface", for the log.
     * @param name Its name, for the log.
     * @param draw The drawing.
     * @return True if it finished, false if it threw, which the caller should stop drawing.
     */
    public static boolean run(
            @NonNull String plugin,
            @NonNull String kind,
            @NonNull String name,
            @NonNull Runnable draw) {
        final ErrorRecoveryState before = new ErrorRecoveryState();
        IkGuiInternal.errorRecoveryStoreState(before);
        try {
            draw.run();
            return true;
        } catch (RuntimeException e) {
            recover(before);
            log.error(
                    "{} {} from {} threw while drawing, and is no longer drawn",
                    kind,
                    name,
                    plugin,
                    e);
            return false;
        }
    }

    /**
     * Unwind IkGui to a stored state, quietly: each call left open is a consequence of the
     * exception, which is logged on its own.
     *
     * @param state The state to unwind to.
     */
    private static void recover(@NonNull ErrorRecoveryState state) {
        final IkIO io = IkGui.getIO();
        final boolean assertion = io.configErrorRecoveryEnableAssert;
        final boolean debugLog = io.configErrorRecoveryEnableDebugLog;
        final boolean tooltip = io.configErrorRecoveryEnableTooltip;
        io.configErrorRecoveryEnableAssert = false;
        io.configErrorRecoveryEnableDebugLog = false;
        io.configErrorRecoveryEnableTooltip = false;
        try {
            IkGuiInternal.errorRecoveryTryToRecoverState(state);
        } finally {
            io.configErrorRecoveryEnableAssert = assertion;
            io.configErrorRecoveryEnableDebugLog = debugLog;
            io.configErrorRecoveryEnableTooltip = tooltip;
        }
    }
}
