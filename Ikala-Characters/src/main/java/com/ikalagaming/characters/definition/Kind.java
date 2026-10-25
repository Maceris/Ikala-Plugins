package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.DataFormat;

import lombok.Getter;
import lombok.NonNull;

/**
 * The kinds of character definition. Each kind has its own folder of JSON files and its own numeric
 * IDs.
 *
 * @author Ches Burks
 */
public enum Kind {
    /** Bone hierarchies. */
    RIG(Rig.class),
    /** Body plans, built on rigs. */
    BODY_PLAN(BodyPlan.class),
    /** Sliders that scale bones. */
    SLIDER(Slider.class),
    /** Swappable meshes: body parts, clothing layers and accessories. */
    PART(Part.class),
    /** Material parameter sets. */
    MATERIAL_TEMPLATE(MaterialTemplate.class),
    /** Reusable color themes. */
    PALETTE(Palette.class),
    /** Mappings from another skeleton's bones onto a rig. */
    RIG_MAP(RigMap.class);

    /** The record that holds a definition of this kind. */
    @Getter private final Class<? extends Definition> type;

    /** The name in JSON, which is also the folder its files are in, like {@code bodyPlan}. */
    @Getter private final String folder;

    Kind(Class<? extends Definition> type) {
        this.type = type;
        folder = DataFormat.enumName(this);
    }

    /**
     * Find the kind a record class holds.
     *
     * @param type The record class.
     * @return The kind.
     * @throws IllegalArgumentException If it isn't a definition record.
     */
    public static Kind of(@NonNull Class<? extends Definition> type) {
        for (Kind kind : values()) {
            if (kind.type == type) {
                return kind;
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + " is not a kind of definition");
    }
}
