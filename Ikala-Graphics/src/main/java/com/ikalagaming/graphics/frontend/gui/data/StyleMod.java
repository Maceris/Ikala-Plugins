package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;

import lombok.NonNull;

/**
 * Create a new style mod. All values are stored as floats.
 *
 * @param type The type of style variable being modified
 * @param x The first variable.
 * @param y The second variable, 0 by convention if unused.
 */
public record StyleMod(@NonNull StyleVariable type, float x, float y) {}
