package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;

import lombok.Getter;
import lombok.Setter;

/**
 * A component that we can draw on the screen, can handle input, and can be enabled/disabled.
 * Intended to be something complex and toggleable like a main menu or floating window, rather than
 * a button or list.
 */
@Setter
@Getter
public abstract class GuiComponent implements GuiInstance {
    public boolean visible;
}
