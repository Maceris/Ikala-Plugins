package com.ikalagaming.ecs;

import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A kind of component, and the key its values are stored under. Make each type once, as a constant,
 * and use that constant everywhere.
 *
 * <ul>
 *   <li><b>Simulated</b> types are game state. They are part of every {@link Snapshot}, so rolling
 *       back puts them back. Their values must be immutable records or enums with value equality,
 *       since snapshots share them and compare them, and only simulated entities may have them.
 *   <li><b>Local</b> types are everything else: what is drawn, sounds, UI state, caches. Snapshots
 *       leave them out and restoring never changes them. Any entity may have them, and their values
 *       can be any object.
 * </ul>
 *
 * @param <T> The type of the values.
 */
public final class ComponentType<T> {

    /** The next index to hand out. */
    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

    /**
     * A name for logs and messages, such as {@code "lotomation:movement"}.
     *
     * @return The name.
     */
    @Getter private final String name;

    /**
     * The class of the values.
     *
     * @return The class.
     */
    @Getter private final Class<T> valueClass;

    /**
     * Whether values are game state that snapshots keep.
     *
     * @return True for simulated types, false for local ones.
     */
    @Getter private final boolean simulated;

    /** Where worlds keep this type's table; unique across every type made in the process. */
    final int index;

    /**
     * A component type.
     *
     * @param name A name for logs and messages.
     * @param valueClass The class of the values.
     * @param simulated Whether values are game state that snapshots keep.
     */
    private ComponentType(String name, Class<T> valueClass, boolean simulated) {
        this.name = name;
        this.valueClass = valueClass;
        this.simulated = simulated;
        index = NEXT_INDEX.getAndIncrement();
    }

    /**
     * A type whose values are game state, kept by snapshots.
     *
     * @param <T> The type of the values.
     * @param name A name for logs and messages.
     * @param valueClass The class of the values, a record or an enum.
     * @return The new type.
     * @throws IllegalArgumentException If the class is not a record or an enum.
     */
    public static <T> ComponentType<T> simulated(
            @NonNull String name, @NonNull Class<T> valueClass) {
        if (!valueClass.isRecord() && !valueClass.isEnum()) {
            throw new IllegalArgumentException(
                    "Simulated component "
                            + name
                            + " must be a record or an enum, not "
                            + valueClass.getName());
        }
        return new ComponentType<>(name, valueClass, true);
    }

    /**
     * A type whose values are local to this process, left out of snapshots.
     *
     * @param <T> The type of the values.
     * @param name A name for logs and messages.
     * @param valueClass The class of the values.
     * @return The new type.
     */
    public static <T> ComponentType<T> local(@NonNull String name, @NonNull Class<T> valueClass) {
        return new ComponentType<>(name, valueClass, false);
    }

    @Override
    public String toString() {
        return name;
    }
}
