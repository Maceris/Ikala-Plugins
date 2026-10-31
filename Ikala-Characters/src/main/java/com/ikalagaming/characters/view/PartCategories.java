package com.ikalagaming.characters.view;

import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An imported character's slots sorted into the body's regions, head, upper body and lower body, by
 * how high their meshes sit at rest, until slots come from body plans that name their regions. Each
 * category also gives the box around its meshes, for the camera to frame.
 *
 * @param categories The categories that have slots, from the head down.
 * @param min The minimum corner of the box around every part, in model space.
 * @param max The maximum corner of the box around every part, in model space.
 * @author Ches Burks
 */
public record PartCategories(
        @NonNull List<Category> categories, @NonNull Vector3f min, @NonNull Vector3f max) {
    /** The head category's ID. */
    public static final String HEAD = "head";

    /** The upper body category's ID. */
    public static final String UPPER_BODY = "upper-body";

    /** The lower body category's ID. */
    public static final String LOWER_BODY = "lower-body";

    /** How far up the body, as a fraction of its height, a slot's middle must be for the head. */
    static final float HEAD_FROM = 0.82f;

    /** How far up the body a slot's middle must be, at least, for the upper body. */
    static final float UPPER_FROM = 0.42f;

    /**
     * One region of the body.
     *
     * @param id The category's ID, like {@link #HEAD}.
     * @param label What editors call it.
     * @param slots The positions of its slots in the {@link PartSlots} list.
     * @param min The minimum corner of the box around its slots' meshes, in model space.
     * @param max The maximum corner of the box around its slots' meshes, in model space.
     */
    public record Category(
            @NonNull String id,
            @NonNull String label,
            @NonNull List<Integer> slots,
            @NonNull Vector3f min,
            @NonNull Vector3f max) {}

    /**
     * Sort a character's slots into categories.
     *
     * @param character The character.
     * @param slots Its parts sorted into slots.
     * @return The categories.
     */
    public static PartCategories of(
            @NonNull ImportedCharacter character, @NonNull PartSlots slots) {
        List<Integer> everything = new ArrayList<>();
        for (int i = 0; i < character.parts().size(); ++i) {
            everything.add(i);
        }
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        bounds(character, everything, min, max);
        float height = Math.max(max.y - min.y, 1e-3f);

        String[] ids = {HEAD, UPPER_BODY, LOWER_BODY};
        String[] labels = {"Head", "Upper Body", "Lower Body"};
        List<List<Integer>> members =
                List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int s = 0; s < slots.slots().size(); ++s) {
            float middle = (middle(character, slots.slots().get(s)) - min.y) / height;
            int category;
            if (middle >= HEAD_FROM) {
                category = 0;
            } else if (middle >= UPPER_FROM) {
                category = 1;
            } else {
                category = 2;
            }
            members.get(category).add(s);
        }
        List<Category> categories = new ArrayList<>();
        for (int c = 0; c < ids.length; ++c) {
            if (members.get(c).isEmpty()) {
                continue;
            }
            List<Integer> parts = new ArrayList<>();
            members.get(c).forEach(s -> parts.addAll(allOptions(slots.slots().get(s))));
            Vector3f categoryMin = new Vector3f();
            Vector3f categoryMax = new Vector3f();
            bounds(character, parts, categoryMin, categoryMax);
            categories.add(
                    new Category(
                            ids[c],
                            labels[c],
                            List.copyOf(members.get(c)),
                            categoryMin,
                            categoryMax));
        }
        return new PartCategories(categories, min, max);
    }

    /**
     * How high a slot sits: the median of its options' middles, so an outfit that also fills it,
     * like coveralls in a shirt slot reaching down to the ankles, doesn't move it to another
     * category the way a box around every option would.
     *
     * @param character The character.
     * @param slot The slot.
     * @return The height of its middle, in model space.
     */
    private static float middle(ImportedCharacter character, PartSlots.Slot slot) {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        float[] middles = new float[slot.options().size()];
        for (int o = 0; o < middles.length; ++o) {
            bounds(character, slot.options().get(o), min, max);
            middles[o] = (min.y + max.y) / 2;
        }
        if (middles.length == 0) {
            return 0;
        }
        Arrays.sort(middles);
        int half = middles.length / 2;
        return middles.length % 2 == 1 ? middles[half] : (middles[half - 1] + middles[half]) / 2;
    }

    /**
     * Every part that can fill a slot.
     *
     * @param slot The slot.
     * @return The parts' indices.
     */
    private static List<Integer> allOptions(PartSlots.Slot slot) {
        List<Integer> parts = new ArrayList<>();
        slot.options().forEach(parts::addAll);
        return parts;
    }

    /**
     * The box around some parts at rest.
     *
     * @param character The character.
     * @param parts The parts' indices.
     * @param min Where to put the minimum corner.
     * @param max Where to put the maximum corner.
     */
    static void bounds(
            ImportedCharacter character, Collection<Integer> parts, Vector3f min, Vector3f max) {
        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);
        for (int index : parts) {
            ImportedCharacter.Part part = character.parts().get(index);
            min.min(part.min());
            max.max(part.max());
        }
        if (min.x > max.x) {
            min.zero();
            max.zero();
        }
    }
}
