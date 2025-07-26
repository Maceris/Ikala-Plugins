package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class IkString implements Comparable<IkString> {
    public static final short DEFAULT_LENGTH = 100;
    public static final short CARET_LENGTH = 1;
    public final IkString.InputData inputData;
    @Getter private byte[] data;
    private String cachedText;

    public IkString() {
        this(100);
    }

    public IkString(int length) {
        this.inputData = new IkString.InputData();
        this.cachedText = "";
        this.data = new byte[length + 1];
    }

    public IkString(String text) {
        this.inputData = new IkString.InputData();
        this.cachedText = "";
        this.set(text, true, 0);
    }

    public IkString(String text, int length) {
        this(length);
        this.set(text);
    }

    public IkString(IkString other) {
        this(other.cachedText, other.data.length);
        this.inputData.allowedChars = other.inputData.allowedChars;
        this.inputData.isResizable = other.inputData.isResizable;
        this.inputData.resizeFactor = other.inputData.resizeFactor;
        this.inputData.size = other.inputData.size;
        this.inputData.isDirty = other.inputData.isDirty;
        this.inputData.isResized = other.inputData.isResized;
    }

    public String get() {
        if (this.inputData.isDirty) {
            this.inputData.isDirty = false;
            this.cachedText = new String(this.data, 0, this.inputData.size, StandardCharsets.UTF_8);
        }
        return this.cachedText;
    }

    public void set(Object object) {
        this.set(String.valueOf(object));
    }

    public void set(IkString value) {
        this.set(value.get(), true);
    }

    public void set(IkString value, boolean resize) {
        this.set(value.get(), resize);
    }

    public void set(String value) {
        this.set(value, this.inputData.isResizable, this.inputData.resizeFactor);
    }

    public void set(String value, boolean resize) {
        this.set(value, resize, this.inputData.resizeFactor);
    }

    public void set(String value, boolean resize, int extraRoom) {
        final byte[] oldBuffer = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        final int currentLength = this.data == null ? 0 : this.data.length;
        byte[] newBuffer = null;
        if (resize && currentLength - 1 < oldBuffer.length) {
            newBuffer = new byte[oldBuffer.length + extraRoom + 1];
            this.inputData.size = oldBuffer.length;
        }

        if (newBuffer == null) {
            newBuffer = new byte[currentLength];
            this.inputData.size = Math.max(0, Math.min(oldBuffer.length, currentLength - 1));
        }

        System.arraycopy(
                oldBuffer, 0, newBuffer, 0, Math.min(oldBuffer.length, newBuffer.length - 1));
        this.data = newBuffer;
        this.inputData.isDirty = true;
    }

    /**
     * Resize the internal buffer, retaining the contents.
     *
     * @param newSize The new size of the string. Must be larger than the current size of the
     *     buffer.
     * @throws IllegalArgumentException If newSize is less than the current data length.
     */
    public void resize(int newSize) {
        if (newSize < this.data.length) {
            throw new IllegalArgumentException(
                    "New size must be greater than current size of the buffer");
        }

        final int size = newSize + 1;
        byte[] newBuffer = new byte[size];
        System.arraycopy(this.data, 0, newBuffer, 0, this.data.length);
        this.data = newBuffer;
    }

    byte[] resizeInternal(int newSize) {
        this.resize(newSize + this.inputData.resizeFactor);
        return this.data;
    }

    public int getLength() {
        return this.get().length();
    }

    public int getBufferSize() {
        return this.data.length;
    }

    public boolean isEmpty() {
        return this.getLength() == 0;
    }

    public boolean isNotEmpty() {
        return !this.isEmpty();
    }

    public void clear() {
        this.set("");
    }

    public String toString() {
        return this.get();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkString other) {
            return Objects.equals(this.cachedText, other.cachedText);
        }
        return false;
    }

    public int hashCode() {
        return this.cachedText.hashCode();
    }

    public IkString copy() {
        return new IkString(this);
    }

    public int compareTo(IkString o) {
        return this.get().compareTo(o.get());
    }

    @NoArgsConstructor
    public static final class InputData {
        private static final short DEFAULT_RESIZE_FACTOR = 10;
        public String allowedChars = "";
        public boolean isResizable = false;
        public int resizeFactor = 10;
        int size = 0;
        boolean isDirty = false;
        boolean isResized = false;
    }
}
