package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkByte implements Comparable<IkByte> {
    private final byte[] data = new byte[] {0};

    public IkByte(IkByte ikByte) {
        this.data[0] = ikByte.data[0];
    }

    public IkByte(byte value) {
        this.data[0] = value;
    }

    public byte get() {
        return this.data[0];
    }

    public void set(byte value) {
        this.data[0] = value;
    }

    public void set(IkByte value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkByte other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Byte.hashCode(this.data[0]);
    }

    public IkByte copy() {
        return new IkByte(this);
    }

    public int compareTo(IkByte o) {
        return Byte.compare(this.get(), o.get());
    }
}
