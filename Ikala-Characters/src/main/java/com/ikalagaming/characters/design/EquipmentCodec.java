package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.definition.Kind;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes equipment as compact bytes and reads it back, against a registry both ends share, like
 * {@link DesignCodec}. It is sent apart from the design, since it changes far more often.
 *
 * <p>The layout, version {@value #FORMAT}, with counts and IDs as unsigned varints:
 *
 * <pre>
 * format (byte)
 * count, then each:
 *   slot (index in the plan's equipment slots)
 *   item (numeric ID)
 *   shown (0 for its own look, 1 for hidden, else the look shown instead's numeric ID + 2)
 *   state (0 for the first, else index in the shown look's states + 1)
 *   dye: count, then (channel index in the shown item's dye channels, code as 2 bytes) each
 * </pre>
 *
 * @author Ches Burks
 */
public final class EquipmentCodec {
    /** The encoding's version. */
    public static final int FORMAT = 1;

    /** What the shown field holds for an item's own look. */
    private static final int OWN_LOOK = 0;

    /** What the shown field holds for a hidden item. */
    private static final int HIDDEN = 1;

    /** What is added to a look's numeric ID in the shown field. */
    private static final int LOOK_OFFSET = 2;

    /**
     * Encode equipment.
     *
     * @param equipment The equipment, which should be valid.
     * @param plan The character's plan.
     * @param registry The loaded definitions.
     * @return The bytes.
     * @throws IllegalArgumentException If it refers to something that isn't loaded.
     */
    public static byte[] encode(
            @NonNull Equipment equipment, @NonNull Id plan, @NonNull CharacterRegistry registry) {
        BodyPlan body = registry.get(BodyPlan.class, plan);
        if (body == null) {
            throw new IllegalArgumentException("No loaded plan " + plan);
        }
        List<String> slotNames = slotNames(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FORMAT);
        DesignCodec.writeVarint(out, equipment.slots().size());
        for (Map.Entry<String, Equipment.Equipped> entry : equipment.slots().entrySet()) {
            Equipment.Equipped equipped = entry.getValue();
            int slot = slotNames.indexOf(entry.getKey());
            if (slot < 0) {
                throw new IllegalArgumentException("The plan has no slot " + entry.getKey());
            }
            DesignCodec.writeVarint(out, slot);
            DesignCodec.writeVarint(out, DesignCodec.numeric(registry, Kind.ITEM, equipped.item()));
            int shown = OWN_LOOK;
            if (equipped.hidden()) {
                shown = HIDDEN;
            } else if (equipped.lookAs() != null) {
                shown = DesignCodec.numeric(registry, Kind.ITEM, equipped.lookAs()) + LOOK_OFFSET;
            }
            DesignCodec.writeVarint(out, shown);
            Item item = registry.get(Item.class, equipped.shown());
            List<String> states = states(item, plan);
            int state = equipped.state() == null ? -1 : states.indexOf(equipped.state());
            if (equipped.state() != null && state < 0) {
                throw new IllegalArgumentException("No state " + equipped.state());
            }
            DesignCodec.writeVarint(out, state + 1);
            DesignCodec.writeVarint(out, equipped.dye().size());
            for (Map.Entry<String, Color> dye : equipped.dye().entrySet()) {
                int channel = item.dyeChannels().indexOf(dye.getKey());
                if (channel < 0) {
                    throw new IllegalArgumentException("Can't dye in " + dye.getKey());
                }
                DesignCodec.writeVarint(out, channel);
                int code = ColorCode.encode(dye.getValue());
                out.write(code >>> Byte.SIZE);
                out.write(code & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * Decode equipment.
     *
     * @param bytes The bytes.
     * @param plan The character's plan.
     * @param registry The loaded definitions, the same as the encoder's.
     * @return The equipment, which should still be validated.
     * @throws IllegalArgumentException If the bytes are broken or refer to definitions that aren't
     *     loaded.
     */
    public static Equipment decode(
            byte @NonNull [] bytes, @NonNull Id plan, @NonNull CharacterRegistry registry) {
        BodyPlan body = registry.get(BodyPlan.class, plan);
        if (body == null) {
            throw new IllegalArgumentException("No loaded plan " + plan);
        }
        List<String> slotNames = slotNames(body);
        ByteBuffer in = ByteBuffer.wrap(bytes);
        try {
            int format = in.get() & 0xFF;
            if (format != FORMAT) {
                throw new IllegalArgumentException("Unknown equipment format " + format);
            }
            Map<String, Equipment.Equipped> slots = new LinkedHashMap<>();
            int count = DesignCodec.readVarint(in);
            for (int i = 0; i < count; ++i) {
                String slot = DesignCodec.element(slotNames, DesignCodec.readVarint(in), "slot");
                Id item =
                        DesignCodec.definition(registry, Item.class, DesignCodec.readVarint(in))
                                .id();
                int shown = DesignCodec.readVarint(in);
                Id lookAs =
                        shown < LOOK_OFFSET
                                ? null
                                : DesignCodec.definition(registry, Item.class, shown - LOOK_OFFSET)
                                        .id();
                Item shownItem = registry.get(Item.class, lookAs == null ? item : lookAs);
                int stateIndex = DesignCodec.readVarint(in);
                String state =
                        stateIndex == 0
                                ? null
                                : DesignCodec.element(
                                        states(shownItem, plan), stateIndex - 1, "state");
                Map<String, Color> dye = new LinkedHashMap<>();
                int dyes = DesignCodec.readVarint(in);
                for (int d = 0; d < dyes; ++d) {
                    String channel =
                            DesignCodec.element(
                                    shownItem.dyeChannels(),
                                    DesignCodec.readVarint(in),
                                    "dye channel");
                    int code = (in.get() & 0xFF) << Byte.SIZE | (in.get() & 0xFF);
                    dye.put(channel, ColorCode.toColor(code));
                }
                slots.put(slot, new Equipment.Equipped(item, state, lookAs, shown == HIDDEN, dye));
            }
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("Extra bytes after the equipment");
            }
            return new Equipment(slots);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("The equipment is cut short", e);
        }
    }

    /**
     * The plan's equipment slot names, in order.
     *
     * @param body The plan.
     * @return The names.
     */
    private static List<String> slotNames(BodyPlan body) {
        return body.equipment().stream().map(BodyPlan.EquipmentSlot::id).toList();
    }

    /**
     * A look's states, in order.
     *
     * @param item The item.
     * @param plan The plan.
     * @return The states, maybe none.
     */
    private static List<String> states(Item item, Id plan) {
        Item.Look look = item.lookOn(plan);
        return look == null ? List.of() : new ArrayList<>(look.states().keySet());
    }

    /** Nothing to make. */
    private EquipmentCodec() {}
}
