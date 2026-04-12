package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Slf4j
class IkGuiImplUtils {

    /**
     * Used to wrap a large frame count back to 0, which I like more than accidentally wrapping into
     * the negatives. We just need the numbers to be different each frame anyway, so this shouldn't
     * matter much.
     */
    private static final int FRAME_COUNT_CAP = 2_000_000_000;

    static Context context;

    public static int applyGlobalAlpha(int color, boolean disabled) {
        int result = color;
        result = Color.multiplyAlpha(result, context.style.variable.alpha);
        if (disabled) {
            result = Color.multiplyAlpha(result, context.style.variable.disabledAlpha);
        }
        return result;
    }

    public static void beginDisabled(boolean disabled) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static float calculateItemWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        // TODO(ches) complete this
    }

    public static void endDisabled() {
        // TODO(ches) complete this
    }

    public static void endFrame() {
        // TODO(ches) complete this

        if (context.frameCountEnded == context.frameCount) {
            log.error("newFrame() must be called before endFrame()");
            return;
        }

        if (!context.styleVariableStack.isEmpty()) {
            log.error("Did not pop off all style variables before ending frame");
            context.styleVariableStack.clear();
        }

        // TODO(ches) update navigation
        // TODO(ches) update docking
        // TODO(ches) update drag and drop
        // TODO(ches) end the frame
        IkGuiInternal.updateMouseMovingWindowEndFrame();

        context.backgroundDrawList.prepareForRender();
        context.foregroundDrawList.prepareForRender();
        // TODO(ches) update viewports list
        // TODO(ches) sort the window list
        // TODO(ches) check the window parents/children are sane
        // TODO(ches) update textures
        // TODO(ches) unlock the font atlas
        context.io.mousePositionPrevious.set(context.io.mousePosition);
        context.io.appFocusLost = false;
        context.io.mouseWheel = 0.0f;
        context.io.mouseWheelH = 0.0f;
        // TODO(ches) clear input queue?

        // TODO(ches) call context hooks

        context.frameCountEnded = (context.frameCountEnded + 1) % FRAME_COUNT_CAP;
    }

    public static void newFrame() {
        // TODO(ches) complete this

        // TODO(ches) sanity checks for IO and configuration
        // TODO(ches) update settings
        // Updating frame time and count
        final long lastFrameStart = context.frameStartTime;
        context.frameStartTime = System.currentTimeMillis();
        if (lastFrameStart > 0) {
            context.io.deltaTime = context.frameStartTime - lastFrameStart;
        }
        context.time += context.io.deltaTime;
        context.frameCount = (context.frameCount + 1) % FRAME_COUNT_CAP;
        // TODO(ches) update window counts

        // Updating input events
        context.io.clearFrameSpecificValues();
        context.io.processInputEvents();

        // TODO(ches) update viewports

        // Update draw lists
        context.drawData.clear();
        context.backgroundDrawList.clear();
        context.foregroundDrawList.clear();
        context.drawData.drawLists.add(context.backgroundDrawList);
        context.drawData.drawLists.add(context.foregroundDrawList);
        // TODO(ches) update active IDs
        // TODO(ches) update hover delay
        // TODO(ches) update keyboard inputs
        // TODO(ches) update drag and drop

        IkGuiInternal.updateHoveredWindowAndCaptureFlags(context.io.mousePosition);
        IkGuiInternal.updateMouseMovingWindowNewFrame();

        // TODO(ches) update navigation
        context.io.updateMouseInputs();
        // TODO(ches) clean up transient buffers
        context.windowDisplayOrder.clear();
        context.windowFocusOrder.clear();
        // TODO(ches) create fallback window
    }

    public static void render() {
        // TODO(ches) complete this

        if (context.frameCountEnded != context.frameCount) {
            IkGui.endFrame();
        }

        context.frameCountRendered = (context.frameCountRendered + 1) % FRAME_COUNT_CAP;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplUtils() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
