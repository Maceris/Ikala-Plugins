package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The rules for what can be worn together, shared by the editor, the shown look and design checks
 * so they never disagree:
 *
 * <ul>
 *   <li>Two parts clash if either lists the other in its conflicts, or they share one of the plan's
 *       exclusive tags, like two "cloth" pieces.
 *   <li>A part needs the parts it requires.
 *   <li>Worn parts hide layers, sockets (the accessories and effects there) and sliders they name.
 * </ul>
 *
 * @author Ches Burks
 */
public final class WearRules {
    private WearRules() {}

    /**
     * Whether two parts can't be worn together.
     *
     * @param plan The plan.
     * @param a One part.
     * @param b The other.
     * @return True if either conflicts with the other, or they share an exclusive tag.
     */
    public static boolean clash(@NonNull BodyPlan plan, @NonNull Part a, @NonNull Part b) {
        if (a.id().equals(b.id())) {
            return false;
        }
        return a.conflicts().contains(b.id())
                || b.conflicts().contains(a.id())
                || (!paired(a, b) && sharedExclusiveTag(plan, a, b) != null)
                || blocksClothing(plan, a, b)
                || blocksClothing(plan, b, a);
    }

    /**
     * Whether two parts are each other's other side, like a pair of wings, which are worn together
     * even when they share an exclusive tag.
     *
     * @param a One part.
     * @param b The other.
     * @return True if either names the other as its pair.
     */
    public static boolean paired(@NonNull Part a, @NonNull Part b) {
        return b.id().equals(a.pair()) || a.id().equals(b.pair());
    }

    /**
     * Whether a body part worn bare keeps a layer off: the layer covers one of its slots. A t-shirt
     * leaving the arms bare still goes over spiked arms; a jacket doesn't.
     *
     * @param plan The plan.
     * @param body The body part.
     * @param layer The layer.
     * @return True if they can't be worn together.
     */
    public static boolean blocksClothing(
            @NonNull BodyPlan plan, @NonNull Part body, @NonNull Part layer) {
        if (body.kind() != Part.PartKind.BODY
                || !body.bare()
                || layer.kind() != Part.PartKind.LAYER) {
            return false;
        }
        Set<String> covered = layer.coveredSlots(plan);
        return body.fills().stream().anyMatch(covered::contains);
    }

    /**
     * An exclusive tag two parts share.
     *
     * @param plan The plan, whose exclusive tags count.
     * @param a One part.
     * @param b The other.
     * @return The first such tag in the plan's order, or null if they share none.
     */
    public static String sharedExclusiveTag(
            @NonNull BodyPlan plan, @NonNull Part a, @NonNull Part b) {
        return plan.sharedExclusiveTag(a, b);
    }

    /**
     * Keep only parts that can be worn together: walking from the last entry to the first, a part
     * is dropped if it clashes with one kept already, so later entries win, as the last part put on
     * does in the editor and worn items do over the design. Then parts whose required parts were
     * dropped or never worn go too, until none are left to drop.
     *
     * @param plan The plan.
     * @param worn The parts by the place they are listed under, in order; not changed.
     * @param find Finds a part, or gives null; parts not found are kept as they are.
     * @return The parts kept, in the same order.
     */
    public static Map<String, Id> settle(
            @NonNull BodyPlan plan,
            @NonNull Map<String, Id> worn,
            @NonNull Function<Id, Part> find) {
        List<Map.Entry<String, Id>> entries = new ArrayList<>(worn.entrySet());
        List<Part> kept = new ArrayList<>();
        Set<String> dropped = new HashSet<>();
        for (int i = entries.size() - 1; i >= 0; --i) {
            Part part = find.apply(entries.get(i).getValue());
            if (part == null) {
                continue;
            }
            if (kept.stream().anyMatch(other -> clash(plan, part, other))) {
                dropped.add(entries.get(i).getKey());
            } else {
                kept.add(part);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<Id> present = new HashSet<>();
            for (Map.Entry<String, Id> entry : entries) {
                if (!dropped.contains(entry.getKey())) {
                    present.add(entry.getValue());
                }
            }
            for (Map.Entry<String, Id> entry : entries) {
                if (dropped.contains(entry.getKey())) {
                    continue;
                }
                Part part = find.apply(entry.getValue());
                if (part != null && !present.containsAll(part.requires())) {
                    dropped.add(entry.getKey());
                    changed = true;
                }
            }
        }
        Map<String, Id> settled = new LinkedHashMap<>();
        for (Map.Entry<String, Id> entry : entries) {
            if (!dropped.contains(entry.getKey())) {
                settled.put(entry.getKey(), entry.getValue());
            }
        }
        return settled;
    }

    /**
     * The clothing form a layer is shown in: the thickest form among worn body parts that fill a
     * slot the layer covers.
     *
     * @param plan The plan.
     * @param layer The layer part.
     * @param worn The parts worn.
     * @return The form's name, or null for the bare body's, when no body part under it has a
     *     thicker one.
     */
    public static String formFor(
            @NonNull BodyPlan plan, @NonNull Part layer, @NonNull Collection<Part> worn) {
        Set<String> covered = new HashSet<>();
        for (String fill : layer.fills()) {
            BodyPlan.Layer place = plan.layer(fill);
            if (place != null) {
                covered.addAll(place.covers());
            }
        }
        int thickest = 0;
        for (Part part : worn) {
            if (part.kind() == Part.PartKind.BODY
                    && part.form() != null
                    && part.fills().stream().anyMatch(covered::contains)) {
                thickest = Math.max(thickest, plan.formIndex(part.form()));
            }
        }
        return thickest > 0 ? plan.forms().get(thickest).id() : null;
    }

    /**
     * The clothing form of the body part worn in each body slot, for layers to push out over it
     * slot by slot (see {@link com.ikalagaming.characters.view.PartForms}).
     *
     * @param plan The body plan, with its forms.
     * @param worn The parts worn.
     * @return The form by slot, the thickest where parts share one; slots with no body part or a
     *     body part of the first form are left out.
     */
    public static Map<String, String> slotForms(
            @NonNull BodyPlan plan, @NonNull Collection<Part> worn) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Part part : worn) {
            int index =
                    part.kind() == Part.PartKind.BODY && part.form() != null
                            ? plan.formIndex(part.form())
                            : -1;
            if (index <= 0) {
                continue;
            }
            for (String slot : part.fills()) {
                String held = forms.get(slot);
                if (held == null || plan.formIndex(held) < index) {
                    forms.put(slot, part.form());
                }
            }
        }
        return forms;
    }

    /**
     * How far the body parts worn stand out from the bare body over each body slot, which clothing
     * over them is pushed out by (see {@link com.ikalagaming.characters.view.PartForms}): a part's
     * own measure for the slot, else its form's share, the furthest where parts share a slot.
     *
     * @param plan The body plan, with its forms.
     * @param worn The parts worn.
     * @return The distance as a share of each slot's radius; slots the bare body's are left out.
     */
    public static Map<String, Float> slotStandoffs(
            @NonNull BodyPlan plan, @NonNull Collection<Part> worn) {
        Map<String, Float> standoffs = new LinkedHashMap<>();
        for (Part part : worn) {
            if (part.kind() != Part.PartKind.BODY) {
                continue;
            }
            for (String slot : part.fills()) {
                Float own = part.standoffOver(slot);
                float standoff = own != null ? own : plan.formShare(part.form());
                if (standoff > standoffs.getOrDefault(slot, 0f)) {
                    standoffs.put(slot, standoff);
                }
            }
        }
        return standoffs;
    }

    /**
     * The layers some worn parts hide.
     *
     * @param worn The parts.
     * @return The layer names.
     */
    public static Set<String> hiddenLayers(@NonNull Collection<Part> worn) {
        Set<String> hidden = new LinkedHashSet<>();
        worn.forEach(part -> hidden.addAll(part.hidesLayers()));
        return hidden;
    }

    /**
     * The sockets, by name or type, some worn parts cover.
     *
     * @param worn The parts.
     * @return The socket names and types.
     */
    public static Set<String> hiddenSockets(@NonNull Collection<Part> worn) {
        Set<String> hidden = new LinkedHashSet<>();
        worn.forEach(part -> hidden.addAll(part.hidesSockets()));
        return hidden;
    }

    /**
     * Whether a socket is covered.
     *
     * @param socket The socket.
     * @param hidden The socket names and types covered.
     * @return True if its name or type is among them.
     */
    public static boolean hides(@NonNull BodyPlan.Socket socket, @NonNull Set<String> hidden) {
        return hidden.contains(socket.id())
                || (socket.type() != null && hidden.contains(socket.type()));
    }

    /**
     * Whether a part locks a slider while worn.
     *
     * @param part The part.
     * @param slider The slider.
     * @return True if it names the slider's ID or its editor group.
     */
    public static boolean locks(@NonNull Part part, @NonNull Slider slider) {
        return part.locksSliders().contains(slider.id().toString())
                || (slider.group() != null && part.locksSliders().contains(slider.group()));
    }
}
