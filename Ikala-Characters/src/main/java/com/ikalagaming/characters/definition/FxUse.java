package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.data.Vec3;

import lombok.NonNull;

import java.util.function.BiFunction;

/**
 * An effect placed on a character, from a part (sparks on a robot arm, gone when the arm is
 * swapped) or a design (an aura the player picked): which effect, the bone or socket it follows,
 * and how it is tuned. A palette channel's color tints it before graphics sees it, so effects only
 * ever get plain values.
 *
 * @param fx The effect.
 * @param bone The bone it follows, or null for a socket.
 * @param socket The socket it follows, or null for a bone.
 * @param offset Its offset from the bone or socket, in the bone's space, or null for none.
 * @param color The palette channel that tints it, or null for the effect's own colors.
 * @param scale Its size and speed, times the effect's.
 * @param rate How many particles it spawns, times the effect's.
 * @param intensity How brightly it glows, times the effect's.
 * @param when When it shows: {@value #ALWAYS}, {@value #MOVING}, {@value #COMBAT}, {@value
 *     #ACTING}, or a toggle gameplay or the player controls.
 * @param to The other end of a line it spawns along, like a blade's tip, in the same space as the
 *     offset, or null to spawn at a point.
 * @param turn Whether its spawn area and direction turn with its bone.
 * @author Ches Burks
 */
@Doc("An effect placed on a character: which effect, the bone or socket it follows, and tuning.")
public record FxUse(
        @Doc("The effect, like \"ikala:fx/hand_flame\".") Id fx,
        @Doc("The bone it follows, like \"hand.R\". Give a bone or a socket.") @Optional
                String bone,
        @Doc("The socket it follows, like \"head_top\". Give a bone or a socket.") @Optional
                String socket,
        @Doc(
                        "Its offset from the bone or socket, in meters, in the bone's space, up to"
                                + " 1.27 each way.")
                @Optional
                Vec3 offset,
        @Doc(
                        "The palette channel that tints it, like \"accent\". Left out, it keeps"
                                + " the effect's own colors.")
                @Optional
                String color,
        @Doc("Its size and speed, times the effect's, above 0 and up to 4.") @Default("1")
                float scale,
        @Doc("How many particles it spawns, times the effect's, 0 to 4.") @Default("1") float rate,
        @Doc("How brightly it glows, times the effect's, 0 to 4.") @Default("1") float intensity,
        @Doc(
                        "When it shows: \"always\", \"moving\", \"combat\", or a toggle that"
                                + " gameplay or the player controls, like \"wings\". Lowercase"
                                + " words joined by dots, up to 32 letters.")
                @Default("\"always\"")
                String when,
        @Doc(
                        "The other end of a line it spawns along, like a blade's tip, in the same"
                                + " space as the offset and up to 1.27 m each way. Left out, it"
                                + " spawns at a point.")
                @Optional
                Vec3 to,
        @Doc(
                        "Whether its spawn area and direction turn with its bone, like sparks"
                                + " off a blade. Left out, they keep to the world, so flames"
                                + " rise.")
                @Default("false")
                boolean turn) {

    /** The field for the effect. */
    public static final String FIELD_FX = "fx";

    /** The field for the bone. */
    public static final String FIELD_BONE = "bone";

    /** The field for the socket. */
    public static final String FIELD_SOCKET = "socket";

    /** The field for the offset. */
    public static final String FIELD_OFFSET = "offset";

    /** The field for the color channel. */
    public static final String FIELD_COLOR = "color";

    /** The field for the scale. */
    public static final String FIELD_SCALE = "scale";

    /** The field for the rate. */
    public static final String FIELD_RATE = "rate";

    /** The field for the intensity. */
    public static final String FIELD_INTENSITY = "intensity";

    /** The field for when it shows. */
    public static final String FIELD_WHEN = "when";

    /** Shown all the time. */
    public static final String ALWAYS = "always";

    /** Shown while the character moves. */
    public static final String MOVING = "moving";

    /** Shown while the character fights. */
    public static final String COMBAT = "combat";

    /** Shown while the character plays an action, like a swing. */
    public static final String ACTING = "acting";

    /** The field for the line's other end. */
    public static final String FIELD_TO = "to";

    /** The longest a toggle's name is, so designs stay small. */
    public static final int MAX_WHEN_LENGTH = 32;

    /** The furthest an offset goes along each axis, in meters, so it fits a signed byte of cm. */
    public static final float MAX_OFFSET = 1.27f;

    /** The values the rate and intensity take. */
    public static final Range FACTORS = new Range(0, 4);

    /**
     * Whether a point is further than an offset can go.
     *
     * @param at The point.
     * @return True if some axis is past {@link #MAX_OFFSET}.
     */
    private static boolean tooFar(Vec3 at) {
        return Math.abs(at.x()) > MAX_OFFSET
                || Math.abs(at.y()) > MAX_OFFSET
                || Math.abs(at.z()) > MAX_OFFSET;
    }

    /**
     * The same use on another socket, with its offset and line moved, for an effect on a held item
     * moved onto the item's socket.
     *
     * @param newSocket The socket.
     * @param newOffset The offset in the socket's space.
     * @param newTo The line's other end in the socket's space, or null.
     * @return The moved use.
     */
    public FxUse onSocket(@NonNull String newSocket, Vec3 newOffset, Vec3 newTo) {
        return new FxUse(
                fx, null, newSocket, newOffset, color, scale, rate, intensity, when, newTo, turn);
    }

    /**
     * Whether it shows while some states hold.
     *
     * @param states The states that hold, like {@value #MOVING} or a toggle.
     * @return True if it is always shown or its state holds.
     */
    public boolean shownIn(@NonNull java.util.Set<String> states) {
        return ALWAYS.equals(when) || states.contains(when);
    }

    /**
     * The same use shown at another time.
     *
     * @param newWhen When it shows.
     * @return The changed use.
     */
    public FxUse withWhen(@NonNull String newWhen) {
        return new FxUse(
                fx, bone, socket, offset, color, scale, rate, intensity, newWhen, to, turn);
    }

    /**
     * Its offset, zero if it has none.
     *
     * @return The offset.
     */
    public Vec3 offsetOrZero() {
        return offset == null ? Vec3.ZERO : offset;
    }

    /**
     * Check it against the plan it is used on.
     *
     * @param body The plan.
     * @param rig The plan's rig, or null if it is missing.
     * @param find Finds an effect by ID, reporting it at the given path if it is missing.
     * @param report Where problems go.
     * @param path Where it is.
     */
    public void check(
            @NonNull BodyPlan body,
            Rig rig,
            @NonNull BiFunction<Id, String, Fx> find,
            @NonNull Report report,
            @NonNull String path) {
        check(body, rig, find, report, path, false);
    }

    /**
     * Check it against the plan it is used on.
     *
     * @param body The plan.
     * @param rig The plan's rig, or null if it is missing.
     * @param find Finds an effect by ID, reporting it at the given path if it is missing.
     * @param report Where problems go.
     * @param path Where it is.
     * @param onItem Whether it is on a held item, where it may leave out both bone and socket to
     *     sit on the item, and its offsets may go further, being in the item's mesh's space.
     */
    public void check(
            @NonNull BodyPlan body,
            Rig rig,
            @NonNull BiFunction<Id, String, Fx> find,
            @NonNull Report report,
            @NonNull String path,
            boolean onItem) {
        find.apply(fx, JsonPath.field(path, FIELD_FX));
        if (bone != null && socket != null || !onItem && bone == null && socket == null) {
            report.error(
                    path,
                    onItem
                            ? "An effect on an item follows a bone, a socket or the item"
                            : "An effect follows either a bone or a socket");
        }
        if (bone != null && rig != null && rig.bone(bone) == null) {
            report.error(
                    JsonPath.field(path, FIELD_BONE), "The rig has no bone named '" + bone + "'");
        }
        if (socket != null && body.socket(socket) == null) {
            report.error(
                    JsonPath.field(path, FIELD_SOCKET), "The plan has no socket '" + socket + "'");
        }
        // Items' effects aren't sent in designs, so only designs and parts keep to a byte
        if (!onItem && tooFar(offsetOrZero())) {
            report.error(
                    JsonPath.field(path, FIELD_OFFSET),
                    "Offsets go up to " + MAX_OFFSET + " m each way");
        }
        if (!onItem && to != null && tooFar(to)) {
            report.error(
                    JsonPath.field(path, FIELD_TO), "Lines go up to " + MAX_OFFSET + " m each way");
        }
        if (color != null && !body.hasChannel(color)) {
            report.error(
                    JsonPath.field(path, FIELD_COLOR),
                    "The plan has no palette channel '" + color + "'");
        }
        if (scale <= 0 || scale > FACTORS.max()) {
            report.error(JsonPath.field(path, FIELD_SCALE), "The scale is above 0 and up to 4");
        }
        if (!FACTORS.contains(rate)) {
            report.error(JsonPath.field(path, FIELD_RATE), "The rate is 0 to 4");
        }
        if (!FACTORS.contains(intensity)) {
            report.error(JsonPath.field(path, FIELD_INTENSITY), "The intensity is 0 to 4");
        }
        if (when.length() > MAX_WHEN_LENGTH || !AnimationSet.ACTION_ID.matcher(when).matches()) {
            report.error(
                    JsonPath.field(path, FIELD_WHEN),
                    "When is lowercase words joined by dots, up to "
                            + MAX_WHEN_LENGTH
                            + " letters, like moving or wings");
        }
    }
}
