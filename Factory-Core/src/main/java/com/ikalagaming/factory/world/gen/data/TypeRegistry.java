package com.ikalagaming.factory.world.gen.data;

import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The code-defined types of one kind of world generation object, by the {@code type} name data
 * uses. Data can only configure and combine these; mods with code can register more, and must
 * unregister them when they unload.
 *
 * @param <F> What builds an object of a type.
 */
public final class TypeRegistry<F> {

    /** The factories by type name. */
    private final Map<String, F> factories = new ConcurrentHashMap<>();

    /**
     * Add a type.
     *
     * @param type The type name, as data writes it.
     * @param factory What builds it.
     * @return False if the name was already taken, in which case nothing changes.
     */
    public boolean register(@NonNull String type, @NonNull F factory) {
        return factories.putIfAbsent(type, factory) == null;
    }

    /**
     * Remove a type.
     *
     * @param type The type name.
     */
    public void unregister(@NonNull String type) {
        factories.remove(type);
    }

    /**
     * The factory for a type.
     *
     * @param type The type name.
     * @return The factory, if the type is known.
     */
    public Optional<F> get(@NonNull String type) {
        return Optional.ofNullable(factories.get(type));
    }

    /**
     * Every type name, for messages.
     *
     * @return The names, sorted.
     */
    public SortedSet<String> names() {
        return new TreeSet<>(factories.keySet());
    }
}
