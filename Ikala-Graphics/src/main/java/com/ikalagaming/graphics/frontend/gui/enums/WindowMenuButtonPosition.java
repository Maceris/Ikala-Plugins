package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WindowMenuButtonPosition {
    NONE(0),
    LEFT(1),
    RIGHT(2);

    private final int intValue;

    public static WindowMenuButtonPosition fromInteger(int value) {
        for (var position : WindowMenuButtonPosition.values()) {
            if (position.intValue == value) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unexpected integer value " + value);
    }
}
