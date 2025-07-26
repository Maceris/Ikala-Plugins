package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkLong implements Comparable<IkLong> {
    private final long[] data = new long[] {0};

    public IkLong(IkLong iklong) {
        this.data[0] = iklong.data[0];
    }

    public IkLong(long value) {
        this.data[0] = value;
    }

    public long get() {
        return this.data[0];
    }

    public void set(long value) {
        this.data[0] = value;
    }

    public void set(IkLong value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkLong other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Long.hashCode(this.data[0]);
    }

    public IkLong copy() {
        return new IkLong(this);
    }

    public int compareTo(IkLong o) {
        return Long.compare(this.get(), o.get());
    }
}
