package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData;
import com.ikalagaming.graphics.frontend.gui.data.TypingSelectRequest;
import com.ikalagaming.graphics.frontend.gui.data.TypingSelectState;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.flags.InputFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TypingSelectFlags;

import lombok.NonNull;

import java.util.function.IntFunction;

/**
 * Typing-select support: typing the start of an item's name to jump to it, like in file browsers.
 *
 * <p>This would typically only be called on the focused window or location you want to grab inputs
 * for, e.g.
 *
 * <pre>{@code
 * if (IkGui.isWindowFocused()) {
 *     final TypingSelectRequest request = IkGuiInternal.getTypingSelectRequest();
 *     focusIndex = IkGuiInternal.typingSelectFindMatch(
 *             request, items.size(), index -> items.get(index).name, -1);
 * }
 * }</pre>
 */
class IkGuiImplTypingSelect {
    /** The shared context. */
    static Context context;

    /** How long after the last character the search buffer is cleared, in milliseconds. */
    private static final long TYPING_SELECT_RESET_TIMER = 1800;

    /** Lock single char matching when repeating the same character this many times. */
    private static final int TYPING_SELECT_SINGLE_CHAR_COUNT_FOR_LOCK = 4;

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTypingSelect() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Check if a key was pressed, with any owner.
     *
     * @param key The key.
     * @param repeat Whether to include repeats.
     * @return True if the key was pressed.
     */
    private static boolean isKeyPressed(@NonNull Key key, boolean repeat) {
        return IkGuiImplKeys.isKeyPressed(
                key, repeat ? InputFlags.REPEAT : InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * Consume character inputs and return a search request, if any. Calling this from multiple
     * locations is safe (e.g. to obtain the buffer), unless {@link
     * TypingSelectFlags#ALLOW_BACKSPACE} is used.
     *
     * @param flags The typing select flags.
     * @return The request, or null if nothing has been typed.
     * @see TypingSelectFlags
     */
    static TypingSelectRequest getTypingSelectRequest(int flags) {
        final TypingSelectState data = context.typingSelectState;
        final TypingSelectRequest request = data.request;
        final StringBuilder buffer = data.searchBuffer;

        // Clear the buffer
        if (!buffer.isEmpty()) {
            boolean clearBuffer = context.navFocusScopeID != data.focusScope;
            clearBuffer |= data.lastRequestTime + TYPING_SELECT_RESET_TIMER < context.time;
            clearBuffer |= context.navAnyRequest;
            // Allow temporary space activation to not interfere
            clearBuffer |= context.activeID != 0 && context.navActivateID == 0;
            clearBuffer |= isKeyPressed(Key.ESCAPE, true) || isKeyPressed(Key.ENTER, true);
            clearBuffer |=
                    isKeyPressed(Key.BACKSPACE, true)
                            && (flags & TypingSelectFlags.ALLOW_BACKSPACE) == 0;
            if (clearBuffer) {
                data.clear();
            }
        }

        // Append to the buffer
        boolean selectRequest = false;
        final String typed = context.io.inputQueueCharacters.toString();
        for (int i = 0; i < typed.length(); ) {
            final int codePoint = typed.codePointAt(i);
            i += Character.charCount(codePoint);
            final String character = new String(Character.toChars(codePoint));
            // Ignore control characters and leading blanks
            if (codePoint < 32
                    || (buffer.isEmpty() && Character.isWhitespace(codePoint))
                    || buffer.length() + character.length() > TypingSelectState.MAX_SEARCH_LENGTH) {
                continue;
            }
            if (data.singleCharModeLock
                    && character.length() == request.singleCharSize
                    && buffer.indexOf(character) == 0) {
                // Same character: we don't need to append to the buffer
                selectRequest = true;
                continue;
            }
            if (data.singleCharModeLock) {
                // Different character: clear
                data.clear();
            }
            buffer.append(character);
            selectRequest = true;
        }
        context.io.inputQueueCharacters.setLength(0);

        // Handle backspace
        if ((flags & TypingSelectFlags.ALLOW_BACKSPACE) != 0
                && !buffer.isEmpty()
                && isKeyPressed(Key.BACKSPACE, true)) {
            buffer.setLength(buffer.offsetByCodePoints(buffer.length(), -1));
        }

        // Return the request, if any
        if (buffer.isEmpty()) {
            return null;
        }
        if (selectRequest) {
            data.focusScope = context.navFocusScopeID;
            data.lastRequestFrame = context.frameCount;
            data.lastRequestTime = context.time;
        }
        request.flags = flags;
        request.searchBuffer = buffer.toString();
        request.selectRequest = data.lastRequestFrame == context.frameCount;
        request.singleCharMode = false;
        request.singleCharSize = 0;

        // Calculate if the buffer contains the same character repeated. This can be used to
        // implement a special search mode on the first character. Single char mode is always set
        // for the first input character, because it usually leads to a "next".
        if ((flags & TypingSelectFlags.ALLOW_SINGLE_CHAR_MODE) != 0) {
            final String search = request.searchBuffer;
            final int firstLength = Character.charCount(search.codePointAt(0));
            int position = firstLength;
            while (position < search.length()
                    && search.regionMatches(position, search, 0, firstLength)) {
                position += firstLength;
            }
            final int singleCharCount =
                    position >= search.length() ? search.length() / firstLength : 0;
            request.singleCharMode = singleCharCount > 0 || data.singleCharModeLock;
            request.singleCharSize = firstLength;
            // From now on we stop search matching, and lock to single char mode
            data.singleCharModeLock |= singleCharCount >= TYPING_SELECT_SINGLE_CHAR_COUNT_FOR_LOCK;
        }

        return request;
    }

    /**
     * Count how many leading characters match, ignoring case.
     *
     * @param search The search text.
     * @param searchLength How many chars of the search text to compare.
     * @param name The item name.
     * @return The number of matching chars.
     */
    private static int matchLengthIgnoreCase(
            @NonNull String search, int searchLength, @NonNull String name) {
        final int limit = Math.min(searchLength, name.length());
        int matchLength = 0;
        while (matchLength < limit
                && Character.toUpperCase(search.charAt(matchLength))
                        == Character.toUpperCase(name.charAt(matchLength))) {
            ++matchLength;
        }
        return matchLength;
    }

    /**
     * The default handler for finding the result of a typing-select request. You may implement your
     * own, and might want to display a tooltip showing the current search buffer (except in single
     * char mode, where it's better not to display one).
     *
     * @param request The request, may be null so both calls can be done from the same spot.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @param navItemIndex The index of the currently focused item, required for single char mode to
     *     go to the next match. If your selection user data are indices, this is {@link
     *     com.ikalagaming.graphics.frontend.gui.data.MultiSelectIO#navIDItem}, otherwise use -1.
     * @return The index of the matching item, or -1 for no match.
     */
    static int typingSelectFindMatch(
            TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName,
            int navItemIndex) {
        if (request == null || !request.selectRequest) {
            return -1;
        }
        final int index;
        if (request.singleCharMode
                && (request.flags & TypingSelectFlags.ALLOW_SINGLE_CHAR_MODE) != 0) {
            index =
                    typingSelectFindNextSingleCharMatch(
                            request, itemsCount, getItemName, navItemIndex);
        } else {
            index = typingSelectFindBestLeadingMatch(request, itemsCount, getItemName);
        }
        if (index != -1) {
            IkGuiImplNav.setNavCursorVisibleAfterMove();
        }
        return index;
    }

    /**
     * Special handling for when a single character is repeated: search for items starting with that
     * letter, going to the next one after the focused item.
     *
     * @param request The request.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @param navItemIndex The index of the focused item, or -1 to return the first match.
     * @return The index of the matching item, or -1 for no match.
     */
    static int typingSelectFindNextSingleCharMatch(
            @NonNull TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName,
            int navItemIndex) {
        int firstMatchIndex = -1;
        boolean returnNextMatch = false;
        for (int index = 0; index < itemsCount; ++index) {
            final String itemName = getItemName.apply(index);
            if (matchLengthIgnoreCase(request.searchBuffer, request.singleCharSize, itemName)
                    < request.singleCharSize) {
                continue;
            }
            if (returnNextMatch) {
                // Return the next matching item after the current item
                return index;
            }
            if (firstMatchIndex == -1 && navItemIndex == -1) {
                // Return the first match immediately if we don't have a nav item
                return index;
            }
            if (firstMatchIndex == -1) {
                // Record the first match for wrapping
                firstMatchIndex = index;
            }
            if (navItemIndex == index) {
                // Record that we encountered the nav item, so we can return the next match
                returnNextMatch = true;
            }
        }
        return firstMatchIndex;
    }

    /**
     * Find the item whose name has the longest leading match with the search buffer.
     *
     * @param request The request.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @return The index of the best matching item, or -1 for no match.
     */
    static int typingSelectFindBestLeadingMatch(
            @NonNull TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName) {
        final String search = request.searchBuffer;
        int longestMatchIndex = -1;
        int longestMatchLength = 0;
        for (int index = 0; index < itemsCount; ++index) {
            final String itemName = getItemName.apply(index);
            final int matchLength = matchLengthIgnoreCase(search, search.length(), itemName);
            if (matchLength <= longestMatchLength) {
                continue;
            }
            longestMatchIndex = index;
            longestMatchLength = matchLength;
            if (matchLength == search.length()) {
                break;
            }
        }
        return longestMatchIndex;
    }
}
