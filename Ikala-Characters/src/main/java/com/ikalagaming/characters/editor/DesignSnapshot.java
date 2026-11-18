package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.design.DesignCodec;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editor's design as it stands, and the look's starting values from a design: the editor shows
 * a {@link CharacterLook}, and saves, switches variant and equips through a {@link Design} made
 * from it. Static helpers only; render thread, like the look.
 */
final class DesignSnapshot {
    /**
     * A design's slider values as the look keeps them.
     *
     * @param design The design.
     * @return The values, by slider ID.
     */
    static Map<Id, float[]> sliderValues(@NonNull Design design) {
        Map<Id, float[]> values = new HashMap<>();
        design.sliders()
                .forEach(
                        (id, list) -> {
                            float[] value = new float[list.size()];
                            for (int i = 0; i < value.length; ++i) {
                                value[i] = list.get(i);
                            }
                            values.put(Id.parse(id), value);
                        });
        return values;
    }

    /**
     * A design's colors as the look keeps them.
     *
     * @param design The design.
     * @return The color codes, by palette channel.
     */
    static Map<String, Integer> colorCodes(@NonNull Design design) {
        Map<String, Integer> codes = new HashMap<>();
        design.colors().forEach((channel, color) -> codes.put(channel, ColorCode.encode(color)));
        return codes;
    }

    /**
     * The design the editor shows, as it is now.
     *
     * @param look The look shown.
     * @param plan Its body plan.
     * @param prepared The design prepared for it, whose options the look's choices index.
     * @param id The ID to give it.
     * @param variant The variant to make it.
     * @param withSliders Whether to keep the slider values, or leave them for the variant to start.
     * @return The design.
     */
    static Design of(
            @NonNull CharacterLook look,
            @NonNull BodyPlan plan,
            @NonNull PreparedDesign prepared,
            @NonNull Id id,
            @NonNull String variant,
            boolean withSliders) {
        Map<String, Id> parts = new LinkedHashMap<>();
        Map<String, Id> layers = new LinkedHashMap<>();
        Map<String, Id> chains = new LinkedHashMap<>();
        for (int s = 0; s < look.slots().slots().size(); ++s) {
            int choice = look.choice(s);
            if (choice == CharacterLook.EMPTY) {
                continue;
            }
            String place = look.slots().slots().get(s).id();
            if (plan.socket(place) != null) {
                // Designs don't list accessories yet, so they are only tried on here
                continue;
            }
            Id part = prepared.optionIds().get(s).get(choice);
            if (plan.layer(place) != null) {
                layers.put(place, part);
            } else if (plan.extension(place) != null) {
                chains.put(place, part);
            } else {
                parts.put(place, part);
            }
        }
        Map<String, List<Float>> sliders = new LinkedHashMap<>();
        if (withSliders) {
            BodyPlan.Variant shape = plan.variant(variant);
            look.values()
                    .forEach(
                            (sliderId, value) -> {
                                // Sliders where the variant starts them are left out
                                BodyPlan.SliderSetting setting =
                                        shape == null
                                                ? null
                                                : shape.sliders().get(sliderId.toString());
                                float start = setting == null ? 0 : setting.initial();
                                List<Float> snapped = new ArrayList<>();
                                boolean moved = false;
                                for (float v : value) {
                                    float step = DesignCodec.snap(v);
                                    snapped.add(step);
                                    moved |= step != DesignCodec.snap(start);
                                }
                                if (moved) {
                                    sliders.put(sliderId.toString(), snapped);
                                }
                            });
        }
        Map<String, Color> colors = new LinkedHashMap<>();
        for (String channel : look.channels()) {
            Integer code = look.color(channel);
            if (code != null) {
                colors.put(channel, ColorCode.toColor(code));
            }
        }
        return new Design(
                id,
                plan.id(),
                variant,
                look.style(),
                sliders,
                parts,
                layers,
                null,
                colors,
                List.copyOf(look.fx()),
                look.decal(),
                chains);
    }

    /** Static helpers only. */
    private DesignSnapshot() {}
}
