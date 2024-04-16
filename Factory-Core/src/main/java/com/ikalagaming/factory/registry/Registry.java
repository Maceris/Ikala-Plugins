package com.ikalagaming.factory.registry;

import lombok.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A generic map from string to an arbitrary type, which several registries use.
 *
 * @param <T> The type we are storing, typically a definition.
 */
public class Registry<T> {
    /**
     * A map from the name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format to the
     * definition.
     */
    protected final Map<String, T> definitions = new HashMap<>();

    /**
     * Returns true if this registry contains a definition for the specified key.
     *
     * @param name The name of the key.
     * @return true if this registry contains a mapping for the specified key.
     */
    public boolean containsKey(@NonNull String name) {
        return definitions.containsKey(name);
    }

    /**
     * Look up the definition by fully qualified name.
     *
     * @param name The name.
     * @return An optional that will contain the definition, if it can be found.
     */
    public Optional<T> find(@NonNull String name) {
        if (!definitions.containsKey(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(name));
    }

    /**
     * Fetch an unmodifiable copy of the list of the names that currently exist.
     *
     * @return An unmodifiable copy of the block names.
     */
    public List<String> getNames() {
        return List.copyOf(definitions.keySet());
    }

    /**
     * Fetch an unmodifiable copy of the list of values that currently exist.
     *
     * @return An unmodifiable copy of the values.
     */
    public List<T> getValues() {
        return List.copyOf(definitions.values());
    }
}
