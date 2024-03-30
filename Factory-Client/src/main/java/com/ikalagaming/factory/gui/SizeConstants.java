package com.ikalagaming.factory.gui;

/**
 * Defines for UI sizing.
 *
 * @param buttonWidth The width of a button.
 * @param buttonHeight The height of a button.
 * @param padding Padding between elements.
 */
public record SizeConstants(int buttonWidth, int buttonHeight, int padding) {
    public static final SizeConstants MEDIUM = new SizeConstants(300, 100, 10);
}
