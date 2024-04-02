package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class BlockRegistry extends RegistryTemplate<BlockDefinition> {

    private final TagRegistry tagRegistry;
    private final MaterialRegistry materialRegistry;

    /**
     * Register a block. The name must be valid and not currently in use, and the definition must
     * also be valid. Things like name formats, and whether tags/materials currently are registered
     * will be taken into account.
     *
     * @param name The {@link RegistryConstants#FULLY_QUALIFIED_NAME_FORMAT fully qualified} name of
     *     the block.
     * @param definition The definition of the block.
     * @return Whether we successfully registered the block.
     */
    public boolean register(@NonNull String name, @NonNull BlockDefinition definition) {
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

        if (!validate(definition)) {
            return false;
        }

        if (!name.equals(
                RegistryConstants.combineName(definition.modName(), definition.blockName()))) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "DEFINITION_MISMATCHED_NAME", FactoryPlugin.getResourceBundle(), name));
            return false;
        }

        definitions.put(name, definition);
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
