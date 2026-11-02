package com.ikalagaming.characters.view;

import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Clothing pushed out over bulky body parts slot by slot. A layer's mesh can carry a form target
 * for each body slot it lies over: for each vertex, its move to clear the bulkiest clothing form,
 * times how much that slot owns the vertex (1 over the slot, easing to 0 a little way into its
 * neighbors). Over a slot whose worn body part has a thinner form the move is scaled down by the
 * forms' offsets, so jeans over a robot leg stand out around that leg and stay snug at the waist. A
 * vertex takes the largest of its slots' scaled moves, not their sum, so it doesn't stand out twice
 * as far where two bulky slots meet.
 *
 * @author Ches Burks
 */
public final class PartForms {
    /** Floats per vertex in a part. */
    private static final int STRIDE = 14;

    /**
     * How far over each slot clothing is pushed out, as shares of the bulkiest form's offset.
     *
     * @param plan The body plan, with its forms.
     * @param slotForms The form of the body part worn in each slot; slots left out are bare.
     * @return The share for each slot with a form thicker than the first.
     */
    public static Map<String, Float> amounts(
            @NonNull BodyPlan plan, @NonNull Map<String, String> slotForms) {
        Map<String, Float> amounts = new HashMap<>();
        if (plan.forms().isEmpty()) {
            return amounts;
        }
        float bulkiest = plan.forms().getLast().offset();
        if (bulkiest <= 0) {
            return amounts;
        }
        slotForms.forEach(
                (slot, form) -> {
                    int index = plan.formIndex(form);
                    if (index > 0) {
                        amounts.put(slot, plan.forms().get(index).offset() / bulkiest);
                    }
                });
        return amounts;
    }

    /**
     * Push a layer's mesh out over its slots by their amounts.
     *
     * @param part The mesh.
     * @param amounts Each slot's share of the bulkiest form's offset; see {@link #amounts}.
     * @return The mesh moved, or the same mesh if it has no form targets for those slots.
     */
    public static ImportedCharacter.Part displace(
            @NonNull ImportedCharacter.Part part, @NonNull Map<String, Float> amounts) {
        Map<String, float[]> targets = part.formTargets();
        if (targets.isEmpty()
                || amounts.entrySet().stream()
                        .noneMatch(e -> e.getValue() > 0 && targets.containsKey(e.getKey()))) {
            return part;
        }
        float[][] deltas = targets.values().toArray(new float[0][]);
        float[] shares = new float[deltas.length];
        int s = 0;
        for (String slot : targets.keySet()) {
            shares[s++] = amounts.getOrDefault(slot, 0f);
        }
        float[] vertices = part.vertices().clone();
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f position = new Vector3f();
        float[] lengths = new float[deltas.length];
        int count = vertices.length / STRIDE;
        for (int v = 0; v < count; ++v) {
            // The whole move is the slot owning the vertex outright: the longest of its targets
            int owner = -1;
            float longest = 0;
            for (int t = 0; t < deltas.length; ++t) {
                float[] d = deltas[t];
                lengths[t] =
                        (float)
                                Math.sqrt(
                                        d[v * 3] * d[v * 3]
                                                + d[v * 3 + 1] * d[v * 3 + 1]
                                                + d[v * 3 + 2] * d[v * 3 + 2]);
                if (lengths[t] > longest) {
                    longest = lengths[t];
                    owner = t;
                }
            }
            int base = v * STRIDE;
            position.set(vertices[base], vertices[base + 1], vertices[base + 2]);
            if (owner >= 0) {
                float scale = 0;
                for (int t = 0; t < deltas.length; ++t) {
                    scale = Math.max(scale, lengths[t] / longest * shares[t]);
                }
                float[] whole = deltas[owner];
                position.add(
                        whole[v * 3] * scale, whole[v * 3 + 1] * scale, whole[v * 3 + 2] * scale);
                vertices[base] = position.x;
                vertices[base + 1] = position.y;
                vertices[base + 2] = position.z;
            }
            min.min(position);
            max.max(position);
        }
        return part.withVertices(vertices, min, max);
    }

    /** Static methods only. */
    private PartForms() {}
}
