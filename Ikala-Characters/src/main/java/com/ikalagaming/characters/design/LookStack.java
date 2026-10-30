package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a character shows: its design with its equipment on top. Worn items replace the design's
 * parts in their layers, held items go to sockets their slots allow, items and parts hide the
 * layers and regions they say, and items' styles come before the design's, in the order of the
 * plan's equipment slots. The real item picks the style; a look shown in its place only changes
 * what is drawn. Hidden items show nothing, so what is under them shows.
 *
 * @author Ches Burks
 */
public final class LookStack {
    /**
     * A held item, placed in a socket.
     *
     * @param slot The equipment slot it is in.
     * @param item The look drawn.
     * @param look Its look on the plan.
     * @param socket The socket it is in.
     * @param dye Its dye colors, by palette channel.
     */
    public record Held(
            @NonNull String slot,
            @NonNull Id item,
            Item.@NonNull Look look,
            BodyPlan.@NonNull Socket socket,
            @NonNull Map<String, Color> dye) {
        /**
         * How it sits in its socket.
         *
         * @return The attach, or null for its mesh as it is.
         */
        public Item.Attach attach() {
            return look.attachFor(socket);
        }
    }

    /**
     * What a character shows.
     *
     * @param worn The parts worn, by the slot or layer they are listed under.
     * @param hiddenRegions The body's mask regions hidden by items.
     * @param held The held items.
     * @param styles The styles to move in, the first winning.
     * @param fx The effects the items carry, after the design's own.
     * @param dyes Dye colors for worn items' parts, by part.
     */
    public record Shown(
            @NonNull Map<String, Id> worn,
            @NonNull Set<String> hiddenRegions,
            @NonNull List<Held> held,
            @NonNull List<Id> styles,
            @NonNull List<FxUse> fx,
            @NonNull Map<Id, Map<String, Color>> dyes) {}

    /**
     * Work out what a character shows.
     *
     * @param design The design.
     * @param equipment What it has equipped.
     * @param registry The loaded definitions.
     * @return What it shows.
     * @throws IllegalArgumentException If the design's plan isn't loaded.
     */
    public static Shown resolve(
            @NonNull Design design,
            @NonNull Equipment equipment,
            @NonNull CharacterRegistry registry) {
        BodyPlan plan = registry.get(BodyPlan.class, design.plan());
        if (plan == null) {
            throw new IllegalArgumentException("No loaded plan " + design.plan());
        }
        Map<String, Id> worn = design.worn(plan, id -> registry.get(Part.class, id));
        Set<String> hiddenRegions = new LinkedHashSet<>();
        Set<String> hiddenLayers = new HashSet<>();
        List<Held> held = new ArrayList<>();
        List<Id> styles = new ArrayList<>();
        List<FxUse> fx = new ArrayList<>(design.fx());
        Map<Id, Map<String, Color>> dyes = new HashMap<>();
        Map<String, Integer> socketUse = new HashMap<>();

        for (BodyPlan.EquipmentSlot slot : plan.equipment()) {
            Equipment.Equipped equipped = equipment.slots().get(slot.id());
            if (equipped == null) {
                continue;
            }
            Item real = registry.get(Item.class, equipped.item());
            Item.Look realLook = real == null ? null : real.lookOn(plan.id());
            if (realLook == null) {
                continue;
            }
            if (realLook.style() != null) {
                styles.add(realLook.style());
            }
            if (equipped.hidden()) {
                continue;
            }
            Item shown = registry.get(Item.class, equipped.shown());
            Item.Look look = shown == null ? null : shown.lookOn(plan.id());
            if (look == null) {
                continue;
            }
            hiddenLayers.addAll(look.hidesLayers());
            hiddenRegions.addAll(look.hidesRegions());
            if (look.part() != null) {
                fx.addAll(look.fx());
                Part part = registry.get(Part.class, look.part());
                if (part != null) {
                    putWorn(worn, part, registry);
                    if (!equipped.dye().isEmpty()) {
                        dyes.put(part.id(), equipped.dye());
                    }
                }
            } else {
                BodyPlan.Socket socket = place(plan, slot, look, equipped, socketUse);
                if (socket != null) {
                    Held item = new Held(slot.id(), shown.id(), look, socket, equipped.dye());
                    held.add(item);
                    for (FxUse use : look.fx()) {
                        fx.add(onItem(use, item));
                    }
                }
            }
        }
        if (design.style() != null) {
            styles.add(design.style());
        }
        for (Id partId : worn.values()) {
            Part part = registry.get(Part.class, partId);
            if (part != null) {
                hiddenLayers.addAll(part.hidesLayers());
            }
        }
        worn.entrySet()
                .removeIf(
                        entry -> {
                            Part part = registry.get(Part.class, entry.getValue());
                            return part != null
                                    && part.kind() == Part.PartKind.LAYER
                                    && part.fills().stream().anyMatch(hiddenLayers::contains);
                        });
        return new Shown(worn, hiddenRegions, held, styles, fx, dyes);
    }

    /**
     * An effect of a held item, moved onto the item's socket if it sits on the item.
     *
     * @param use The effect.
     * @param item The held item.
     * @return The effect as placed.
     */
    private static FxUse onItem(FxUse use, Held item) {
        if (use.bone() != null || use.socket() != null) {
            return use;
        }
        Matrix4f attach = new Matrix4f();
        Item.Attach how = item.attach();
        if (how != null && how.offset() != null) {
            attach.translate(how.offset().x(), how.offset().y(), how.offset().z());
        }
        if (how != null && how.rotate() != null) {
            attach.rotateXYZ(
                    (float) Math.toRadians(how.rotate().x()),
                    (float) Math.toRadians(how.rotate().y()),
                    (float) Math.toRadians(how.rotate().z()));
        }
        return use.onSocket(
                item.socket().id(),
                moved(attach, use.offsetOrZero()),
                use.to() == null ? null : moved(attach, use.to()));
    }

    /**
     * A point moved by a matrix.
     *
     * @param matrix The matrix.
     * @param point The point.
     * @return The moved point.
     */
    private static Vec3 moved(Matrix4f matrix, Vec3 point) {
        Vector3f at = matrix.transformPosition(new Vector3f(point.x(), point.y(), point.z()));
        return new Vec3(at.x, at.y, at.z);
    }

    /**
     * Wear a part, taking off whatever filled the places it fills.
     *
     * @param worn The parts worn, changed.
     * @param part The part.
     * @param registry The loaded definitions.
     */
    private static void putWorn(Map<String, Id> worn, Part part, CharacterRegistry registry) {
        worn.entrySet()
                .removeIf(
                        entry -> {
                            Part other = registry.get(Part.class, entry.getValue());
                            return other != null
                                    && other.fills().stream().anyMatch(part.fills()::contains);
                        });
        Map<String, Id> reordered = new LinkedHashMap<>(worn);
        reordered.put(part.fills().get(0), part.id());
        worn.clear();
        worn.putAll(reordered);
    }

    /**
     * The socket a held item goes in: the first its state names that its slot allows and that has
     * room.
     *
     * @param plan The plan.
     * @param slot The equipment slot.
     * @param look The item's look.
     * @param equipped The equipped item.
     * @param socketUse How many items each socket holds so far, changed.
     * @return The socket, or null if none has room.
     */
    private static BodyPlan.Socket place(
            BodyPlan plan,
            BodyPlan.EquipmentSlot slot,
            Item.Look look,
            Equipment.Equipped equipped,
            Map<String, Integer> socketUse) {
        String state = equipped.state();
        if (state == null || !look.states().containsKey(state)) {
            state = look.firstState();
        }
        if (state == null) {
            return null;
        }
        Set<BodyPlan.Socket> allowed = new LinkedHashSet<>();
        slot.sockets().forEach(listed -> allowed.addAll(plan.socketsFor(listed)));
        for (BodyPlan.Socket socket : plan.socketsFor(look.states().get(state))) {
            int used = socketUse.getOrDefault(socket.id(), 0);
            if (allowed.contains(socket) && used < socket.max()) {
                socketUse.put(socket.id(), used + 1);
                return socket;
            }
        }
        return null;
    }

    /** Nothing to make. */
    private LookStack() {}
}
