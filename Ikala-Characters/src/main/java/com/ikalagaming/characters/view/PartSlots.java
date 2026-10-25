package com.ikalagaming.characters.view;

import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An imported character's parts sorted into slots by their names, until parts come from definitions
 * with real slots. Modular characters, like the RPG characters, name their parts like {@code
 * human_male_head_2} or {@code human_male_light_armor}: the word before any number or {@code
 * _default} is the slot, so the heads are one slot and the armors another. Slots with a {@code
 * _default} part always wear one; the rest can be empty, and start empty but for hair. A character
 * without default parts wears every part, one slot each.
 *
 * @param slots The slots, in the order their first parts come in the file.
 * @author Ches Burks
 */
public record PartSlots(@NonNull List<Slot> slots) {
    /** What default parts' names end with. */
    private static final String DEFAULT_SUFFIX = "_default";

    /** A number at the end of a name, like {@code _2}. */
    private static final Pattern NUMBER_SUFFIX = Pattern.compile("_\\d+$");

    /** The end the importer gives each primitive of a mesh with several, like {@code .1}. */
    private static final Pattern PRIMITIVE_SUFFIX = Pattern.compile("\\.\\d+$");

    /** The slot that starts filled though it has no default, so modular characters aren't bald. */
    private static final String HAIR = "hair";

    /**
     * One slot.
     *
     * @param id The slot's name, like "head".
     * @param options What can fill it: each a mesh, as the indices of its parts, one per primitive.
     * @param labels What each option is called, without the name the meshes share.
     * @param optional Whether it can be empty.
     * @param initial The position in {@code options} of what it starts with, or -1 for empty.
     */
    public record Slot(
            @NonNull String id,
            @NonNull List<List<Integer>> options,
            @NonNull List<String> labels,
            boolean optional,
            int initial) {}

    /**
     * Sort a character's parts into slots.
     *
     * @param character The character.
     * @return The slots.
     */
    public static PartSlots of(@NonNull ImportedCharacter character) {
        // The primitives of each mesh, which go together
        Map<String, List<Integer>> meshes = new LinkedHashMap<>();
        List<ImportedCharacter.Part> parts = character.parts();
        for (int i = 0; i < parts.size(); ++i) {
            String mesh = PRIMITIVE_SUFFIX.matcher(parts.get(i).name()).replaceFirst("");
            meshes.computeIfAbsent(mesh, m -> new ArrayList<>()).add(i);
        }
        List<String> names = new ArrayList<>(meshes.keySet());
        boolean modular = names.stream().anyMatch(name -> name.endsWith(DEFAULT_SUFFIX));
        String prefix = sharedPrefix(names);
        Map<String, List<String>> bySlot = new LinkedHashMap<>();
        for (String name : names) {
            String slot = modular ? slotOf(name) : label(name, prefix);
            bySlot.computeIfAbsent(slot, s -> new ArrayList<>()).add(name);
        }
        List<Slot> slots = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : bySlot.entrySet()) {
            List<String> members = entry.getValue();
            List<List<Integer>> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            int defaultPart = -1;
            for (int j = 0; j < members.size(); ++j) {
                String name = members.get(j);
                options.add(meshes.get(name));
                labels.add(label(name, prefix));
                if (name.endsWith(DEFAULT_SUFFIX)) {
                    defaultPart = j;
                }
            }
            boolean optional = modular && defaultPart < 0;
            int initial;
            if (!modular) {
                initial = 0;
            } else if (defaultPart >= 0) {
                initial = defaultPart;
            } else {
                initial = HAIR.equals(entry.getKey()) ? 0 : -1;
            }
            slots.add(new Slot(entry.getKey(), options, labels, optional, initial));
        }
        return new PartSlots(slots);
    }

    /**
     * The slot a modular part's name puts it in.
     *
     * @param name The part's name.
     * @return The slot.
     */
    static String slotOf(@NonNull String name) {
        String stem = name.toLowerCase(Locale.ROOT);
        if (stem.endsWith(DEFAULT_SUFFIX)) {
            stem = stem.substring(0, stem.length() - DEFAULT_SUFFIX.length());
        }
        stem = NUMBER_SUFFIX.matcher(stem).replaceFirst("");
        int underscore = stem.lastIndexOf('_');
        return underscore < 0 ? stem : stem.substring(underscore + 1);
    }

    /**
     * The longest start of the names, up to an underscore, that more than half of them share, like
     * {@code human_male_}.
     *
     * @param names The names.
     * @return The shared start, or empty if there is none.
     */
    static String sharedPrefix(@NonNull List<String> names) {
        Map<String, Integer> counts = new HashMap<>();
        for (String name : names) {
            for (int at = name.indexOf('_'); at >= 0; at = name.indexOf('_', at + 1)) {
                counts.merge(name.substring(0, at + 1), 1, Integer::sum);
            }
        }
        String best = "";
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() * 2 > names.size() && entry.getKey().length() > best.length()) {
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * What a part is called in an editor.
     *
     * @param name The part's name.
     * @param prefix The start most names share.
     * @return The name without that start, with spaces for underscores.
     */
    private static String label(String name, String prefix) {
        String rest =
                name.startsWith(prefix) && name.length() > prefix.length()
                        ? name.substring(prefix.length())
                        : name;
        return rest.replace('_', ' ');
    }

    /**
     * The parts the slots start with.
     *
     * @return The indices of the parts.
     */
    public List<Integer> initialParts() {
        List<Integer> shown = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.initial() >= 0) {
                shown.addAll(slot.options().get(slot.initial()));
            }
        }
        return shown;
    }
}
