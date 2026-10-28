package com.ikalagaming.characters.design;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Definition;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Kind;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes designs as compact bytes for the network and reads them back, against a registry both ends
 * share (see {@link CharacterRegistry#getContentHash()}). Definitions go by their numeric IDs,
 * variants, slots, layers and channels by their position in the plan, slider values as signed
 * bytes, and colors as {@link ColorCode}s, so a design is a few dozen bytes. Clients can cache
 * designs by {@link #hash}.
 *
 * <p>The layout, version {@value #FORMAT}, with counts and IDs as unsigned varints:
 *
 * <pre>
 * format (byte)
 * plan (numeric ID)   variant (index in the plan)   style (0 for none, else numeric ID + 1)
 * parts:   count, then (place index: slots then layers in plan order, part numeric ID) each
 * sliders: count, then (slider numeric ID, value count byte, values as signed bytes) each
 * colors:  count, then (channel index: standard channels then the plan's own, code as 2 bytes)
 * palette (0 for none, else numeric ID + 1)
 * fx:      count, then each:
 *          effect numeric ID
 *          place (socket index in the plan, or socket count + bone index in the rig)
 *          color (0 for none, else channel index + 1)
 *          offset (3 signed bytes of centimeters)
 *          scale, rate, intensity (unsigned bytes of 1/{@value #FACTOR_STEPS})
 *          when (length byte, 0 for always, then ASCII)
 * </pre>
 *
 * Encoding is lossy where the steps are coarse: slider values go to steps of 1/{@value #STEPS},
 * covering -2 to 2, colors to the nearest code, effect offsets to centimeters and their factors to
 * steps of 1/{@value #FACTOR_STEPS}. Editors snap to all of them, so their designs survive the trip
 * unchanged.
 *
 * @author Ches Burks
 */
public final class DesignCodec {
    /** The encoding's version. */
    public static final int FORMAT = 1;

    /** Slider steps per unit: a signed byte covers -2 to 2, with 1 and 2 exactly on a step. */
    public static final float STEPS = 63;

    /** Effect scale, rate and intensity steps per unit: an unsigned byte covers 0 to 4. */
    public static final float FACTOR_STEPS = 32;

    /** Effect offset steps per meter: a signed byte of centimeters. */
    public static final float OFFSET_STEPS = 100;

    /** The largest effect factor step. */
    private static final int MAX_FACTOR_STEP = 0xFF;

    /** The largest slider step, either way. */
    private static final int MAX_STEP = 127;

    /** The bits a varint byte holds. */
    private static final int VARINT_BITS = 7;

    /** The bit that says more varint bytes follow. */
    private static final int VARINT_MORE = 0x80;

    /** How many hex digits of the SHA-256 a design's hash keeps. */
    private static final int HASH_DIGITS = 16;

    /**
     * Encode a design.
     *
     * @param design The design, which should be valid against the registry.
     * @param registry The loaded definitions.
     * @return The bytes.
     * @throws IllegalArgumentException If something it refers to isn't loaded.
     */
    public static byte[] encode(@NonNull Design design, @NonNull CharacterRegistry registry) {
        BodyPlan plan = registry.get(BodyPlan.class, design.plan());
        if (plan == null) {
            throw new IllegalArgumentException("No loaded plan " + design.plan());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FORMAT);
        writeVarint(out, numeric(registry, Kind.BODY_PLAN, design.plan()));
        int variant = plan.variants().indexOf(plan.variant(design.variant()));
        if (variant < 0) {
            throw new IllegalArgumentException("The plan has no variant " + design.variant());
        }
        writeVarint(out, variant);
        writeVarint(
                out,
                design.style() == null
                        ? 0
                        : numeric(registry, Kind.ANIM_STYLE, design.style()) + 1);

        List<String> places = places(plan);
        writeVarint(out, design.parts().size() + design.layers().size());
        for (Map<String, Id> listed : List.of(design.parts(), design.layers())) {
            for (Map.Entry<String, Id> part : listed.entrySet()) {
                writeVarint(out, index(places, part.getKey(), "slot or layer"));
                writeVarint(out, numeric(registry, Kind.PART, part.getValue()));
            }
        }

        writeVarint(out, design.sliders().size());
        for (Map.Entry<String, List<Float>> slider : design.sliders().entrySet()) {
            writeVarint(out, numeric(registry, Kind.SLIDER, Id.parse(slider.getKey())));
            out.write(slider.getValue().size());
            for (float value : slider.getValue()) {
                out.write(step(value));
            }
        }

        List<String> channels = plan.allChannels();
        writeVarint(out, design.colors().size());
        for (Map.Entry<String, Color> color : design.colors().entrySet()) {
            writeVarint(out, index(channels, color.getKey(), "palette channel"));
            int code = ColorCode.encode(color.getValue());
            out.write(code >>> Byte.SIZE);
            out.write(code & 0xFF);
        }
        writeVarint(
                out,
                design.palette() == null
                        ? 0
                        : numeric(registry, Kind.PALETTE, design.palette()) + 1);

        Rig rig = rigOf(registry, plan);
        writeVarint(out, design.fx().size());
        for (FxUse use : design.fx()) {
            writeVarint(out, numeric(registry, Kind.FX, use.fx()));
            writeVarint(out, place(plan, rig, use));
            writeVarint(out, use.color() == null ? 0 : index(channels, use.color(), "channel") + 1);
            Vec3 offset = use.offsetOrZero();
            out.write(offsetStep(offset.x()));
            out.write(offsetStep(offset.y()));
            out.write(offsetStep(offset.z()));
            out.write(factorStep(use.scale()));
            out.write(factorStep(use.rate()));
            out.write(factorStep(use.intensity()));
            writeWhen(out, use.when());
        }
        return out.toByteArray();
    }

    /**
     * Decode a design.
     *
     * @param bytes The bytes.
     * @param id The ID to give it, since IDs aren't sent.
     * @param registry The loaded definitions, the same as the encoder's.
     * @return The design, which should still be validated.
     * @throws IllegalArgumentException If the bytes are broken or refer to definitions that aren't
     *     loaded.
     */
    public static Design decode(
            byte @NonNull [] bytes, @NonNull Id id, @NonNull CharacterRegistry registry) {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        try {
            int format = in.get() & 0xFF;
            if (format != FORMAT) {
                throw new IllegalArgumentException("Unknown design format " + format);
            }
            BodyPlan plan = definition(registry, BodyPlan.class, readVarint(in));
            BodyPlan.Variant variant = element(plan.variants(), readVarint(in), "variant");
            int style = readVarint(in);
            Id styleId = style == 0 ? null : definition(registry, AnimStyle.class, style - 1).id();

            List<String> places = places(plan);
            Map<String, Id> parts = new LinkedHashMap<>();
            Map<String, Id> layers = new LinkedHashMap<>();
            int partCount = readVarint(in);
            for (int p = 0; p < partCount; ++p) {
                int place = readVarint(in);
                String name = element(places, place, "slot or layer");
                Id part = definition(registry, Part.class, readVarint(in)).id();
                (place < plan.slots().size() ? parts : layers).put(name, part);
            }

            Map<String, List<Float>> sliders = new LinkedHashMap<>();
            int sliderCount = readVarint(in);
            for (int s = 0; s < sliderCount; ++s) {
                Slider slider = definition(registry, Slider.class, readVarint(in));
                int count = in.get() & 0xFF;
                List<Float> values = new ArrayList<>();
                for (int v = 0; v < count; ++v) {
                    values.add(in.get() / STEPS);
                }
                sliders.put(slider.id().toString(), values);
            }

            List<String> channels = plan.allChannels();
            Map<String, Color> colors = new LinkedHashMap<>();
            int colorCount = readVarint(in);
            for (int c = 0; c < colorCount; ++c) {
                String channel = element(channels, readVarint(in), "palette channel");
                int code = (in.get() & 0xFF) << Byte.SIZE | (in.get() & 0xFF);
                colors.put(channel, ColorCode.toColor(code));
            }
            int palette = readVarint(in);
            Id paletteId =
                    palette == 0 ? null : definition(registry, Palette.class, palette - 1).id();

            Rig rig = rigOf(registry, plan);
            List<FxUse> fx = new ArrayList<>();
            int fxCount = readVarint(in);
            for (int f = 0; f < fxCount; ++f) {
                Id effect = definition(registry, Fx.class, readVarint(in)).id();
                int place = readVarint(in);
                String socket = null;
                String bone = null;
                if (place < plan.sockets().size()) {
                    socket = plan.sockets().get(place).id();
                } else {
                    bone = element(rig.bones(), place - plan.sockets().size(), "bone").name();
                }
                int color = readVarint(in);
                String channel = color == 0 ? null : element(channels, color - 1, "channel");
                Vec3 offset =
                        new Vec3(
                                in.get() / OFFSET_STEPS,
                                in.get() / OFFSET_STEPS,
                                in.get() / OFFSET_STEPS);
                float scale = (in.get() & 0xFF) / FACTOR_STEPS;
                float rate = (in.get() & 0xFF) / FACTOR_STEPS;
                float intensity = (in.get() & 0xFF) / FACTOR_STEPS;
                String when = readWhen(in);
                fx.add(
                        new FxUse(
                                effect,
                                bone,
                                socket,
                                Vec3.ZERO.equals(offset) ? null : offset,
                                channel,
                                scale,
                                rate,
                                intensity,
                                when));
            }
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("Extra bytes after the design");
            }
            return new Design(
                    id,
                    plan.id(),
                    variant.id(),
                    styleId,
                    sliders,
                    parts,
                    layers,
                    paletteId,
                    colors,
                    fx);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("The design is cut short", e);
        }
    }

    /**
     * A short hash of a design's bytes, for caching designs by content.
     *
     * @param bytes The encoded design.
     * @return The first {@value #HASH_DIGITS} hex digits of its SHA-256.
     */
    public static String hash(byte @NonNull [] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest).substring(0, HASH_DIGITS);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to have SHA-256
            throw new IllegalStateException(e);
        }
    }

    /**
     * A slider value as a signed step.
     *
     * @param value The value.
     * @return The step, -127 to 127.
     */
    public static int step(float value) {
        return Math.clamp(Math.round(value * STEPS), -MAX_STEP, MAX_STEP);
    }

    /**
     * A slider value snapped to what the encoding holds.
     *
     * @param value The value.
     * @return The nearest value a design's bytes can hold.
     */
    public static float snap(float value) {
        return step(value) / STEPS;
    }

    /**
     * An effect offset as a signed step.
     *
     * @param meters The offset along one axis.
     * @return The step, in centimeters.
     */
    public static int offsetStep(float meters) {
        return Math.clamp(Math.round(meters * OFFSET_STEPS), -MAX_STEP, MAX_STEP);
    }

    /**
     * An effect offset snapped to what the encoding holds.
     *
     * @param meters The offset along one axis.
     * @return The nearest centimeter.
     */
    public static float snapOffset(float meters) {
        return offsetStep(meters) / OFFSET_STEPS;
    }

    /**
     * An effect's scale, rate or intensity as an unsigned step.
     *
     * @param factor The factor.
     * @return The step, 0 to 255.
     */
    public static int factorStep(float factor) {
        return Math.clamp(Math.round(factor * FACTOR_STEPS), 0, MAX_FACTOR_STEP);
    }

    /**
     * An effect's scale, rate or intensity snapped to what the encoding holds.
     *
     * @param factor The factor.
     * @return The nearest value a design's bytes can hold.
     */
    public static float snapFactor(float factor) {
        return factorStep(factor) / FACTOR_STEPS;
    }

    /**
     * The rig a plan is built on.
     *
     * @param registry The loaded definitions.
     * @param plan The plan.
     * @return The rig.
     * @throws IllegalArgumentException If it isn't loaded.
     */
    private static Rig rigOf(CharacterRegistry registry, BodyPlan plan) {
        Rig rig = registry.get(Rig.class, plan.rig());
        if (rig == null) {
            throw new IllegalArgumentException("No loaded rig " + plan.rig());
        }
        return rig;
    }

    /**
     * Where an effect is placed, as a number: a socket's index in the plan, or the socket count
     * plus a bone's index in the rig.
     *
     * @param plan The plan.
     * @param rig The plan's rig.
     * @param use The effect.
     * @return The number.
     * @throws IllegalArgumentException If the socket or bone isn't there.
     */
    private static int place(BodyPlan plan, Rig rig, FxUse use) {
        if (use.socket() != null) {
            BodyPlan.Socket socket = plan.socket(use.socket());
            if (socket == null) {
                throw new IllegalArgumentException("The plan has no socket " + use.socket());
            }
            return plan.sockets().indexOf(socket);
        }
        Rig.Bone bone = use.bone() == null ? null : rig.bone(use.bone());
        if (bone == null) {
            throw new IllegalArgumentException("The rig has no bone " + use.bone());
        }
        return plan.sockets().size() + rig.bones().indexOf(bone);
    }

    /**
     * A plan's slots then its layers, which design bytes refer to by position.
     *
     * @param plan The plan.
     * @return The names.
     */
    static List<String> places(BodyPlan plan) {
        List<String> places = new ArrayList<>();
        plan.slots().forEach(slot -> places.add(slot.id()));
        plan.layers().forEach(layer -> places.add(layer.id()));
        return places;
    }

    /**
     * A definition's numeric ID.
     *
     * @param registry The loaded definitions.
     * @param kind Its kind.
     * @param id Its ID.
     * @return The numeric ID.
     * @throws IllegalArgumentException If it isn't loaded.
     */
    private static int numeric(CharacterRegistry registry, Kind kind, Id id) {
        int numeric = registry.numericId(kind, id);
        if (numeric < 0) {
            throw new IllegalArgumentException("No loaded " + kind.getFolder() + " " + id);
        }
        return numeric;
    }

    /**
     * A definition by numeric ID.
     *
     * @param <T> The kind of definition.
     * @param registry The loaded definitions.
     * @param type The kind of definition.
     * @param numeric Its numeric ID.
     * @return The definition.
     * @throws IllegalArgumentException If there is none.
     */
    private static <T extends Definition> T definition(
            CharacterRegistry registry, Class<T> type, int numeric) {
        T found = registry.get(type, numeric);
        if (found == null) {
            throw new IllegalArgumentException(
                    "No " + Kind.of(type).getFolder() + " numbered " + numeric);
        }
        return found;
    }

    /**
     * A name's position in a list.
     *
     * @param names The list.
     * @param name The name.
     * @param what What the names are, for the error.
     * @return Its position.
     * @throws IllegalArgumentException If it isn't there.
     */
    private static int index(List<String> names, String name, String what) {
        int index = names.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("The plan has no " + what + " '" + name + "'");
        }
        return index;
    }

    /**
     * A list's element by a position read from bytes.
     *
     * @param <T> What the list holds.
     * @param list The list.
     * @param index The position.
     * @param what What the elements are, for the error.
     * @return The element.
     * @throws IllegalArgumentException If the position is out of range.
     */
    private static <T> T element(List<T> list, int index, String what) {
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException("No " + what + " numbered " + index);
        }
        return list.get(index);
    }

    /**
     * Write when an effect shows: 0 for always, else its length and its ASCII letters.
     *
     * @param out Where to write.
     * @param when When it shows.
     */
    private static void writeWhen(ByteArrayOutputStream out, String when) {
        if (FxUse.ALWAYS.equals(when)) {
            out.write(0);
            return;
        }
        byte[] letters = when.getBytes(StandardCharsets.US_ASCII);
        if (letters.length == 0 || letters.length > FxUse.MAX_WHEN_LENGTH) {
            throw new IllegalArgumentException("An effect's when is too long: " + when);
        }
        out.write(letters.length);
        out.writeBytes(letters);
    }

    /**
     * Read when an effect shows.
     *
     * @param in Where to read.
     * @return When it shows.
     */
    private static String readWhen(ByteBuffer in) {
        int length = in.get() & 0xFF;
        if (length == 0) {
            return FxUse.ALWAYS;
        }
        if (length > FxUse.MAX_WHEN_LENGTH) {
            throw new IllegalArgumentException("An effect's when is too long");
        }
        byte[] letters = new byte[length];
        in.get(letters);
        return new String(letters, StandardCharsets.US_ASCII);
    }

    /**
     * Write an unsigned varint: seven bits a byte, low bits first, the high bit set while more
     * follow.
     *
     * @param out Where to write.
     * @param value The value, not negative.
     */
    static void writeVarint(ByteArrayOutputStream out, int value) {
        int rest = value;
        while ((rest & ~0x7F) != 0) {
            out.write((rest & 0x7F) | VARINT_MORE);
            rest >>>= VARINT_BITS;
        }
        out.write(rest);
    }

    /**
     * Read an unsigned varint.
     *
     * @param in Where to read.
     * @return The value.
     * @throws IllegalArgumentException If it is longer than an int.
     */
    static int readVarint(ByteBuffer in) {
        int value = 0;
        for (int shift = 0; shift < Integer.SIZE; shift += VARINT_BITS) {
            int b = in.get() & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & VARINT_MORE) == 0) {
                if (value < 0) {
                    throw new IllegalArgumentException("A varint is out of range");
                }
                return value;
            }
        }
        throw new IllegalArgumentException("A varint is too long");
    }

    /** Static methods only. */
    private DesignCodec() {}
}
