package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.registry.BlockRegistry;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Information required to generate blocks in a biome.
 *
 * @param layers The materials that compose the biome, ordered from highest to lowest.
 * @param totalMinHeight The total of all layers min heights.
 */
@Slf4j
public record BiomeDefinition(@NonNull List<Layer> layers, int totalMinHeight) {
    /**
     * A layer of material.
     *
     * @param block The fully qualified block ID for the material the layer is composed of. If the
     *     string is empty, this layer is considered empty (as in, does not have blocks).
     * @param min The minimum number of blocks the layer can contain.
     * @param max The maximum number of blocks the layer can contain.
     * @param extraProportion The percentage of the total layers (max - min) that this takes up.
     */
    public record Layer(@NonNull String block, short min, short max, float extraProportion) {}

    /**
     * Check if the provided definition is in a valid format. Things that are invalid:
     *
     * <ul>
     *   <li>Not having any layers
     *   <li>Having more layers than blocks in the full world height
     *   <li>Negative min or max values for a layer
     *   <li>Layer max value smaller than min value
     *   <li>Having the minimum values for all layers sum to greater than the world height
     * </ul>
     *
     * @param definition The biome definition.
     * @param blockRegistry The block registry to use in validating the blocks are known.
     * @return True if the definition is structurally valid, false if it is not.
     */
    public static boolean validate(
            @NonNull BiomeDefinition definition, @NonNull BlockRegistry blockRegistry) {
        final var layers = definition.layers;
        if (layers.isEmpty()) {
            log.warn(
                    SafeResourceLoader.getString(
                            "BIOME_DEFINITION_MISSING_LAYERS", FactoryPlugin.getResourceBundle()));
            return false;
        }
        if (layers.size() > World.WORLD_HEIGHT_TOTAL) {
            log.warn(
                    SafeResourceLoader.getString(
                            "BIOME_DEFINITION_TO_MANY_LAYERS", FactoryPlugin.getResourceBundle()));
            return false;
        }

        int totalMin = 0;

        for (int i = 0; i < layers.size(); ++i) {
            Layer layer = layers.get(i);
            if (layer.min < 0 || layer.max < 0) {
                log.warn(
                        SafeResourceLoader.getString(
                                "BIOME_DEFINITION_LAYER_NEGATIVE_VALUE",
                                FactoryPlugin.getResourceBundle()));
                return false;
            }
            if (layer.max < layer.min) {
                log.warn(
                        SafeResourceLoader.getString(
                                "BIOME_DEFINITION_LAYER_WRONG_ORDER",
                                FactoryPlugin.getResourceBundle()));
                return false;
            }
            totalMin += layer.min;
            if (!blockRegistry.containsKey(layer.block())) {
                log.warn(
                        SafeResourceLoader.getStringFormatted(
                                "BIOME_DEFINITION_UNKNOWN_BLOCK",
                                FactoryPlugin.getResourceBundle(),
                                layer.block(),
                                Integer.toString(i)));
                return false;
            }
        }
        if (totalMin > World.WORLD_HEIGHT_TOTAL) {
            log.warn(
                    SafeResourceLoader.getString(
                            "BIOME_DEFINITION_TOTAL_MIN_HEIGHT_TOO_LARGE",
                            FactoryPlugin.getResourceBundle()));
            return false;
        }

        return true;
    }
}
