package com.ikalagaming.factory.world.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BlockRegistry {
    /**
     * A map from the item name in {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT} format to
     * the {@link ItemDefinition definition} of the item.
     */
    private final Map<String, BlockDefinition> definitions = new HashMap<>();

    private final TagRegistry tagRegistry;
    private final MaterialRegistry materialRegistry;

    public boolean register(@NonNull String name, @NonNull BlockDefinition value) {
        if (!name.matches(RegistryConstants.FULLY_QUALIFIED_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "FULL_BLOCK_NAME_INVALID", FactoryPlugin.getResourceBundle(), name));
            return false;
        }
        if (definitions.containsKey(name)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "BLOCK_ALREADY_DEFINED", FactoryPlugin.getResourceBundle(), name));
            return false;
        }

        if (!validate(value)) {
            return false;
        }

        if (!name.equals(RegistryConstants.combineName(value.modName(), value.blockName()))) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "DEFINITION_MISMATCHED_NAME", FactoryPlugin.getResourceBundle(), name));
            return false;
        }

        definitions.put(name, value);
        log.debug(
                SafeResourceLoader.getStringFormatted(
                        "BLOCK_REGISTERED", FactoryPlugin.getResourceBundle(), name));
        return true;
    }

    /**
     * Validates if the definition is valid. Logs warnings if it is not.
     *
     * @param value The definition to check.
     * @return True if it is valid, false if it is not.
     */
    private boolean validate(@NonNull BlockDefinition value) {
        if (!value.modName().matches(RegistryConstants.MOD_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MOD_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.modName()));
            return false;
        }
        if (!value.blockName().matches(RegistryConstants.RESOURCE_NAME_FORMAT)) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "RESOURCE_NAME_INVALID",
                            FactoryPlugin.getResourceBundle(),
                            value.blockName()));
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
