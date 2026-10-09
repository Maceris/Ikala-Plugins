package com.ikalagaming.graphics.ui.style;

import lombok.NonNull;

/**
 * A reference to a theme token, used in place of a value so many styles can share it. Written
 * {@code $name} in theme files.
 *
 * @param name The token name.
 */
public record Token(@NonNull String name) {
    @Override
    public String toString() {
        return "$" + name;
    }
}
