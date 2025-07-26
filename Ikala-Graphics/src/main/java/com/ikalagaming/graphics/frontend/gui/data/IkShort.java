package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkShort implements Comparable<IkShort> {
    private final short[] data = new short[] {0};

    public IkShort(IkShort ikshort) {
        this.data[0] = ikshort.data[0];
    }

    public IkShort(short value) {
        this.data[0] = value;
    }

    public short get() {
        return this.data[0];
    }

    public void set(short value) {
        this.data[0] = value;
    }

    public void set(IkShort value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkShort other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Short.hashCode(this.data[0]);
    }

    public IkShort copy() {
        return new IkShort(this);
    }

    public int compareTo(IkShort o) {
        return Short.compare(this.get(), o.get());
    }
}
