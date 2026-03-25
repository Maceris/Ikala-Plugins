package com.ikalagaming.graphics.frontend.gui.data;

/**
 * Tracks the location in a window's (or similar) canvas of items that care about mouse input.
 *
 * @param x The x coordinate of the upper-left corner.
 * @param y The y coordinate of the upper-left corner.
 * @param width The width of the item.
 * @param height The height of the item.
 * @param id The ID of the item.
 */
public record ClickableItem(float x, float y, float width, float height, int id) {}
