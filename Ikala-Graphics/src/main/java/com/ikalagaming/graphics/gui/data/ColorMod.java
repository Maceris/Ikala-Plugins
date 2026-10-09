package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.ColorType;

import lombok.NonNull;

/**
 * An override of a color.
 *
 * @param type The type of color.
 * @param color The color, as RGBA.
 * @see com.ikalagaming.graphics.gui.util.Color
 */
public record ColorMod(@NonNull ColorType type, int color) {}
