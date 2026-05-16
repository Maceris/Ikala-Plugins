package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Axis {
    NONE(-1),
    X(0),
    Y(1);

    public final int intValue;
}
