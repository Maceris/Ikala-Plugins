package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores the definitions of items, */
@Slf4j
@RequiredArgsConstructor
public class ItemRegistry {
    /**
     * A map from the item name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format to
     * the {@link ItemDefinition definition} of the item.
     */
    private final Map<String, ItemDefinition> definitions = new HashMap<>();

    private final TagRegistry tagRegistry;
    private final MaterialRegistry materialRegistry;

    /**
     * Look up item definition by fully qualified name.
     *
     * @param name The name of the item.
     * @return An optional that will contain the definition, if it can be found.
     */
    public Optional<ItemDefinition> find(@NonNull String name) {
        if (!definitions.containsKey(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(name));
    }

    /**
     * Fetch an unmodifiable copy of the list of the item names that currently exist.
     *
     * @return An unmodifiable copy of the item names.
     */
    public List<String> getNames() {
        return List.copyOf(definitions.keySet());
    }

    public boolean register(@NonNull String name, @NonNull ItemDefinition value) {

        if (!name.matches(RegistryConstants.FULLY_QUALIFIED_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "FULL_ITEM_NAME_INVALID", FactoryPlugin.getResourceBundle(), name));
            return false;
        }
        if (definitions.containsKey(name)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "ITEM_ALREADY_DEFINED", FactoryPlugin.getResourceBundle(), name));
            return false;
        }

        if (!validate(value)) {
            return false;
        }

        if (!name.equals(RegistryConstants.combineName(value.modName(), value.itemName()))) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "DEFINITION_MISMATCHED_NAME", FactoryPlugin.getResourceBundle(), name));
            return false;
        }

        definitions.put(name, value);
        log.debug(
                SafeResourceLoader.getStringFormatted(
                        "ITEM_REGISTERED", FactoryPlugin.getResourceBundle(), name));
        return true;
    }

    /**
     * Validates if the definition is valid. Logs warnings if it is not.
     *
     * @param value The definition to check.
     * @return True if it is valid, false if it is not.
     */
    private boolean validate(@NonNull ItemDefinition value) {
        if (!value.modName().matches(RegistryConstants.MOD_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MOD_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.modName()));
            return false;
        }
        if (!value.itemName().matches(RegistryConstants.RESOURCE_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "RESOURCE_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.itemName()));
            return false;
        }
        if (value.material() != null && !materialRegistry.materialExists(value.material())) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MAT_MISSING", FactoryPlugin.getResourceBundle(), value.material()));
            return false;
        }
        for (String tag : value.tags()) {
            if (!tagRegistry.tagExists(tag)) {
                log.warn(
                        SafeResourceLoader.getStringFormatted(
                                "TAG_MISSING",
                                FactoryPlugin.getResourceBundle(),
                                value.material()));
                return false;
            }
        }
        return true;
    }
}
