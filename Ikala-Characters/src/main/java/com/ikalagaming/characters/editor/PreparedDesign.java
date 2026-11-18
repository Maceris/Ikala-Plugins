package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.PartAssembler;
import com.ikalagaming.characters.view.PartRegions;
import com.ikalagaming.characters.view.PartSlots;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A design built from its plan's parts and sorted into slots, ready to show, which is slow enough
 * to do off the render thread. Every part of the plan with a mesh for the design's variant is
 * loaded, so choosing parts doesn't load anything.
 *
 * @param design The design.
 * @param plan Its plan.
 * @param character Every part's meshes, merged, with base parts split along the regions' borders.
 * @param slots The plan's slots and layers that have parts, each offering the parts that fill it
 *     first.
 * @param regions What the layers cover.
 * @param optionIds For each slot, the part each of its options is.
 * @param optionParts For each slot, the definition of the part each of its options is.
 * @param formMeshes For each slot, each option's meshes by clothing form, for layers with meshes
 *     for bulky body parts.
 * @param fills For each slot, the places each of its options fills.
 * @param channels For each of the character's parts, the palette channel each tint mask channel
 *     takes, as a position in the plan's channels.
 * @param channelNames The palette channels the parts use, in the plan's order.
 */
record PreparedDesign(
        Design design,
        BodyPlan plan,
        ImportedCharacter character,
        PartSlots slots,
        PartRegions regions,
        List<List<Id>> optionIds,
        List<List<Part>> optionParts,
        List<List<Map<String, List<Integer>>>> formMeshes,
        List<List<Set<String>>> fills,
        List<int[]> channels,
        List<int[]> fallbacks,
        List<Part> partDefinitions,
        List<String> channelNames) {
    /**
     * Build a design's parts and sort them.
     *
     * @param design The design.
     * @param registry The loaded definitions.
     * @param reference The rig's reference file.
     * @param files Files read before, added to.
     * @return The prepared design.
     * @throws IllegalArgumentException If its plan or parts can't be loaded.
     */
    static PreparedDesign build(
            Design design,
            CharacterRegistry registry,
            Path reference,
            Map<Path, ImportedCharacter> files) {
        BodyPlan plan = registry.get(BodyPlan.class, design.plan());
        if (plan == null) {
            throw new IllegalArgumentException("No loaded plan " + design.plan());
        }
        Map<String, Id> worn = design.worn(plan, id -> registry.get(Part.class, id));
        Set<Id> wornIds = new HashSet<>(worn.values());
        // The worn parts first, so their files' clips are the ones kept
        List<Part> candidates =
                registry.all(Part.class).stream()
                        .filter(
                                part ->
                                        part.plan().equals(plan.id())
                                                && !part.places().isEmpty()
                                                && PartAssembler.meshFor(part, design.variant())
                                                        != null)
                        .sorted(Comparator.comparing(part -> !wornIds.contains(part.id())))
                        .toList();
        PartAssembler.Assembled assembled =
                // Every clothing form's meshes too, which layers swap to over bulky parts
                PartAssembler.assemble(
                        candidates, plan, design.variant(), registry, reference, files, true);
        if (assembled == null) {
            throw new IllegalArgumentException("None of " + plan.id() + "'s parts loaded");
        }

        List<String> places = new ArrayList<>();
        plan.slots().forEach(slot -> places.add(slot.id()));
        plan.layers().forEach(layer -> places.add(layer.id()));
        plan.sockets().forEach(socket -> places.add(socket.id()));
        // A chain named like a layer, like the cape, is that layer's place
        plan.extensions().stream()
                .map(BodyPlan.Extension::id)
                .filter(chain -> !places.contains(chain))
                .forEach(places::add);
        List<PartSlots.Slot> slots = new ArrayList<>();
        List<List<Id>> optionIds = new ArrayList<>();
        List<List<Part>> optionParts = new ArrayList<>();
        List<List<Map<String, List<Integer>>>> formMeshes = new ArrayList<>();
        List<List<Set<String>>> fills = new ArrayList<>();
        for (String place : places) {
            Part.PartKind wanted =
                    plan.layer(place) != null
                            ? Part.PartKind.LAYER
                            : plan.socket(place) != null || plan.extension(place) != null
                                    ? Part.PartKind.ACCESSORY
                                    : Part.PartKind.BODY;
            List<List<Integer>> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<Id> ids = new ArrayList<>();
            List<Part> defined = new ArrayList<>();
            List<Map<String, List<Integer>>> optionForms = new ArrayList<>();
            List<Set<String>> optionFills = new ArrayList<>();
            for (Part part : candidates.stream().sorted(Comparator.comparing(Part::id)).toList()) {
                List<Integer> meshes = assembled.partsOf(part.id());
                if (part.kind() != wanted
                        || !part.places().get(0).equals(place)
                        || meshes.isEmpty()) {
                    continue;
                }
                options.add(meshes);
                labels.add(label(part.id()));
                ids.add(part.id());
                defined.add(part);
                Map<String, List<Integer>> byForm = new LinkedHashMap<>();
                for (String form : PartAssembler.formsOf(part, design.variant(), plan)) {
                    List<Integer> formParts = assembled.partsOf(part.id(), form);
                    if (!formParts.isEmpty()) {
                        byForm.put(form, formParts);
                    }
                }
                optionForms.add(byForm);
                optionFills.add(Set.copyOf(part.fills()));
            }
            if (options.isEmpty()) {
                continue;
            }
            int initial = ids.indexOf(worn.get(place));
            // Layers and sockets can be empty; body slots only while another part covers them
            boolean optional = wanted != Part.PartKind.BODY;
            slots.add(new PartSlots.Slot(place, options, labels, optional, initial));
            optionIds.add(ids);
            optionParts.add(defined);
            formMeshes.add(optionForms);
            fills.add(optionFills);
        }

        ImportedCharacter character = assembled.character();
        boolean[] base = new boolean[character.parts().size()];
        boolean[] wearable = new boolean[character.parts().size()];
        // Where each layer part stacks: the highest of the layers it fills, so an outfit
        // filling a shirt and pants layer goes under a jacket
        int[] order = new int[character.parts().size()];
        for (int p = 0; p < base.length; ++p) {
            Part part = registry.get(Part.class, assembled.partIds().get(p));
            base[p] = part.kind() == Part.PartKind.BODY;
            // A layer on a chain, like a cape, swings away from the body, so it hides none
            wearable[p] = part.kind() == Part.PartKind.LAYER && part.extension() == null;
            order[p] = Integer.MIN_VALUE;
            for (String fill : part.fills()) {
                BodyPlan.Layer layer = plan.layer(fill);
                if (layer != null) {
                    order[p] = Math.max(order[p], layer.order());
                }
            }
        }
        PartRegions regions = PartRegions.of(character, base, wearable, order);

        List<String> all = plan.allChannels();
        Set<Integer> used = new TreeSet<>();
        for (int[] mapped : assembled.channels()) {
            for (int channel : mapped) {
                if (channel >= 0) {
                    used.add(channel);
                }
            }
        }
        return new PreparedDesign(
                design,
                plan,
                regions.character(),
                new PartSlots(slots),
                regions,
                optionIds,
                optionParts,
                formMeshes,
                fills,
                assembled.channels(),
                assembled.fallbacks(),
                assembled.partIds().stream().map(id -> registry.get(Part.class, id)).toList(),
                used.stream().map(all::get).toList());
    }

    /**
     * What an editor calls a part: the last step of its ID's path, with spaces.
     *
     * @param id The part's ID.
     * @return The label.
     */
    static String label(Id id) {
        String path = id.path();
        return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
    }

    /**
     * Find the slot and option that offer a part.
     *
     * @param optionIds For each slot, the part each of its options is.
     * @param partId The part's ID, like "gen:part/body/robot_leg_l".
     * @return The slot's and option's positions, or null if no slot offers it.
     */
    static int[] findOption(@NonNull List<List<Id>> optionIds, @NonNull String partId) {
        for (int slot = 0; slot < optionIds.size(); ++slot) {
            List<Id> ids = optionIds.get(slot);
            for (int option = 0; option < ids.size(); ++option) {
                if (ids.get(option).toString().equals(partId)) {
                    return new int[] {slot, option};
                }
            }
        }
        return null;
    }
}
