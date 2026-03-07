package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ColorButtonPosition {
    LEFT(0),
    RIGHT(1);

    private final int intValue;

    public static ColorButtonPosition fromInteger(int value) {
        for (var position : ColorButtonPosition.values()) {
            if (position.intValue == value) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unexpected integer value " + value);
    }
}
