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
                || sharedExclusiveTag(plan, a, b) != null;
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
     * is dropped if it clashes with one kept already, so later entries win, as the last part put
     * on does in the editor and worn items do over the design. Then parts whose required parts
     * were dropped or never worn go too, until none are left to drop.
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
        return hidden.contains(socket.id()) || (socket.type() != null && hidden.contains(socket.type()));
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
