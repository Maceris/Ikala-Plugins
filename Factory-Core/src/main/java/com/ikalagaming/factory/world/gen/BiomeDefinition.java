package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.World;
import lombok.NonNull;

import java.util.List;

/**
 * Information required to generate blocks in a biome.
 *
 * @param layers The materials that compose the biome, ordered from highest to lowest.
 */
public record BiomeDefinition(@NonNull List<Layer> layers) {
    /**
     * A layer of material.
     *
     * @param block The fully qualified block ID for the material the layer is composed of. If the string is empty, this layer is considered empty (as in, does not have blocks).
     * @param min The minimum number of blocks the layer can contain.
     * @param max The maximum number of blocks the layer can contain.
     */
    public record Layer(@NonNull String block, short min, short max) {}

    /**
     * Check if the provided definition is in a valid format. Things that are invalid:
     *
     * <ul>
     *     <li>Not having any layers</li>
     *     <li>Having more layers than blocks in the full world height</li>
     *     <li>Negative min or max values for a layer</li>
     *     <li>Layer max value smaller than min value</li>
     *     <li>Having the minimum values for all layers sum to greater than the world height</li>
     * </ul>
     *
     * @param definition The biome definition.
     * @return True if the definition is structurally valid, false if it is not.
     */
    public static boolean validate(@NonNull BiomeDefinition definition) {
        final var layers = definition.layers;
        if (layers.isEmpty()) {
            //TODO(ches) log this
            return false;
        }
        if (layers.size() > World.WORLD_HEIGHT_TOTAL) {
            //TODO(ches) log this
            return false;
        }

        int totalMin = 0;

        for (Layer layer : layers) {
            if (layer.min < 0 || layer.max < 0) {
                //TODO(ches) log this
                return false;
            }
            if (layer.max < layer.min) {
                //TODO(ches) log this
                return false;
            }
            totalMin += layer.min;
        }
        if (totalMin > World.WORLD_HEIGHT_TOTAL) {
            //TODO(ches) log this
            return false;
        }

        return true;
    }
}
