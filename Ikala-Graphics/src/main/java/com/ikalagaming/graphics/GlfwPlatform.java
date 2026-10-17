package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;

import com.ikalagaming.graphics.exceptions.WindowCreationException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.APIUtil;

import java.util.Map;

/**
 * GLFW itself, which lives for as long as the application rather than any one window. It must be
 * initialized before creating windows or the Vulkan instance, and terminated after both are gone,
 * all from the main thread.
 */
@Slf4j
public final class GlfwPlatform {

    /**
     * The width of the largest monitor we could find, in pixels.
     *
     * @return The width of the largest monitor mode.
     */
    @Getter private static int largestMonitorWidth = 0;

    /**
     * The height of the largest monitor we could find, in pixels.
     *
     * @return The height of the largest monitor mode.
     */
    @Getter private static int largestMonitorHeight = 0;

    /** Whether GLFW is initialized right now. */
    private static boolean initialized = false;

    /**
     * Initialize GLFW, log its errors, and find the largest monitor. Does nothing if it is already
     * initialized.
     *
     * @throws WindowCreationException If GLFW can't start, or can't find Vulkan or any monitors.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        glfwInitHint(GLFW_COCOA_CHDIR_RESOURCES, GLFW_TRUE);
        if (!glfwInit()) {
            final String error = "Unable to initialize GLFW";
            log.warn(error);
            throw new WindowCreationException(error);
        }
        initialized = true;

        GLFWErrorCallback previous = glfwSetErrorCallback(createErrorLogger());
        if (previous != null) {
            log.debug("We are replacing a GLFWErrorCallback, hope that's fine");
            previous.free();
        }

        if (!GLFWVulkan.glfwVulkanSupported()) {
            final String error = "GLFW cannot find the Vulkan loader";
            log.warn(error);
            terminate();
            throw new WindowCreationException(error);
        }

        findLargestMonitor();
    }

    /** Terminate GLFW and free the error callback. Every window must be destroyed first. */
    public static void terminate() {
        if (!initialized) {
            return;
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
        initialized = false;
    }

    /**
     * Whether GLFW is initialized right now.
     *
     * @return True between {@link #initialize()} and {@link #terminate()}.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Wait for input to any window, or until some time passes, whichever comes first. Callbacks run
     * for whatever input arrives, as they would when polling. Main thread.
     *
     * @param seconds The longest to wait.
     */
    public static void waitEvents(double seconds) {
        glfwWaitEventsTimeout(seconds);
    }

    /**
     * Find the biggest video mode of any monitor, which is as big as render targets ever need to
     * get.
     *
     * @throws WindowCreationException If there are no monitors.
     */
    private static void findLargestMonitor() {
        PointerBuffer monitors = glfwGetMonitors();
        if (monitors == null) {
            final String error = "Can't find monitor info";
            log.warn(error);
            throw new WindowCreationException(error);
        }
        int maxWidth = 0;
        int maxHeight = 0;
        int maxArea = 0;
        for (int i = 0; i < monitors.limit(); i++) {
            final GLFWVidMode.Buffer modes = glfwGetVideoModes(monitors.get(i));
            if (modes == null) {
                continue;
            }
            for (int j = 0; j < modes.limit(); j++) {
                final GLFWVidMode mode = modes.get(j);
                final int area = mode.width() * mode.height();
                if (area > maxArea) {
                    maxWidth = mode.width();
                    maxHeight = mode.height();
                    maxArea = area;
                }
            }
        }
        largestMonitorWidth = maxWidth;
        largestMonitorHeight = maxHeight;
        log.debug(
                "We found a monitor that is {}x{}, using that as the max size for rendering targets",
                maxWidth,
                maxHeight);
    }

    /**
     * Create the callback that logs GLFW errors, with their names and where they happened.
     *
     * @return The error callback.
     */
    private static GLFWErrorCallback createErrorLogger() {
        return new GLFWErrorCallback() {
            private final Map<Integer, String> ERROR_CODES =
                    APIUtil.apiClassTokens(
                            (field, value) -> 0x10000 < value && value < 0x20000, null, GLFW.class);

            @Override
            public void invoke(int error, long description) {
                String msg = getDescription(description);

                StringBuilder sb = new StringBuilder(512);
                sb.append("[GLFW] ")
                        .append(ERROR_CODES.get(error))
                        .append(" error\n")
                        .append("\tDescription : ")
                        .append(msg)
                        .append("\n")
                        .append("\tStacktrace  :\n");

                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                for (int i = 4; i < stack.length; i++) {
                    sb.append("\t\t");
                    sb.append(stack[i]);
                    sb.append("\n");
                }

                log.error(sb.toString());
            }
        };
    }

    /** Private constructor so this class is not initialized. */
    private GlfwPlatform() {}
}
