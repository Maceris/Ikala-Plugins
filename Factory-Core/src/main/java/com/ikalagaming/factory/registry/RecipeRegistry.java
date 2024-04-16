package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.crafting.Recipe;

import lombok.NonNull;

import java.util.*;

public class RecipeRegistry {
    /**
     * A map from the machine name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format
     * to the list of recipes that apply to that machine.
     */
    protected final Map<String, List<Recipe>> definitions = new HashMap<>();

    /**
     * Returns true if this registry contains a machine corresponding the specified key.
     *
     * @param name The name of the key.
     * @return true if this registry contains a mapping for the specified key.
     */
    public boolean containsKey(@NonNull String name) {
        return definitions.containsKey(name);
    }

    /**
     * Look up the list of recipes by fully qualified name.
     *
     * @param name The name.
     * @return An optional that will contain the definition, if it can be found.
     */
    public Optional<List<Recipe>> find(@NonNull String name) {
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
     * Fetch an unmodifiable copy of all recipes that currently exist. The lists are all combined,
     * with an attempt to remove duplicates.
     *
     * @return An unmodifiable copy of all recipes.
     */
    public List<Recipe> getValues() {
        return definitions.values().stream().flatMap(Collection::stream).distinct().toList();
    }
}
