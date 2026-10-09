package com.ikalagaming.graphics.ui.spec;

/** A listener registration, which stops listening when closed. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    /** Stop listening. Closing again does nothing. */
    @Override
    void close();
}
