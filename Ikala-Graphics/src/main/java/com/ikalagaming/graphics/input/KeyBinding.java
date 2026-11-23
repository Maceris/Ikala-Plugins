package com.ikalagaming.graphics.input;

import com.ikalagaming.graphics.gui.enums.Key;

import lombok.NonNull;

import java.util.Locale;

/**
 * A key or mouse button, with the modifiers that must be held with it. A binding with no modifiers
 * matches whatever modifiers are held, so sprinting with Ctrl doesn't stop W from walking.
 *
 * <p>Written as text like {@code "w"}, {@code "f3"}, {@code "mouse_right"} or {@code
 * "ctrl+shift+f3"}: the {@link Key} name in lower case, after any of {@code ctrl+}, {@code shift+}
 * and {@code alt+}. {@code "none"} is unbound.
 *
 * @param key The key, or {@link Key#NONE} for unbound.
 * @param ctrl Whether Ctrl must be held.
 * @param shift Whether Shift must be held.
 * @param alt Whether Alt must be held.
 */
public record KeyBinding(@NonNull Key key, boolean ctrl, boolean shift, boolean alt) {

    /** Bound to nothing. */
    public static final KeyBinding NONE = new KeyBinding(Key.NONE, false, false, false);

    /** The text for {@link #NONE}. */
    public static final String NONE_TEXT = "none";

    private static final String CTRL = "ctrl";
    private static final String SHIFT = "shift";
    private static final String ALT = "alt";
    private static final String SEPARATOR = "+";

    /**
     * Check the key is one that can be bound.
     *
     * @param key The key.
     * @param ctrl Whether Ctrl must be held.
     * @param shift Whether Shift must be held.
     * @param alt Whether Alt must be held.
     * @throws IllegalArgumentException If the key is a merged modifier key or a gamepad key, or if
     *     an unbound binding has modifiers.
     */
    public KeyBinding {
        if (key.isModKey() || key.isGamepadKey()) {
            throw new IllegalArgumentException(key + " can't be bound");
        }
        if (key == Key.NONE && (ctrl || shift || alt)) {
            throw new IllegalArgumentException("An unbound binding has no modifiers");
        }
    }

    /**
     * A key with no modifiers.
     *
     * @param key The key.
     * @return The binding.
     */
    public static KeyBinding of(@NonNull Key key) {
        return new KeyBinding(key, false, false, false);
    }

    /**
     * Read a binding from its text.
     *
     * @param text The text, like {@code "ctrl+f3"}, in any case.
     * @return The binding.
     * @throws IllegalArgumentException If the text names no key, or a modifier twice.
     */
    public static KeyBinding parse(@NonNull String text) {
        final String[] parts = text.trim().toLowerCase(Locale.ROOT).split("\\+");
        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        for (int i = 0; i < parts.length - 1; ++i) {
            final String part = parts[i].trim();
            final boolean seen =
                    switch (part) {
                        case CTRL -> ctrl;
                        case SHIFT -> shift;
                        case ALT -> alt;
                        default ->
                                throw new IllegalArgumentException(
                                        "Unknown modifier " + part + " in " + text);
                    };
            if (seen) {
                throw new IllegalArgumentException("Modifier " + part + " twice in " + text);
            }
            ctrl |= CTRL.equals(part);
            shift |= SHIFT.equals(part);
            alt |= ALT.equals(part);
        }
        final String keyName = parts[parts.length - 1].trim();
        if (NONE_TEXT.equals(keyName)) {
            return new KeyBinding(Key.NONE, ctrl, shift, alt);
        }
        final Key key;
        try {
            key = Key.valueOf(keyName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown key " + keyName + " in " + text, e);
        }
        return new KeyBinding(key, ctrl, shift, alt);
    }

    /**
     * Whether this is bound to nothing.
     *
     * @return True if unbound.
     */
    public boolean isNone() {
        return key == Key.NONE;
    }

    /**
     * How many modifiers this needs.
     *
     * @return From 0 to 3.
     */
    public int modifierCount() {
        return (ctrl ? 1 : 0) + (shift ? 1 : 0) + (alt ? 1 : 0);
    }

    /**
     * Whether this binding is held in a key state.
     *
     * @param state The keys held.
     * @return True if the key and every modifier this needs are down.
     */
    public boolean isHeld(@NonNull KeyState state) {
        return !isNone()
                && state.isDown(key)
                && (!ctrl || state.isDown(Key.MOD_CTRL))
                && (!shift || state.isDown(Key.MOD_SHIFT))
                && (!alt || state.isDown(Key.MOD_ALT));
    }

    /**
     * The binding as text to show a player, like {@code "Ctrl+F3"} or {@code "Mouse Right"}.
     *
     * @return The text, or blank when unbound.
     */
    public String displayName() {
        if (isNone()) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        if (ctrl) {
            text.append("Ctrl+");
        }
        if (shift) {
            text.append("Shift+");
        }
        if (alt) {
            text.append("Alt+");
        }
        final String[] words = key.name().toLowerCase(Locale.ROOT).split("_");
        for (int i = 0; i < words.length; ++i) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return text.toString();
    }

    /**
     * The binding as text that {@link #parse} reads back.
     *
     * @return The text.
     */
    @Override
    public String toString() {
        if (isNone()) {
            return NONE_TEXT;
        }
        final StringBuilder text = new StringBuilder();
        if (ctrl) {
            text.append(CTRL).append(SEPARATOR);
        }
        if (shift) {
            text.append(SHIFT).append(SEPARATOR);
        }
        if (alt) {
            text.append(ALT).append(SEPARATOR);
        }
        return text.append(key.name().toLowerCase(Locale.ROOT)).toString();
    }
}
