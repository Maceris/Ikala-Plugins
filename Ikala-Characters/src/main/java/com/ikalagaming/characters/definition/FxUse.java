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
        @Doc("How brightly it glows, times the effect's, 0 to 4.") @Default("1") float intensity) {

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

    /** The furthest an offset goes along each axis, in meters, so it fits a signed byte of cm. */
    public static final float MAX_OFFSET = 1.27f;

    /** The values the rate and intensity take. */
    public static final Range FACTORS = new Range(0, 4);

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
        find.apply(fx, JsonPath.field(path, FIELD_FX));
        if ((bone == null) == (socket == null)) {
            report.error(path, "An effect follows either a bone or a socket");
        }
        if (bone != null && rig != null && rig.bone(bone) == null) {
            report.error(
                    JsonPath.field(path, FIELD_BONE), "The rig has no bone named '" + bone + "'");
        }
        if (socket != null && body.socket(socket) == null) {
            report.error(
                    JsonPath.field(path, FIELD_SOCKET), "The plan has no socket '" + socket + "'");
        }
        Vec3 at = offsetOrZero();
        if (Math.abs(at.x()) > MAX_OFFSET
                || Math.abs(at.y()) > MAX_OFFSET
                || Math.abs(at.z()) > MAX_OFFSET) {
            report.error(
                    JsonPath.field(path, FIELD_OFFSET),
                    "Offsets go up to " + MAX_OFFSET + " m each way");
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
    }
}
