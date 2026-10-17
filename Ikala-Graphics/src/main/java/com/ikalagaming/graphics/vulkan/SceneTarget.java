package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

/**
 * Where the scene is drawn this frame: the size to draw it at, which is the scene window's size,
 * and the window's presentation it is copied into at the end.
 *
 * @param width The width to draw the scene at, in pixels. The render targets can be bigger.
 * @param height The height to draw the scene at, in pixels.
 * @param presentation The scene window's presentation, with its swapchain image acquired.
 */
public record SceneTarget(int width, int height, @NonNull Presentation presentation) {}
