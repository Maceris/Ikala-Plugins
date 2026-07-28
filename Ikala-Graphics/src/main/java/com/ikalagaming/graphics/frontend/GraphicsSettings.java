package com.ikalagaming.graphics.frontend;

import lombok.NonNull;

public class GraphicsSettings {

    /** The general rendering quality. */
    public enum Quality {
        // TODO(ches) document specific technical details. ... Once we decide what they are.
        // NOTE(ches) doesn't do anything yet. Intended to do things like enable ray tracing or
        // crank down effects
        /** Intentionally lowered quality. */
        LOW,
        /** Regular quality. */
        MEDIUM,
        /** Extra quality. */
        HIGH,
    }

    /** Whether anti-aliasing is enabled. */
    public boolean antiAliasing = true;

    /** For OpenGL. Whether we want to use a compatible profile instead of the core one. */
    public boolean compatibleProfile = false;

    /**
     * The graphics quality. Should not be set directly once rendering is up and running, see {@link
     * com.ikalagaming.graphics.GraphicsManager#setQuality(Quality)}. Fine to set before initial
     * setup though.
     */
    @NonNull public Quality quality = Quality.MEDIUM;

    /** The height of the window in pixels. Only respected if greater than 0. */
    public int requestedWindowHeight = 0;

    /** The width of the window in pixels. Only respected if greater than 0. */
    public int requestedWindowWidth = 0;

    /**
     * The target frames per second. If zero (or negative), this will be interpreted as requesting
     * VSync and thus will be effectively whatever the monitor's refresh rate is.
     */
    public int targetFPS = 0;
}
