package com.ikalagaming.graphics.gui.data;

import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A mutable string with a capacity, used for text inputs. This takes the place of the char buffers
 * that Dear ImGui uses.
 *
 * <p>The capacity is measured in Java (UTF-16) characters, and limits how long the text can get
 * when edited through an input text widget. If {@link InputData#isResizable} is set, the capacity
 * grows as needed instead.
 */
public class IkString implements Comparable<IkString> {
    /** The default capacity of a string. */
    public static final short DEFAULT_LENGTH = 100;

    /** Options for how the string is edited by input text widgets. */
    public final IkString.InputData inputData;

    /** The maximum number of characters the string can hold. */
    private int capacity;

    /** The current text. */
    private String text;

    /** Create an empty string with the default capacity. */
    public IkString() {
        this(DEFAULT_LENGTH);
    }

    /**
     * Create an empty string.
     *
     * @param capacity The maximum number of characters the string can hold.
     */
    public IkString(int capacity) {
        inputData = new IkString.InputData();
        this.capacity = Math.max(0, capacity);
        text = "";
    }

    /**
     * Create a string with an initial value, with a capacity that exactly fits the text.
     *
     * @param text The text.
     */
    public IkString(String text) {
        this(0);
        set(text, true, 0);
    }

    /**
     * Create a string with an initial value and capacity. The text is truncated if it doesn't fit.
     *
     * @param text The text.
     * @param capacity The maximum number of characters the string can hold.
     */
    public IkString(String text, int capacity) {
        this(capacity);
        set(text);
    }

    /**
     * Copy constructor.
     *
     * @param other The string to copy.
     */
    public IkString(@NonNull IkString other) {
        this(other.capacity);
        text = other.text;
        inputData.allowedChars = other.inputData.allowedChars;
        inputData.isResizable = other.inputData.isResizable;
        inputData.resizeFactor = other.inputData.resizeFactor;
    }

    /**
     * Fetch the current text.
     *
     * @return The text.
     */
    public String get() {
        return text;
    }

    /**
     * Set the text to the string representation of an object.
     *
     * @param object The object.
     */
    public void set(Object object) {
        set(String.valueOf(object));
    }

    /**
     * Set the text to a copy of another string, resizing to fit.
     *
     * @param value The value to copy.
     */
    public void set(@NonNull IkString value) {
        set(value.get(), true);
    }

    /**
     * Set the text to a copy of another string.
     *
     * @param value The value to copy.
     * @param resize Whether to grow the capacity if the text doesn't fit.
     */
    public void set(@NonNull IkString value, boolean resize) {
        set(value.get(), resize);
    }

    /**
     * Set the text, growing the capacity if the string is resizable.
     *
     * @param value The new text.
     */
    public void set(String value) {
        set(value, inputData.isResizable, inputData.resizeFactor);
    }

    /**
     * Set the text.
     *
     * @param value The new text.
     * @param resize Whether to grow the capacity if the text doesn't fit.
     */
    public void set(String value, boolean resize) {
        set(value, resize, inputData.resizeFactor);
    }

    /**
     * Set the text.
     *
     * @param value The new text.
     * @param resize Whether to grow the capacity if the text doesn't fit.
     * @param extraRoom How much extra capacity to leave when resizing.
     */
    public void set(String value, boolean resize, int extraRoom) {
        String newText = String.valueOf(value);
        if (newText.length() > capacity) {
            if (resize) {
                capacity = newText.length() + Math.max(0, extraRoom);
            } else {
                newText = truncate(newText, capacity);
            }
        }
        text = newText;
    }

    /**
     * Truncate text to a maximum length without splitting a surrogate pair.
     *
     * @param value The text.
     * @param length The maximum length.
     * @return The truncated text.
     */
    private static String truncate(@NonNull String value, int length) {
        if (value.length() <= length) {
            return value;
        }
        if (length > 0 && Character.isHighSurrogate(value.charAt(length - 1))) {
            --length;
        }
        return value.substring(0, length);
    }

    /**
     * Grow the capacity, retaining the contents.
     *
     * @param newCapacity The new capacity. Must be at least the current capacity.
     * @throws IllegalArgumentException If newCapacity is less than the current capacity.
     */
    public void resize(int newCapacity) {
        if (newCapacity < capacity) {
            throw new IllegalArgumentException(
                    "New size must be greater than current size of the buffer");
        }
        capacity = newCapacity;
    }

    /**
     * The length of the text, in characters.
     *
     * @return The length of the text.
     */
    public int getLength() {
        return text.length();
    }

    /**
     * The maximum number of characters the string can hold before it needs to be resized.
     *
     * @return The capacity of the string.
     */
    public int getBufferSize() {
        return capacity;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /** Clear the text. */
    public void clear() {
        text = "";
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkString other) {
            return text.equals(other.text);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * Create a copy of this string.
     *
     * @return A new copy.
     */
    public IkString copy() {
        return new IkString(this);
    }

    @Override
    public int compareTo(@NonNull IkString o) {
        return get().compareTo(o.get());
    }

    /** Options for editing the string with input text widgets. */
    @NoArgsConstructor
    public static final class InputData {
        /** The default for {@link #resizeFactor}. */
        private static final short DEFAULT_RESIZE_FACTOR = 10;

        /** If not empty, only these characters can be typed into the string. */
        public String allowedChars = "";

        /** Whether the capacity grows automatically when text is input. */
        public boolean isResizable = false;

        /** How much extra capacity to add when the string grows. */
        public int resizeFactor = DEFAULT_RESIZE_FACTOR;
    }
}
