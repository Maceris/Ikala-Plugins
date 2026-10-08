package com.ikalagaming.graphics.frontend.gui.util;

import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;

import lombok.NonNull;

/**
 * A key chord is a key combined with zero or more modifiers, like Ctrl+S, packed into an int. This
 * takes the place of Dear ImGui's ImGuiKeyChord, which ORs ImGuiMod_XXX flags with a key.
 *
 * <p>A chord can also be just a single modifier with no key (Key.NONE), like Ctrl.
 */
public class KeyChord {
    /** How far the modifier flags are shifted within the chord. */
    private static final int MOD_SHIFT = 16;

    /** The mask for the key within the chord. */
    private static final int KEY_MASK = (1 << MOD_SHIFT) - 1;

    /** A chord with no key and no modifiers. */
    public static final int NONE = 0;

    /**
     * Create a chord from a key with no modifiers.
     *
     * @param key The key.
     * @return The chord.
     */
    public static int of(@NonNull Key key) {
        return key.ordinal();
    }

    /**
     * Create a chord from modifiers and a key.
     *
     * @param keyMods The modifiers.
     * @param key The key, Key.NONE for just the modifiers.
     * @return The chord.
     * @see KeyModFlags
     */
    public static int of(int keyMods, @NonNull Key key) {
        return (keyMods << MOD_SHIFT) | key.ordinal();
    }

    /**
     * Create a chord from just modifiers.
     *
     * @param keyMods The modifiers.
     * @return The chord.
     * @see KeyModFlags
     */
    public static int ofMods(int keyMods) {
        return keyMods << MOD_SHIFT;
    }

    /**
     * Fetch the key of a chord.
     *
     * @param chord The chord.
     * @return The key, which may be Key.NONE.
     */
    public static Key getKey(int chord) {
        return Key.values()[chord & KEY_MASK];
    }

    /**
     * Fetch the modifiers of a chord.
     *
     * @param chord The chord.
     * @return The modifiers.
     * @see KeyModFlags
     */
    public static int getMods(int chord) {
        return chord >>> MOD_SHIFT;
    }

    /**
     * Replace the key of a chord, keeping the modifiers.
     *
     * @param chord The chord.
     * @param key The new key.
     * @return The new chord.
     */
    public static int withKey(int chord, @NonNull Key key) {
        return (chord & ~KEY_MASK) | key.ordinal();
    }

    /**
     * Add the modifier for left/right modifier keys, e.g. LEFT_CTRL becomes Ctrl+LEFT_CTRL. This is
     * required so that the chord matches the modifier state while the key is held.
     *
     * @param chord The chord.
     * @return The fixed up chord.
     */
    public static int fixup(int chord) {
        final int extraMod =
                switch (getKey(chord)) {
                    case LEFT_CTRL, RIGHT_CTRL -> KeyModFlags.CTRL;
                    case LEFT_SHIFT, RIGHT_SHIFT -> KeyModFlags.SHIFT;
                    case LEFT_ALT, RIGHT_ALT -> KeyModFlags.ALT;
                    case LEFT_SUPER, RIGHT_SUPER -> KeyModFlags.SUPER;
                    default -> KeyModFlags.NONE;
                };
        return chord | ofMods(extraMod);
    }

    /**
     * Convert a single modifier flag to the key that stores its state.
     *
     * @param keyMod A single modifier flag.
     * @return The modifier key, or Key.NONE if it is not exactly one modifier.
     */
    public static Key modToKey(int keyMod) {
        return switch (keyMod) {
            case KeyModFlags.CTRL -> Key.MOD_CTRL;
            case KeyModFlags.SHIFT -> Key.MOD_SHIFT;
            case KeyModFlags.ALT -> Key.MOD_ALT;
            case KeyModFlags.SUPER -> Key.MOD_SUPER;
            default -> Key.NONE;
        };
    }

    /**
     * Create a human readable name for a chord, like "Ctrl+Shift+S".
     *
     * @param chord The chord.
     * @return The name.
     */
    public static String getName(int chord) {
        chord = fixup(chord);
        // Return "Ctrl+LeftShift" instead of "Ctrl+Shift+LeftShift"
        final int lrMod =
                switch (getKey(chord)) {
                    case LEFT_CTRL, RIGHT_CTRL -> KeyModFlags.CTRL;
                    case LEFT_SHIFT, RIGHT_SHIFT -> KeyModFlags.SHIFT;
                    case LEFT_ALT, RIGHT_ALT -> KeyModFlags.ALT;
                    case LEFT_SUPER, RIGHT_SUPER -> KeyModFlags.SUPER;
                    default -> KeyModFlags.NONE;
                };
        final int mods = getMods(chord) & ~lrMod;
        final StringBuilder result = new StringBuilder();
        if ((mods & KeyModFlags.CTRL) != 0) {
            result.append("Ctrl+");
        }
        if ((mods & KeyModFlags.SHIFT) != 0) {
            result.append("Shift+");
        }
        if ((mods & KeyModFlags.ALT) != 0) {
            result.append("Alt+");
        }
        if ((mods & KeyModFlags.SUPER) != 0) {
            result.append("Super+");
        }
        final Key key = getKey(chord);
        if (key != Key.NONE) {
            result.append(getKeyName(key));
        } else if (chord == NONE) {
            result.append(getKeyName(Key.NONE));
        } else if (!result.isEmpty()) {
            // Remove the trailing '+'
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    /**
     * Create a human readable name for a key.
     *
     * @param key The key.
     * @return The name.
     */
    public static String getKeyName(@NonNull Key key) {
        return switch (key) {
            case ZERO -> "0";
            case ONE -> "1";
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            default -> {
                // e.g. PAGE_DOWN becomes PageDown
                final StringBuilder name = new StringBuilder();
                for (String part : key.name().split("_")) {
                    if (part.length() == 1) {
                        name.append(part);
                    } else {
                        name.append(part.charAt(0)).append(part.substring(1).toLowerCase());
                    }
                }
                yield name.toString();
            }
        };
    }

    /** Private constructor so this is not instantiated. */
    private KeyChord() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
