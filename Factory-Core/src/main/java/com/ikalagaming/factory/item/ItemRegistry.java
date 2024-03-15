package com.ikalagaming.factory.item;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ItemRegistry {
    /**
     * A map from the item name in {@code mod_name:item_name} format to the {@link ItemDefinition
     * definition} of the item.
     */
    private final Map<String, ItemDefinition> definitions = new HashMap<>();

    public boolean register(@NonNull String name, @NonNull ItemDefinition value) {

        if (!name.matches(Item.COMBINED_NAME_FORMAT)) {
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

        if (!name.equals(Item.combineName(value.modName(), value.itemName()))) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "ITEM_DEFINITION_MISMATCHED_NAME",
                            FactoryPlugin.getResourceBundle(),
                            name));
            return false;
        }

        definitions.put(name, value);
        log.debug(
                SafeResourceLoader.getStringFormatted(
                        "ITEM_REGISTERED", FactoryPlugin.getResourceBundle(), name));
        return true;
    }

    private boolean validate(@NonNull ItemDefinition value) {
        if (!value.modName().matches(Item.MOD_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MOD_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.modName()));
            return false;
        }
        if (!value.itemName().matches(Item.ITEM_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "ITEM_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.itemName()));
            return false;
        }
        return true;
    }
}
