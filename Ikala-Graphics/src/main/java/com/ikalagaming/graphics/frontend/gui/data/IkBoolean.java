package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkBoolean implements Comparable<IkBoolean> {
    private final boolean[] data = new boolean[] {false};

    public IkBoolean(IkBoolean ikBoolean) {
        this.data[0] = ikBoolean.data[0];
    }

    public IkBoolean(boolean value) {
        this.data[0] = value;
    }

    public boolean get() {
        return this.data[0];
    }

    public void set(boolean value) {
        this.data[0] = value;
    }

    public void set(IkBoolean value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkBoolean other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Boolean.hashCode(this.data[0]);
    }

    public IkBoolean copy() {
        return new IkBoolean(this);
    }

    public int compareTo(IkBoolean o) {
        return Boolean.compare(this.get(), o.get());
    }
}
