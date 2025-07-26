package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkInt implements Comparable<IkInt> {
    private final int[] data = new int[] {0};

    public IkInt(IkInt ikInt) {
        this.data[0] = ikInt.data[0];
    }

    public IkInt(int value) {
        this.data[0] = value;
    }

    public int get() {
        return this.data[0];
    }

    public void set(int value) {
        this.data[0] = value;
    }

    public void set(IkInt value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkInt other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Integer.hashCode(this.data[0]);
    }

    public IkInt copy() {
        return new IkInt(this);
    }

    public int compareTo(IkInt o) {
        return Integer.compare(this.get(), o.get());
    }
}
