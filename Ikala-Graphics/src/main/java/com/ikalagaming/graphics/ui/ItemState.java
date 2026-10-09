package com.ikalagaming.graphics.ui;

/**
 * The interaction state of a {@link CustomItem} this frame.
 *
 * @param hovered Whether the mouse is over the item.
 * @param held Whether the item is being held down, by the mouse or a navigation input.
 * @param focused Whether the item has keyboard or gamepad navigation focus.
 * @param pressed Whether the item was activated this frame.
 */
public record ItemState(boolean hovered, boolean held, boolean focused, boolean pressed) {}
