package com.ikalagaming.graphics.frontend.gui;

import static com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData.KEY_OWNER_ANY;
import static com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData.KEY_OWNER_NO_OWNER;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.FocusScopeData;
import com.ikalagaming.graphics.frontend.gui.data.KeyOwnerData;
import com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextItemFlags;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;

/**
 * Key ownership and shortcut routing.
 *
 * <p>Key ownership lets an item claim a key, so that other code checking the key with a different
 * owner ID does not see it. Shortcut routing decides which of several shortcut() calls for the same
 * key chord receives it, based on focus and the active item.
 *
 * <p>Owner IDs can be {@link
 * com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData#KEY_OWNER_ANY} to accept any owner, or
 * {@link com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData#KEY_OWNER_NO_OWNER} to require
 * that the key has no owner.
 */
@Slf4j
class IkGuiImplKeys {
    static Context context;

    /**
     * Update key owners and routing at the start of the frame, after key states have been updated.
     */
    static void updateKeyOwnersAndRouting() {
        final int keyMods = context.io.keyMods;
        if (keyMods != context.lastKeyModsFrame) {
            context.lastKeyModsChangeTime = context.time;
            if (context.lastKeyModsFrame == KeyModFlags.NONE) {
                context.lastKeyModsChangeFromNoneTime = context.time;
            }
        }
        context.lastKeyModsFrame = keyMods;

        for (Key key : Key.values()) {
            final int index = key.ordinal();
            if ((key.isKeyboardKey() || key.isModKey())
                    && context.io.keysDown[index]
                    && context.io.keysDownDurationPrevious[index] < 0) {
                context.lastKeyboardKeyPressTime = context.time;
                context.lastKeyboardKeyPressFrame = context.frameCount;
            }

            // Update the owner. Ownership is released on the frame after a release, so a mouse
            // down -> close window -> mouse up chain doesn't let someone else see the mouse up.
            final KeyOwnerData ownerData = context.keysOwnerData[index];
            final boolean down = context.io.keysDown[index];
            ownerData.ownerCurr = ownerData.ownerNext;
            if (!down) {
                ownerData.ownerNext = KEY_OWNER_NO_OWNER;
            }
            ownerData.lockUntilRelease = ownerData.lockUntilRelease && down;
            ownerData.lockThisFrame = ownerData.lockUntilRelease;
        }

        updateKeyRoutingTable();
    }

    /**
     * Strip old routing entries, and apply the result of the previous frame's routing requests to
     * key owners.
     */
    private static void updateKeyRoutingTable() {
        for (Key key : Key.values()) {
            final List<KeyRoutingData> entries = context.keysRoutingTable[key.ordinal()];
            for (Iterator<KeyRoutingData> it = entries.iterator(); it.hasNext(); ) {
                final KeyRoutingData entry = it.next();
                entry.routingCurrScore = entry.routingNextScore;
                entry.routingCurr = entry.routingNext;
                entry.routingNext = KEY_OWNER_NO_OWNER;
                entry.routingNextScore = 0;
                if (entry.routingCurr == KEY_OWNER_NO_OWNER) {
                    it.remove();
                    continue;
                }
                // Apply routing to the owner if there isn't already an owner
                if (entry.mods == context.io.keyMods) {
                    final KeyOwnerData ownerData = context.keysOwnerData[key.ordinal()];
                    if (ownerData.ownerCurr == KEY_OWNER_NO_OWNER) {
                        ownerData.ownerCurr = entry.routingCurr;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Key queries
    // ---------------------------------------------------------------------------------------

    /**
     * Calculate the delay and rate for repeating keys.
     *
     * @param inputFlags Input flags, which may contain a repeat rate.
     * @return The repeat delay and rate, in milliseconds.
     */
    static long[] getTypematicRepeatRate(int inputFlags) {
        final long delay = context.io.keyRepeatDelay;
        final long rate = context.io.keyRepeatRate;
        return switch (inputFlags & InputFlags.INTERNAL_REPEAT_RATE_MASK) {
            case InputFlags.INTERNAL_REPEAT_RATE_NAV_MOVE ->
                    new long[] {(long) (delay * 0.72f), (long) (rate * 0.80f)};
            case InputFlags.INTERNAL_REPEAT_RATE_NAV_TWEAK ->
                    new long[] {(long) (delay * 0.72f), (long) (rate * 0.30f)};
            default -> new long[] {delay, rate};
        };
    }

    /**
     * Check if a key is held down, if it is available to the owner.
     *
     * @param key The key.
     * @param ownerID The owner ID, KEY_OWNER_ANY to skip the owner test.
     * @return True if the key is down.
     */
    static boolean isKeyDown(@NonNull Key key, int ownerID) {
        if (!context.io.getKeyDown(key)) {
            return false;
        }
        return testKeyOwner(key, ownerID);
    }

    /**
     * Check if a key was pressed this frame, if it is available to the owner.
     *
     * @param key The key.
     * @param inputFlags Input flags, which may enable repeat.
     * @param ownerID The owner ID, KEY_OWNER_ANY to skip the owner test.
     * @return True if the key was pressed.
     */
    static boolean isKeyPressed(@NonNull Key key, int inputFlags, int ownerID) {
        if (!context.io.getKeyDown(key)) {
            return false;
        }
        final long t = context.io.getKeyDownDuration(key);
        if (t < 0) {
            return false;
        }
        if ((inputFlags & ~InputFlags.INTERNAL_SUPPORTED_BY_IS_KEY_PRESSED) != 0) {
            log.warn("Unsupported flags passed to isKeyPressed()");
        }
        // Setting any repeat option enables repeat
        if ((inputFlags
                        & (InputFlags.INTERNAL_REPEAT_RATE_MASK
                                | InputFlags.INTERNAL_REPEAT_UNTIL_MASK))
                != 0) {
            inputFlags |= InputFlags.REPEAT;
        }

        // Durations are in whole milliseconds, so they can stay at 0 for several fast frames.
        // Check whether the key went down this frame instead.
        boolean pressed = context.io.keysDownDurationPrevious[key.ordinal()] < 0;
        if (!pressed && (inputFlags & InputFlags.REPEAT) != 0) {
            final long[] repeat = getTypematicRepeatRate(inputFlags);
            pressed =
                    t > repeat[0]
                            && IkGuiImplUtils.getKeyPressedAmount(key, repeat[0], repeat[1]) > 0;
            if (pressed && (inputFlags & InputFlags.INTERNAL_REPEAT_UNTIL_MASK) != 0) {
                final double keyPressedTime = context.time - t;
                if ((inputFlags & InputFlags.INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE) != 0
                        && context.lastKeyModsChangeTime > keyPressedTime) {
                    pressed = false;
                }
                if ((inputFlags & InputFlags.INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE_FROM_NONE) != 0
                        && context.lastKeyModsChangeFromNoneTime > keyPressedTime) {
                    pressed = false;
                }
                if ((inputFlags & InputFlags.INTERNAL_REPEAT_UNTIL_OTHER_KEY_PRESS) != 0
                        && context.lastKeyboardKeyPressTime > keyPressedTime) {
                    pressed = false;
                }
            }
        }
        if (!pressed) {
            return false;
        }
        return testKeyOwner(key, ownerID);
    }

    /**
     * Check if a key was released this frame, if it is available to the owner.
     *
     * @param key The key.
     * @param ownerID The owner ID, KEY_OWNER_ANY to skip the owner test.
     * @return True if the key was released.
     */
    static boolean isKeyReleased(@NonNull Key key, int ownerID) {
        if (context.io.keysDownDurationPrevious[key.ordinal()] < 0 || context.io.getKeyDown(key)) {
            return false;
        }
        return testKeyOwner(key, ownerID);
    }

    /**
     * Check if a key chord was pressed, which requires exactly the modifiers of the chord.
     *
     * @param keyChord The key chord.
     * @param inputFlags Input flags, which may enable repeat.
     * @param ownerID The owner ID, KEY_OWNER_ANY to skip the owner test.
     * @return True if the chord was pressed.
     * @see KeyChord
     */
    static boolean isKeyChordPressed(int keyChord, int inputFlags, int ownerID) {
        keyChord = KeyChord.fixup(keyChord);
        final int mods = KeyChord.getMods(keyChord);
        if (context.io.keyMods != mods) {
            return false;
        }
        // Special storage location for mods
        Key key = KeyChord.getKey(keyChord);
        if (key == Key.NONE) {
            key = KeyChord.modToKey(mods);
        }
        if (key == Key.NONE) {
            return false;
        }
        return isKeyPressed(key, inputFlags & InputFlags.INTERNAL_REPEAT_MASK, ownerID);
    }

    // ---------------------------------------------------------------------------------------
    // Key ownership
    // ---------------------------------------------------------------------------------------

    /**
     * Fetch the owner of a key.
     *
     * @param key The key.
     * @return The owner ID, or KEY_OWNER_NO_OWNER.
     */
    static int getKeyOwner(@NonNull Key key) {
        if (key == Key.NONE) {
            return KEY_OWNER_NO_OWNER;
        }
        final int ownerID = context.keysOwnerData[key.ordinal()].ownerCurr;
        if (context.activeIDUsingAllKeyboardKeys
                && ownerID != context.activeID
                && ownerID != KEY_OWNER_ANY
                && key.isKeyboardKey()) {
            return KEY_OWNER_NO_OWNER;
        }
        return ownerID;
    }

    /**
     * Check if a key is available to an owner. KEY_OWNER_ANY skips the test, KEY_OWNER_NO_OWNER
     * requires that the key has no owner, otherwise the key must be unowned or owned by the ID. All
     * paths also check that the key is not locked.
     *
     * @param key The key.
     * @param ownerID The owner ID.
     * @return True if the key is available to the owner.
     */
    static boolean testKeyOwner(@NonNull Key key, int ownerID) {
        if (key == Key.NONE) {
            return true;
        }
        if (context.activeIDUsingAllKeyboardKeys
                && ownerID != context.activeID
                && ownerID != KEY_OWNER_ANY
                && key.isKeyboardKey()) {
            return false;
        }

        final KeyOwnerData ownerData = context.keysOwnerData[key.ordinal()];
        if (ownerID == KEY_OWNER_ANY) {
            return !ownerData.lockThisFrame;
        }

        if (ownerData.ownerCurr != ownerID) {
            if (ownerData.lockThisFrame) {
                return false;
            }
            if (ownerData.ownerCurr != KEY_OWNER_NO_OWNER) {
                return false;
            }
        }
        return true;
    }

    /**
     * Claim ownership of a key. Ownership is automatically released on the frame after the key is
     * released.
     *
     * @param key The key.
     * @param ownerID The owner ID, KEY_OWNER_NO_OWNER to clear the owner.
     * @param inputFlags INTERNAL_LOCK_THIS_FRAME or INTERNAL_LOCK_UNTIL_RELEASE to lock the key
     *     away from code that doesn't check ownership.
     */
    static void setKeyOwner(@NonNull Key key, int ownerID, int inputFlags) {
        if (key == Key.NONE) {
            return;
        }
        if (ownerID == KEY_OWNER_ANY
                && (inputFlags
                                & (InputFlags.INTERNAL_LOCK_THIS_FRAME
                                        | InputFlags.INTERNAL_LOCK_UNTIL_RELEASE))
                        == 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Can only use KEY_OWNER_ANY with lock flags when setting a key owner");
            return;
        }
        final KeyOwnerData ownerData = context.keysOwnerData[key.ordinal()];
        ownerData.ownerCurr = ownerData.ownerNext = ownerID;
        // We cannot lock by default as it would likely break lots of code
        ownerData.lockUntilRelease = (inputFlags & InputFlags.INTERNAL_LOCK_UNTIL_RELEASE) != 0;
        ownerData.lockThisFrame =
                (inputFlags & InputFlags.INTERNAL_LOCK_THIS_FRAME) != 0
                        || ownerData.lockUntilRelease;
    }

    /**
     * Claim ownership of every key in a chord, including the modifiers.
     *
     * @param keyChord The key chord.
     * @param ownerID The owner ID.
     * @param inputFlags Lock flags.
     */
    static void setKeyOwnersForKeyChord(int keyChord, int ownerID, int inputFlags) {
        final int mods = KeyChord.getMods(keyChord);
        if ((mods & KeyModFlags.CTRL) != 0) {
            setKeyOwner(Key.MOD_CTRL, ownerID, inputFlags);
        }
        if ((mods & KeyModFlags.SHIFT) != 0) {
            setKeyOwner(Key.MOD_SHIFT, ownerID, inputFlags);
        }
        if ((mods & KeyModFlags.ALT) != 0) {
            setKeyOwner(Key.MOD_ALT, ownerID, inputFlags);
        }
        if ((mods & KeyModFlags.SUPER) != 0) {
            setKeyOwner(Key.MOD_SUPER, ownerID, inputFlags);
        }
        final Key key = KeyChord.getKey(keyChord);
        if (key != Key.NONE) {
            setKeyOwner(key, ownerID, inputFlags);
        }
    }

    /**
     * Claim ownership of a key for the last item, if it is hovered or active.
     *
     * @param key The key.
     * @param inputFlags Condition and lock flags.
     * @return True if ownership was set.
     */
    static boolean setItemKeyOwner(@NonNull Key key, int inputFlags) {
        final int id = context.lastItemData.id;
        if (id == 0 || (context.hoveredID != id && context.activeID != id)) {
            return false;
        }
        if ((inputFlags & InputFlags.INTERNAL_COND_MASK) == 0) {
            inputFlags |= InputFlags.INTERNAL_COND_DEFAULT;
        }
        if ((context.hoveredID == id && (inputFlags & InputFlags.INTERNAL_COND_HOVERED) != 0)
                || (context.activeID == id
                        && (inputFlags & InputFlags.INTERNAL_COND_ACTIVE) != 0)) {
            if (!testKeyOwner(key, id)) {
                return false;
            }
            setKeyOwner(key, id, inputFlags & ~InputFlags.INTERNAL_COND_MASK);
            return true;
        }
        return false;
    }

    /**
     * Whether the active item is using a navigation direction, which stops navigation from using
     * it.
     *
     * @param direction The direction.
     * @return True if the active item is using the direction.
     */
    static boolean isActiveIDUsingNavDir(@NonNull Direction direction) {
        return (context.activeIDUsingNavDirMask & (1 << direction.ordinal())) != 0;
    }

    /** Declare that the active item is using all the keyboard keys. */
    static void setActiveIDUsingAllKeyboardKeys() {
        context.activeIDUsingAllKeyboardKeys = true;
        // Navigation also uses the keyboard
        context.activeIDUsingNavDirMask =
                (1 << Direction.LEFT.ordinal())
                        | (1 << Direction.RIGHT.ordinal())
                        | (1 << Direction.UP.ordinal())
                        | (1 << Direction.DOWN.ordinal());
        IkGuiImplNav.navMoveRequestCancel();
    }

    // ---------------------------------------------------------------------------------------
    // Shortcut routing
    // ---------------------------------------------------------------------------------------

    /**
     * Calculate the routing score for a request. Higher scores win.
     *
     * <ul>
     *   <li>0: never route
     *   <li>1: ROUTE_GLOBAL (lower priority)
     *   <li>100..199: ROUTE_FOCUSED (if the window is in the focus stack)
     *   <li>200: ROUTE_GLOBAL | ROUTE_OVER_FOCUSED
     *   <li>300: ROUTE_ACTIVE or ROUTE_FOCUSED (if the item is active)
     *   <li>400: ROUTE_GLOBAL | ROUTE_OVER_ACTIVE
     *   <li>500..599: ROUTE_FOCUSED | ROUTE_OVER_ACTIVE (if the window is in the focus stack)
     * </ul>
     *
     * @param focusScopeID The focus scope the request is made from.
     * @param ownerID The owner ID.
     * @param inputFlags The routing flags.
     * @return The score.
     */
    private static int calcRoutingScore(int focusScopeID, int ownerID, int inputFlags) {
        if ((inputFlags & InputFlags.ROUTE_FOCUSED) != 0) {
            // The active ID gets high priority
            if (ownerID != 0 && context.activeID == ownerID) {
                return 300;
            }
            // Score based on the distance to the focused window (lower is better)
            if (focusScopeID == 0) {
                return 0;
            }
            for (int i = 0; i < context.navFocusRoute.size(); ++i) {
                if (context.navFocusRoute.get(i).id == focusScopeID) {
                    if ((inputFlags & InputFlags.ROUTE_OVER_ACTIVE) != 0) {
                        return 599 - i;
                    }
                    return 199 - i;
                }
            }
            return 0;
        } else if ((inputFlags & InputFlags.ROUTE_ACTIVE) != 0) {
            if (ownerID != 0 && context.activeID == ownerID) {
                return 300;
            }
            return 0;
        } else if ((inputFlags & InputFlags.ROUTE_GLOBAL) != 0) {
            if ((inputFlags & InputFlags.ROUTE_OVER_ACTIVE) != 0) {
                return 400;
            }
            if (ownerID != 0 && context.activeID == ownerID) {
                return 300;
            }
            if ((inputFlags & InputFlags.ROUTE_OVER_FOCUSED) != 0) {
                return 200;
            }
            return 1;
        }
        return 0;
    }

    /**
     * Whether a key chord could produce character input, in which case it should not be considered
     * a shortcut while a text input is active.
     *
     * @param keyChord The chord.
     * @return True if the chord may produce a character.
     */
    private static boolean isKeyChordPotentiallyCharInput(int keyChord) {
        // When the right mods are pressed it cannot be a char input
        final int mods = KeyChord.getMods(keyChord);
        final boolean ignoreCharInputs =
                ((mods & KeyModFlags.CTRL) != 0 && (mods & KeyModFlags.ALT) == 0)
                        || (context.io.configMacOSXBehaviors && (mods & KeyModFlags.CTRL) != 0);
        if (ignoreCharInputs) {
            return false;
        }
        final Key key = KeyChord.getKey(keyChord);
        if (key == Key.NONE) {
            return false;
        }
        return context.keysMayBeCharInput[key.ordinal()];
    }

    /**
     * Find the routing data for a chord, creating it if needed.
     *
     * @param keyChord The chord.
     * @return The routing data, or null if the chord is invalid.
     */
    private static KeyRoutingData getShortcutRoutingData(int keyChord) {
        Key key = KeyChord.getKey(keyChord);
        final int mods = KeyChord.getMods(keyChord);
        if (key == Key.NONE) {
            key = KeyChord.modToKey(mods);
        }
        if (key == Key.NONE) {
            IkGuiImplDebugTools.reportError(log, "Shortcuts need a key, or exactly one modifier");
            return null;
        }
        final List<KeyRoutingData> entries = context.keysRoutingTable[key.ordinal()];
        for (KeyRoutingData entry : entries) {
            if (entry.mods == mods) {
                return entry;
            }
        }
        final KeyRoutingData entry = new KeyRoutingData(mods);
        entries.add(entry);
        return entry;
    }

    /**
     * Use the current focus scope as the routing ID if the owner is none or any.
     *
     * @param ownerID The owner ID.
     * @return The routing ID.
     */
    private static int getRoutingIDFromOwnerID(int ownerID) {
        return ownerID != KEY_OWNER_NO_OWNER && ownerID != KEY_OWNER_ANY
                ? ownerID
                : context.currentFocusScopeID;
    }

    /**
     * Request a route for a key chord. Routes are attributed at the beginning of the next frame,
     * based on the best score and modifier state.
     *
     * @param keyChord The key chord.
     * @param inputFlags Routing flags, the default is a global route over focused and active.
     * @param ownerID The owner ID, which can't be none or any.
     * @return True if the route is available this frame.
     */
    static boolean setShortcutRouting(int keyChord, int inputFlags, int ownerID) {
        if ((inputFlags & InputFlags.INTERNAL_ROUTE_TYPE_MASK) == 0) {
            // This is the default for setShortcutRouting() but not shortcut()
            inputFlags |=
                    InputFlags.ROUTE_GLOBAL
                            | InputFlags.ROUTE_OVER_FOCUSED
                            | InputFlags.ROUTE_OVER_ACTIVE;
        } else if (Integer.bitCount(inputFlags & InputFlags.INTERNAL_ROUTE_TYPE_MASK) != 1) {
            IkGuiImplDebugTools.reportError(log, "Only one routing type can be used for shortcuts");
        }
        if (ownerID == KEY_OWNER_ANY || ownerID == KEY_OWNER_NO_OWNER) {
            IkGuiImplDebugTools.reportError(log, "Shortcut routing requires a valid owner ID");
            return false;
        }

        keyChord = KeyChord.fixup(keyChord);

        if (IkGuiImplDebugTools.isDebugLogEnabled(DebugLogFlags.EVENT_INPUT_ROUTING)) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_INPUT_ROUTING,
                    "SetShortcutRouting(%s, flags=%04X, owner_id=0x%08X)",
                    KeyChord.getName(keyChord),
                    inputFlags,
                    ownerID);
        }

        // Debug break requested by the user
        if (context.debugBreakInShortcutRouting == keyChord) {
            IkGuiImplDebugTools.debugBreak(
                    "setShortcutRouting() for " + KeyChord.getName(keyChord));
        }

        if ((inputFlags & InputFlags.ROUTE_UNLESS_BG_FOCUSED) != 0
                && context.navFocusedWindow == null) {
            return false;
        }

        // Note that ROUTE_ALWAYS won't set routing and thus won't set an owner
        if ((inputFlags & InputFlags.ROUTE_ALWAYS) != 0) {
            return true;
        }

        // Specific culling when there's an active item
        if (context.activeID != 0 && context.activeID != ownerID) {
            if ((inputFlags & InputFlags.ROUTE_ACTIVE) != 0) {
                return false;
            }
            // Cull shortcuts with no modifiers when it could generate a character. e.g. pressing
            // G generates 'g', but Ctrl+G should still trigger while an input text is active.
            if (context.io.wantTextInput && isKeyChordPotentiallyCharInput(keyChord)) {
                return false;
            }
            // Using all keyboard keys trumps everything for the active ID
            if ((inputFlags & InputFlags.ROUTE_OVER_ACTIVE) == 0
                    && context.activeIDUsingAllKeyboardKeys) {
                Key key = KeyChord.getKey(keyChord);
                if (key == Key.NONE) {
                    key = KeyChord.modToKey(KeyChord.getMods(keyChord));
                }
                if (key.isKeyboardKey()) {
                    return false;
                }
            }
        }

        // Where do we evaluate the route for?
        int focusScopeID = context.currentFocusScopeID;
        if ((inputFlags & InputFlags.ROUTE_FROM_ROOT_WINDOW) != 0
                && context.windowCurrent != null) {
            focusScopeID = context.windowCurrent.rootWindow.id;
        }

        final int score = calcRoutingScore(focusScopeID, ownerID, inputFlags);
        if (score == 0) {
            return false;
        }

        // Submit routing for the next frame (assuming the score is sufficient)
        final KeyRoutingData routingData = getShortcutRoutingData(keyChord);
        if (routingData == null) {
            return false;
        }
        if (score > routingData.routingNextScore) {
            routingData.routingNext = ownerID;
            routingData.routingNextScore = score;
        }

        // Return the routing state for the current frame
        return routingData.routingCurr == ownerID;
    }

    /**
     * Check if a route is currently held by an owner, without requesting it.
     *
     * @param keyChord The key chord.
     * @param ownerID The owner ID.
     * @return True if the owner has the route this frame.
     */
    static boolean testShortcutRouting(int keyChord, int ownerID) {
        final int routingID = getRoutingIDFromOwnerID(ownerID);
        final KeyRoutingData routingData = getShortcutRoutingData(KeyChord.fixup(keyChord));
        return routingData != null && routingData.routingCurr == routingID;
    }

    /**
     * Check if a shortcut was pressed, with routing to decide who receives it. By default, the
     * route goes to the focused window (and its focused parents), and the active item has priority.
     *
     * @param keyChord The key chord, e.g. KeyChord.of(KeyModFlags.CTRL, Key.S).
     * @param inputFlags Input flags, for repeat and routing.
     * @param ownerID The owner ID, KEY_OWNER_ANY to use the current focus scope.
     * @return True if the shortcut was pressed and routed to us.
     * @see KeyChord
     * @see InputFlags
     */
    static boolean shortcut(int keyChord, int inputFlags, int ownerID) {
        if ((inputFlags & InputFlags.INTERNAL_ROUTE_TYPE_MASK) == 0) {
            inputFlags |= InputFlags.ROUTE_FOCUSED;
        }

        // Auto-assign an owner based on the current focus scope, which makes shortcut() always
        // input owner aware
        if (ownerID == KEY_OWNER_ANY || ownerID == KEY_OWNER_NO_OWNER) {
            ownerID = getRoutingIDFromOwnerID(ownerID);
        }

        if ((context.currentItemFlags & ItemFlags.DISABLED) != 0) {
            return false;
        }

        // Submit the route
        if (!setShortcutRouting(keyChord, inputFlags, ownerID)) {
            return false;
        }

        // Default repeat behavior, e.g. pressing Ctrl+W and releasing Ctrl while holding W will
        // not trigger the W shortcut
        if ((inputFlags & InputFlags.REPEAT) != 0
                && (inputFlags & InputFlags.INTERNAL_REPEAT_UNTIL_MASK) == 0) {
            inputFlags |= InputFlags.INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE;
        }

        if (!isKeyChordPressed(keyChord, inputFlags, ownerID)) {
            return false;
        }

        // Claim the mods during the press
        setKeyOwnersForKeyChord(KeyChord.ofMods(KeyChord.getMods(keyChord)), ownerID, 0);
        return true;
    }

    /**
     * Set a shortcut for the next item, which activates it when pressed.
     *
     * @param keyChord The key chord.
     * @param inputFlags Input flags, for repeat, routing, and showing a tooltip.
     */
    static void setNextItemShortcut(int keyChord, int inputFlags) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_SHORTCUT;
        context.nextItemData.shortcut = keyChord;
        context.nextItemData.shortcutFlags = inputFlags;
    }

    /**
     * Handle the shortcut set for the item by setNextItemShortcut(). Called from itemAdd().
     *
     * @param id The ID of the item.
     */
    static void itemHandleShortcut(int id) {
        final int inputFlags = context.nextItemData.shortcutFlags;
        if ((inputFlags & ~InputFlags.INTERNAL_SUPPORTED_BY_SET_NEXT_ITEM_SHORTCUT) != 0) {
            log.warn("Unsupported flags passed to setNextItemShortcut()");
        }
        if ((context.lastItemData.itemFlags & ItemFlags.DISABLED) != 0) {
            return;
        }
        if ((inputFlags & InputFlags.TOOLTIP) != 0) {
            context.lastItemData.statusFlags |= ItemStatusFlags.HAS_SHORTCUT;
            context.lastItemData.shortcut = context.nextItemData.shortcut;
        }
        if (!shortcut(
                        context.nextItemData.shortcut,
                        inputFlags & InputFlags.INTERNAL_SUPPORTED_BY_SHORTCUT,
                        id)
                || context.navActivateID != 0) {
            return;
        }

        // This will effectively disable clipping
        context.navActivateID = id;
        context.navActivateFlags =
                com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags.PREFER_INPUT
                        | com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags.FROM_SHORTCUT;
        context.navActivateDownID = context.navActivatePressedID = id;
        IkGuiImplNav.navHighlightActivated(id);
    }

    /**
     * Check whether a focus scope is in the route to the focused window.
     *
     * @param focusScopeID The focus scope ID.
     * @return True if the scope is in the focus route.
     */
    static boolean isInNavFocusRoute(int focusScopeID) {
        if (context.navFocusScopeID == focusScopeID) {
            return true;
        }
        for (FocusScopeData focusScope : context.navFocusRoute) {
            if (focusScope.id == focusScopeID) {
                return true;
            }
        }
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplKeys() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
