package com.ikalagaming.converter.scene;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.lod.MaterialAverages;
import com.ikalagaming.graphics.vulkan.ToneMapMath;

import lombok.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * What the materials going into a level of detail build look like, taken on the render thread for
 * the build: their averages, which far groups blend and bake by, and which of them are cut out.
 */
public final class LodLooks {

    /** Color channels in an average. */
    private static final int COLORS = 3;

    /** Where a packed occlusion, roughness and metal texture's roughness is. */
    private static final int PACKED_ROUGHNESS = 1;

    /** Where its metalness is. */
    private static final int PACKED_METALLIC = 2;

    /**
     * What each material looks like averaged, as the scene draws it: its texture's average where it
     * has one, in place of its base color, else its base color, in linear light, with its
     * roughness, metallic and specular. Render thread.
     *
     * @param graphics The graphics context, for the textures' averages.
     * @param indices Each material's index in the material cache.
     * @return The averages.
     */
    public static MaterialAverages averages(
            @NonNull GraphicsContext graphics, @NonNull Map<Material, Integer> indices) {
        final Map<Integer, float[]> known = new HashMap<>();
        final Map<Integer, Float> covered = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : indices.entrySet()) {
            final Material material = entry.getKey();
            final float[] out = new float[MaterialAverages.VALUES];
            final float[] texture = graphics.textures().average(material.getTexture());
            if (texture != null) {
                System.arraycopy(texture, 0, out, MaterialAverages.RED, COLORS);
                if (texture.length > COLORS
                        && material.getTransparency() == Material.Transparency.CUTOUT) {
                    covered.put(entry.getValue(), texture[COLORS]);
                }
            } else {
                out[MaterialAverages.RED] = ToneMapMath.decode(material.getBaseColor().x);
                out[MaterialAverages.RED + 1] = ToneMapMath.decode(material.getBaseColor().y);
                out[MaterialAverages.RED + 2] = ToneMapMath.decode(material.getBaseColor().z);
            }
            out[MaterialAverages.ROUGHNESS] = material.getRoughness();
            out[MaterialAverages.METALLIC] = material.getMetallic();
            // A packed texture's roughness and metalness stand in for the material's, as the scene
            // draws it
            final float[] packed =
                    graphics.textures().average(material.getOcclusionRoughnessMetal());
            if (packed != null) {
                out[MaterialAverages.ROUGHNESS] = packed[PACKED_ROUGHNESS];
                out[MaterialAverages.METALLIC] = packed[PACKED_METALLIC];
            }
            out[MaterialAverages.SPECULAR] = material.getSpecular();
            known.put(entry.getValue(), out);
        }
        return new MaterialAverages() {
            @Override
            public float[] average(int material) {
                return known.get(material);
            }

            @Override
            public float coverage(int material) {
                return covered.getOrDefault(material, 1f);
            }
        };
    }

    /**
     * Which of the materials are cut out, by index.
     *
     * @param indices Each material's index in the material cache.
     * @return Whether a material index is cut out.
     */
    public static IntPredicate cutOut(@NonNull Map<Material, Integer> indices) {
        final Set<Integer> cut = new HashSet<>();
        for (Map.Entry<Material, Integer> entry : indices.entrySet()) {
            if (entry.getKey().getTransparency() == Material.Transparency.CUTOUT) {
                cut.add(entry.getValue());
            }
        }
        return cut::contains;
    }

    /** Static helpers only. */
    private LodLooks() {}
}
