package com.ikalagaming.graphics.input;

import com.ikalagaming.graphics.gui.enums.Key;

import lombok.NonNull;

/** Which keys and mouse buttons are held, as {@link Controls} sees them. */
@FunctionalInterface
public interface KeyState {

    /** Nothing held. */
    KeyState NONE = key -> false;

    /**
     * Whether a key is held. Keyboard keys read as up while a text field has the keyboard, and
     * mouse keys while the GUI has the mouse.
     *
     * @param key A keyboard key, a mouse key, or one of the merged modifier keys.
     * @return True if it is down.
     */
    boolean isDown(@NonNull Key key);
}
