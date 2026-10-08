package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.PlatformIO;
import com.ikalagaming.graphics.frontend.gui.data.PlatformMonitor;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake platform and renderer backend for multiple viewports, which records what was requested.
 * This lets tests run headless with viewports enabled.
 */
class FakeViewportPlatform {
    /** The fake state of one platform window. */
    static class FakeWindow {
        final Vector2f position = new Vector2f();
        final Vector2f size = new Vector2f();
        boolean shown;
        boolean focused;
        String title;
        float alpha = 1.0f;
    }

    final Map<Viewport, FakeWindow> windows = new HashMap<>();
    final List<String> events = new ArrayList<>();
    final FakeWindow mainWindow = new FakeWindow();
    int renderCount;
    int swapCount;

    FakeViewportPlatform(Context context, float mainX, float mainY) {
        final PlatformIO io = context.platformIO;
        mainWindow.position.set(mainX, mainY);
        mainWindow.size.set(context.io.displaySize);
        mainWindow.shown = true;
        final Viewport mainViewport = context.mainViewport;
        mainViewport.platformHandle = mainWindow;
        windows.put(mainViewport, mainWindow);

        io.platformCreateWindow =
                viewport -> {
                    events.add("create " + viewport.getDebugName());
                    final FakeWindow window = new FakeWindow();
                    windows.put(viewport, window);
                    viewport.platformHandle = window;
                    viewport.platformUserData = window;
                };
        io.platformDestroyWindow =
                viewport -> {
                    events.add("destroy " + viewport.getDebugName());
                    if (viewport != context.mainViewport) {
                        windows.remove(viewport);
                    }
                    viewport.platformUserData = null;
                    viewport.platformHandle = null;
                };
        io.platformShowWindow = viewport -> windows.get(viewport).shown = true;
        io.platformSetWindowPos =
                (viewport, position) -> windows.get(viewport).position.set(position);
        io.platformGetWindowPos = viewport -> new Vector2f(windows.get(viewport).position);
        io.platformSetWindowSize = (viewport, size) -> windows.get(viewport).size.set(size);
        io.platformGetWindowSize = viewport -> new Vector2f(windows.get(viewport).size);
        io.platformGetWindowFocus = viewport -> windows.get(viewport).focused;
        io.platformSetWindowFocus =
                viewport -> {
                    windows.values().forEach(window -> window.focused = false);
                    windows.get(viewport).focused = true;
                };
        io.platformGetWindowMinimized = viewport -> false;
        io.platformSetWindowTitle = (viewport, title) -> windows.get(viewport).title = title;
        io.platformSetWindowAlpha = (viewport, alpha) -> windows.get(viewport).alpha = alpha;
        io.rendererRenderWindow = (viewport, argument) -> renderCount++;
        io.rendererSwapBuffers = (viewport, argument) -> swapCount++;

        // Two 1920x1080 monitors side by side, with a task bar at the bottom of the first
        final PlatformMonitor first = new PlatformMonitor();
        first.mainSize.set(1920, 1080);
        first.workSize.set(1920, 1040);
        final PlatformMonitor second = new PlatformMonitor();
        second.mainPosition.set(1920, 0);
        second.mainSize.set(1920, 1080);
        second.workPosition.set(1920, 0);
        second.workSize.set(1920, 1080);
        io.monitors.add(first);
        io.monitors.add(second);

        context.io.backendFlags |=
                BackendFlags.PLATFORM_HAS_VIEWPORTS | BackendFlags.RENDERER_HAS_VIEWPORTS;
    }

    FakeWindow get(Viewport viewport) {
        return windows.get(viewport);
    }

    /**
     * Give a platform window the OS focus, like a user clicking on it.
     *
     * @param viewport The viewport of the platform window.
     */
    void focus(Viewport viewport) {
        windows.values().forEach(window -> window.focused = false);
        windows.get(viewport).focused = true;
    }
}
