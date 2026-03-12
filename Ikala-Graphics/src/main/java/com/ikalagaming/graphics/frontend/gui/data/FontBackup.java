package com.ikalagaming.graphics.frontend.gui.data;

/**
 * What the font was before it was modified.
 *
 * @param name The name of the font. Could be null if there was no font specified.
 * @param size The size of the font.
 */
public record FontBackup(String name, int size) {}
