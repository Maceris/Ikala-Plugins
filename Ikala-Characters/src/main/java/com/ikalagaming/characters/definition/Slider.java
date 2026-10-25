package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Range;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A named value from -1 to 1 that scales bones. Which bones exist is checked by each body plan that
 * uses the slider, since a slider doesn't name a rig.
 *
 * @param id This slider's ID.
 * @param group The editor group it is shown in.
 * @param label The name shown in editors.
 * @param vector Whether it has separate x, y and z values.
 * @param ops What it does to bones.
 * @param ranges The values allowed in each context.
 * @author Ches Burks
 */
@Doc(
        "A named value from -1 to 1 that scales bones. Body plans pick up sliders by ID, and check"
                + " the bones against their rig.")
public record Slider(
        @Doc("This slider's ID, like \"ikala:slider/biped/leg_length\".") Id id,
        @Doc("The editor group it is shown in, like \"body\" or \"face\".") @Optional String group,
        @Doc("The name shown in editors.") @Optional String label,
        @Doc(
                        "Whether it has separate x, y and z values, like a nose's width, height"
                                + " and depth. Each op then scales each axis by its own value.")
                @Default("false")
                boolean vector,
        @Doc("What the slider does to bones.") List<Op> ops,
        @Doc(
                        "The values allowed in each context, \"player\" and \"npc\". A context"
                                + " left out allows -1 to 1.")
                @Optional
                Map<String, Range> ranges)
        implements Definition {

    /** The field listing what the slider does. */
    public static final String FIELD_OPS = "ops";

    /** The field for the ranges by context. */
    public static final String FIELD_RANGES = "ranges";

    /** The context for players, whose ranges the server enforces. */
    public static final String CONTEXT_PLAYER = "player";

    /** The context for NPCs. */
    public static final String CONTEXT_NPC = "npc";

    /** The contexts a slider's range can be given for. */
    public static final Set<String> CONTEXTS = Set.of(CONTEXT_PLAYER, CONTEXT_NPC);

    /** The range for a context that isn't given one. */
    public static final Range DEFAULT_RANGE = new Range(-1, 1);

    /** How an op scales its bones. */
    public enum OpType {
        /** Scales the bone's offset from its parent, which moves its children. */
        LENGTH,
        /** Scales only the bone's own vertices, which isn't passed to children. */
        SHAPE
    }

    /**
     * One thing a slider does: scale some bones from min at -1 to max at 1, and 1 at 0.
     *
     * @param bones The bones.
     * @param type How they are scaled.
     * @param min The scale at -1.
     * @param max The scale at 1.
     */
    @Doc("Scales some bones from min at -1, through 1 at 0, to max at 1.")
    public record Op(
            @Doc("The bones to scale.") List<String> bones,
            @Doc("How the bones are scaled.") OpType type,
            @Doc("The scale at -1, above 0.") float min,
            @Doc("The scale at 1, above 0.") float max,
            @Doc(
                            "For shape ops, the bone's local axes to scale, like \"xz\" for girth"
                                    + " around a bone that points along y.")
                    @Default("\"xyz\"")
                    String axes) {
        /** The field listing the bones. */
        public static final String FIELD_BONES = "bones";

        /** The field for the axes. */
        public static final String FIELD_AXES = "axes";

        /** The smallest scale an op produces, so values past the range can't flip a bone. */
        public static final float MIN_SCALE = 0.05f;

        /**
         * The scale for a slider value, 1 at 0, min at -1 and max at 1, continuing in a straight
         * line past those for NPC ranges.
         *
         * @param value The slider value.
         * @return The scale, at least {@link #MIN_SCALE}.
         */
        public float scale(float value) {
            float scale = value < 0 ? 1 + (1 - min) * value : 1 + (max - 1) * value;
            return Math.max(MIN_SCALE, scale);
        }

        /**
         * Whether a shape op scales an axis.
         *
         * @param axis 0 for x, 1 for y, 2 for z.
         * @return Whether that axis is scaled.
         */
        public boolean scalesAxis(int axis) {
            return axes.indexOf("xyz".charAt(axis)) >= 0;
        }
    }

    /**
     * The values allowed in a context.
     *
     * @param context The context, like "player".
     * @return The range.
     */
    public Range range(String context) {
        return ranges.getOrDefault(context, DEFAULT_RANGE);
    }

    @Override
    public void check(Checks checks) {
        if (ops.isEmpty()) {
            checks.error(JsonPath.at(FIELD_OPS), "A slider needs at least one op");
        }
        for (int i = 0; i < ops.size(); ++i) {
            Op op = ops.get(i);
            String path = JsonPath.at(FIELD_OPS, i);
            if (op.bones().isEmpty()) {
                checks.error(JsonPath.field(path, Op.FIELD_BONES), "An op needs at least one bone");
            }
            if (op.min() <= 0 || op.max() <= 0) {
                checks.error(path, "Scales must be above 0, so the bones don't vanish or flip");
            }
            String axesPath = JsonPath.field(path, Op.FIELD_AXES);
            if (op.axes().isEmpty()
                    || !op.axes().matches("[xyz]+")
                    || op.axes().chars().distinct().count() != op.axes().length()) {
                checks.error(axesPath, "Axes are some of x, y and z, each once, like \"xz\"");
            }
            if (op.type() == OpType.LENGTH) {
                if (!"xyz".equals(op.axes())) {
                    checks.error(axesPath, "Only shape ops pick axes; length scales the offset");
                }
                if (vector) {
                    checks.error(
                            path, "Vector sliders only have shape ops, since length has one axis");
                }
            }
        }
        for (String context : ranges.keySet()) {
            if (!CONTEXTS.contains(context)) {
                checks.warning(
                        JsonPath.field(JsonPath.at(FIELD_RANGES), context),
                        "Unknown context '" + context + "'; the contexts are " + CONTEXTS);
            }
        }
    }
}
