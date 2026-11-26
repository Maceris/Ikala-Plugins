package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * One entry of a node's context menu.
 *
 * @param label What the menu shows.
 * @param action What choosing it does, run on the render thread outside drawing.
 */
public record MenuItem(@NonNull String label, @NonNull Runnable action) {}
