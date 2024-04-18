package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.crafting.Recipe;

import lombok.NonNull;

import java.util.*;

public class RecipeRegistry {
    /**
     * A map from the machine name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format
     * to the list of recipes that apply to that machine.
     */
    private final Map<String, List<Recipe>> definitions = new HashMap<>();

    /**
     * Add a recipe to multiple machines.
     *
     * @param machineNames The list of machine names in {@link
     *     RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format. Discards duplicate or null names.
     * @param recipe The recipe to add to each machine.
     */
    public void add(@NonNull List<String> machineNames, @NonNull Recipe recipe) {
        machineNames.stream()
                .distinct()
                .filter(Objects::nonNull)
                .forEach(machineName -> add(machineName, recipe));
    }

    /**
     * Add a recipe to a single machine.
     *
     * @param machineName The machine name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT}
     *     format.
     * @param recipe The recipe to add to that machine.
     */
    public void add(@NonNull String machineName, @NonNull Recipe recipe) {
        var list = definitions.computeIfAbsent(machineName, ignored -> new ArrayList<>());
        if (!list.contains(recipe)) {
            list.add(recipe);
        }
    }

    /**
     * Returns true if this registry contains a machine corresponding the specified key.
     *
     * @param machineName The name of the key.
     * @return true if this registry contains a mapping for the specified key.
     */
    public boolean containsKey(@NonNull String machineName) {
        return definitions.containsKey(machineName);
    }

    /**
     * Look up the list of recipes by fully qualified name.
     *
     * @param machineName The name.
     * @return An optional that will contain the definition, if it can be found.
     */
    public Optional<List<Recipe>> find(@NonNull String machineName) {
        if (!definitions.containsKey(machineName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(machineName));
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
