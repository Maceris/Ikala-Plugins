package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;

import lombok.Getter;
import lombok.Setter;

public abstract class GuiComponent implements GuiInstance {
    @Getter @Setter public boolean visible;
}
