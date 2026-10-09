package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGui;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper to parse and apply simple text filters:
 *
 * <ul>
 *   <li>{@code aaa bbb} matches text containing both "aaa" and "bbb".
 *   <li>{@code aaa,bbb} matches text containing either "aaa" or "bbb".
 *   <li>{@code -aaa} excludes text containing "aaa".
 *   <li>{@code "Hello, world"} matches the exact sequence, including spaces and commas.
 * </ul>
 *
 * Matching ignores case. Call {@link #build()} after changing {@link #inputBuffer} yourself, or use
 * {@link #draw(String)} which does that when the user edits the filter.
 */
public class TextFilter {
    /** The maximum length of the filter text. */
    private static final int INPUT_BUFFER_CAPACITY = 256;

    /** A single word of the filter. */
    private static class Item {
        /** The text to search for. */
        final String text;

        /**
         * The number of consecutive items that form an AND chain, for the first item in the chain,
         * 0 otherwise.
         */
        int countInclude;

        Item(String text) {
            this.text = text;
            countInclude = 0;
        }
    }

    /** The filter text the user types. */
    public final IkString inputBuffer;

    /** The parsed items, with the exclusions first. */
    private final List<Item> items;

    /** The number of leading items that are exclusions. */
    private int countExclude;

    /** Create an empty filter, which passes everything. */
    public TextFilter() {
        this("");
    }

    /**
     * Create a filter with some initial filter text.
     *
     * @param defaultFilter The initial filter text.
     */
    public TextFilter(String defaultFilter) {
        inputBuffer = new IkString(INPUT_BUFFER_CAPACITY);
        items = new ArrayList<>();
        countExclude = 0;
        if (defaultFilter != null && !defaultFilter.isEmpty()) {
            inputBuffer.set(defaultFilter);
            build();
        }
    }

    /**
     * Draw an input text field to edit the filter, rebuilding the filter when it changes. Use
     * IkGui.setNextItemWidth() beforehand to change the width.
     *
     * @param label The label of the input text field.
     * @return True if the filter changed.
     */
    public boolean draw(String label) {
        return drawWithHint(label, "incl -excl");
    }

    /**
     * Draw an input text field with a hint to edit the filter, rebuilding the filter when it
     * changes.
     *
     * @param label The label of the input text field.
     * @param hint The hint displayed when the filter is empty.
     * @return True if the filter changed.
     */
    public boolean drawWithHint(String label, String hint) {
        final boolean valueChanged = IkGui.inputTextWithHint(label, hint, inputBuffer);
        if (valueChanged) {
            build();
        }
        return valueChanged;
    }

    /** Clear the filter, so it passes everything. */
    public void clear() {
        inputBuffer.set("");
        build();
    }

    /**
     * Check if any filters are set. Useful if you need e.g. a different code path when there is
     * nothing to filter.
     *
     * @return True if the filter has any items.
     */
    public boolean isActive() {
        return !items.isEmpty();
    }

    /** Parse the filter text into the format used by passFilter(). */
    public void build() {
        items.clear();
        countExclude = 0;

        final String buffer = inputBuffer.toString();
        final int length = buffer.length();
        int sequenceStart = -1;
        int wordEnd;
        for (int wordBegin = 0; wordBegin < length; wordBegin = wordEnd + 1) {
            // Trim blanks
            while (wordBegin < length
                    && (buffer.charAt(wordBegin) == ' ' || buffer.charAt(wordBegin) == '\t')) {
                ++wordBegin;
            }
            final boolean isExclude = wordBegin < length && buffer.charAt(wordBegin) == '-';
            if (isExclude) {
                ++wordBegin;
            }
            final boolean isQuote = wordBegin < length && buffer.charAt(wordBegin) == '"';
            if (isQuote) {
                // Parse quotes, without storing the leading or trailing quote
                ++wordBegin;
                wordEnd = buffer.indexOf('"', wordBegin);
                if (wordEnd == -1) {
                    wordEnd = length;
                }
            } else {
                // Handle both ' ' and ',' separators
                wordEnd = wordBegin;
                while (wordEnd < length
                        && buffer.charAt(wordEnd) != ' '
                        && buffer.charAt(wordEnd) != ',') {
                    ++wordEnd;
                }
            }

            if (wordEnd > wordBegin) {
                // The '-' is not stored in items, it's implied by the index being below
                // countExclude
                final Item item = new Item(buffer.substring(wordBegin, wordEnd));
                if (isExclude) {
                    items.add(countExclude, item);
                    ++countExclude;
                    if (sequenceStart != -1) {
                        ++sequenceStart;
                    }
                } else {
                    if (sequenceStart == -1) {
                        sequenceStart = items.size();
                    }
                    items.add(item);
                    items.get(sequenceStart).countInclude++;
                }
            }

            // Next sequence
            if (wordEnd < length && buffer.charAt(wordEnd) == ',') {
                sequenceStart = -1;
            }
        }
    }

    /**
     * Check if some text contains another, ignoring case.
     *
     * @param text The text to search in.
     * @param search The text to search for.
     * @return True if the text contains the search text.
     */
    private static boolean containsIgnoreCase(String text, String search) {
        final int last = text.length() - search.length();
        for (int i = 0; i <= last; ++i) {
            if (text.regionMatches(true, i, search, 0, search.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if text passes the filter.
     *
     * @param text The text to check, may be null.
     * @return True if the text should be shown.
     */
    public boolean passFilter(String text) {
        if (items.isEmpty()) {
            return true;
        }
        if (text == null) {
            text = "";
        }

        // Exclusions are sorted to always come first
        int index = 0;
        for (; index < countExclude; ++index) {
            if (containsIgnoreCase(text, items.get(index).text)) {
                return false;
            }
        }

        // When only exclusions are specified, we implicitly pass
        if (index == items.size()) {
            return true;
        }

        // Process the inclusions, where each sequence passes if all of its items match
        while (index < items.size()) {
            final int sequenceEnd = index + items.get(index).countInclude;
            for (; index < sequenceEnd; ++index) {
                if (!containsIgnoreCase(text, items.get(index).text)) {
                    break;
                }
            }
            if (index == sequenceEnd) {
                // All matched
                return true;
            }
            // Try the next sequence
            index = sequenceEnd;
        }
        return false;
    }
}
