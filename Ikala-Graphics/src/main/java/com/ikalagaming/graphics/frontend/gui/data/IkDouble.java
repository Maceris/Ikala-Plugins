package com.ikalagaming.graphics.frontend.gui.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IkDouble implements Comparable<IkDouble> {
    private final double[] data = new double[] {0};

    public IkDouble(IkDouble ikdouble) {
        this.data[0] = ikdouble.data[0];
    }

    public IkDouble(double value) {
        this.data[0] = value;
    }

    public double get() {
        return this.data[0];
    }

    public void set(double value) {
        this.data[0] = value;
    }

    public void set(IkDouble value) {
        this.set(value.get());
    }

    public String toString() {
        return String.valueOf(this.data[0]);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof IkDouble other) {
            return this.data[0] == other.data[0];
        }
        return false;
    }

    public int hashCode() {
        return Double.hashCode(this.data[0]);
    }

    public IkDouble copy() {
        return new IkDouble(this);
    }

    public int compareTo(IkDouble o) {
        return Double.compare(this.get(), o.get());
    }
}
