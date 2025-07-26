package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkFloat implements Comparable<IkFloat> {
    private final float[] data = new float[] {0};

    public IkFloat(IkFloat ikFloat) {
        this.data[0] = ikFloat.data[0];
    }

    public IkFloat(float value) {
        this.data[0] = value;
    }

    public float get() {
        return this.data[0];
    }

    public void set(float value) {
        this.data[0] = value;
    }

    public void set(IkFloat value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkFloat other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Float.hashCode(this.data[0]);
    }

    public IkFloat copy() {
        return new IkFloat(this);
    }

    public int compareTo(IkFloat o) {
        return Float.compare(this.get(), o.get());
    }
}
