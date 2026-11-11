package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wing poses made in code for a character's wing chains: folded against the back, spread as they
 * are at rest, held out to glide with a slow sway, or flapping, each bone a little behind the one
 * before. Changing between them fades over {@value #FADE} seconds. A wing's root can sit away from
 * where the rig puts it, like up on the shoulder blades, in every pose. The bones are turned after
 * each solve, before any springs swing them, so the springs follow the wings.
 *
 * @author Ches Burks
 */
public final class WingMotion {
    /** How long a change between poses takes, in seconds. */
    public static final float FADE = 0.4f;

    /** Above this speed, in meters per second, wings that follow the motion spread. */
    public static final float SPREAD_SPEED = 3;

    /** Actions starting with these flap wings that follow the motion, like "jump". */
    public static final List<String> FLAP_ACTIONS = List.of("jump", "fly");

    /** Actions starting with these hold wings out to glide, like "fall". */
    public static final List<String> GLIDE_ACTIONS = List.of("fall", "glide");

    /** What the wings do. */
    public enum State {
        /** Folded against the back. */
        FOLD,
        /** Spread, as they are at rest. */
        SPREAD,
        /** Held out to glide, swaying slowly. */
        GLIDE,
        /** Beating. */
        FLAP;

        /**
         * The state's name as scripts and editors write it.
         *
         * @return The name, like "fold".
         */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * A state by the name scripts and editors write.
         *
         * @param key The name, like "flap".
         * @return The state.
         * @throws IllegalArgumentException If there is none by that name.
         */
        public static State of(@NonNull String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }
    }

    /** States, in the order the weights are kept. */
    private static final State[] STATES = State.values();

    /** One wing chain. */
    private record Wing(String chain, int[] bones, BodyPlan.WingPoses poses, boolean mirrored) {}

    /** The wings, replaced whole when a wing's poses change, never changed. */
    private volatile List<Wing> wings;

    /** How much each state shows, summing to 1. */
    private final float[] weights = new float[STATES.length];

    /** What the wings are doing, or fading to. */
    private volatile State state;

    /** Whether the state follows the character's motion, see {@link #follow}. */
    private volatile boolean automatic;

    /** Seconds the wings have moved, for flapping and swaying. */
    private float time;

    /** Scratch. */
    private final Quaternionf blended = new Quaternionf();

    /** Scratch. */
    private final Quaternionf part = new Quaternionf();

    /** Scratch. */
    private final Quaternionf swaying = new Quaternionf();

    /** Scratch. */
    private final Vector3f shift = new Vector3f();

    /**
     * Set up wing poses for some of a plan's chains. Chains without wing poses, or whose bones the
     * skeleton lacks, are left alone.
     *
     * @param skeleton The skeleton.
     * @param plan The plan, which gives the chains and their poses.
     * @param names The chains, like those the parts worn use.
     * @param start What the wings start out doing, fully.
     */
    public WingMotion(
            @NonNull Skeleton skeleton,
            @NonNull BodyPlan plan,
            @NonNull Collection<String> names,
            @NonNull State start) {
        this(skeleton, plan, names, start, Map.of());
    }

    /**
     * Set up wing poses for some of a plan's chains, with some chains' poses replaced, like by a
     * worn wing part's own. Chains without wing poses, or whose bones the skeleton lacks, are left
     * alone.
     *
     * @param skeleton The skeleton.
     * @param plan The plan, which gives the chains and their poses.
     * @param names The chains, like those the parts worn use.
     * @param start What the wings start out doing, fully.
     * @param own Poses in place of the plan's, by chain.
     */
    public WingMotion(
            @NonNull Skeleton skeleton,
            @NonNull BodyPlan plan,
            @NonNull Collection<String> names,
            @NonNull State start,
            @NonNull Map<String, BodyPlan.WingPoses> own) {
        List<Wing> found = new ArrayList<>();
        for (String name : names) {
            BodyPlan.Extension extension = plan.extension(name);
            BodyPlan.WingPoses poses =
                    own.getOrDefault(name, extension == null ? null : extension.wing());
            if (extension == null || poses == null) {
                continue;
            }
            int[] bones = new int[extension.bones().size()];
            boolean all = true;
            for (int i = 0; i < bones.length; ++i) {
                bones[i] = skeleton.index(extension.bones().get(i));
                all &= bones[i] >= 0;
            }
            if (all) {
                found.add(new Wing(name, bones, poses, extension.rightSide()));
            }
        }
        wings = List.copyOf(found);
        state = start;
        weights[start.ordinal()] = 1;
    }

    /**
     * Whether there are any wings to move.
     *
     * @return True if no chain has wing poses.
     */
    public boolean isEmpty() {
        return wings.isEmpty();
    }

    /**
     * Replace a wing's poses, as an editor does while they are tuned. Any thread may call this.
     *
     * @param chain The wing's chain.
     * @param poses The poses, for the left side; a right wing mirrors them.
     * @return False if no wing is on that chain.
     */
    public boolean setPoses(@NonNull String chain, BodyPlan.@NonNull WingPoses poses) {
        List<Wing> now = wings;
        List<Wing> changed = new ArrayList<>(now.size());
        boolean found = false;
        for (Wing wing : now) {
            if (wing.chain().equals(chain)) {
                changed.add(new Wing(chain, wing.bones(), poses, wing.mirrored()));
                found = true;
            } else {
                changed.add(wing);
            }
        }
        wings = List.copyOf(changed);
        return found;
    }

    /**
     * The poses a wing moves with now.
     *
     * @param chain The wing's chain.
     * @return The poses, or null if no wing is on that chain.
     */
    public BodyPlan.WingPoses poses(@NonNull String chain) {
        for (Wing wing : wings) {
            if (wing.chain().equals(chain)) {
                return wing.poses();
            }
        }
        return null;
    }

    /**
     * A bone's turn in a state, in model space, as {@link #update} turns it there with no fade,
     * leaving out a glide's sway and a flap's beat.
     *
     * @param angles The state's angles for each bone, for the left side, in degrees.
     * @param bone The bone's place in the chain, from the root out.
     * @param mirrored Whether the wing is a right one, which mirrors them.
     * @param out Where to put the turn.
     * @return out.
     */
    public static Quaternionf turn(
            @NonNull List<Vec3> angles, int bone, boolean mirrored, @NonNull Quaternionf out) {
        angles(angles, bone, mirrored, out);
        return out;
    }

    /**
     * The angles, for the left side, of a turn in model space, undoing {@link #turn}.
     *
     * @param turn The turn.
     * @param mirrored Whether the wing is a right one.
     * @return The angles about x, y and z, in degrees.
     */
    public static Vec3 angles(@NonNull Quaternionfc turn, boolean mirrored) {
        Vector3f angles = turn.getEulerAnglesXYZ(new Vector3f());
        float sign = mirrored ? -1 : 1;
        return new Vec3(
                (float) Math.toDegrees(angles.x),
                (float) Math.toDegrees(angles.y) * sign,
                (float) Math.toDegrees(angles.z) * sign);
    }

    /**
     * Change what the wings do, fading over {@link #FADE} seconds, and stop following the
     * character's motion. Any thread may call this.
     *
     * @param newState What they do.
     */
    public void setState(@NonNull State newState) {
        automatic = false;
        state = newState;
    }

    /**
     * Let the wings follow the character's motion from now on, see {@link #automatic}. Any thread
     * may call this.
     */
    public void setAutomatic() {
        automatic = true;
    }

    /**
     * Whether the wings follow the character's motion.
     *
     * @return True if they do.
     */
    public boolean isAutomatic() {
        return automatic;
    }

    /**
     * Follow the character's motion, if the wings do: see {@link #automatic}. The timer that poses
     * the character calls this before each update.
     *
     * @param speed How fast it moves, in meters per second.
     * @param action The action it plays, as asked for, like "jump", or null for none.
     */
    public void follow(float speed, String action) {
        if (automatic) {
            state = automatic(speed, action);
        }
    }

    /**
     * What wings do for some motion: flap for jumping or flying, glide for falling, spread when
     * running, and fold otherwise.
     *
     * @param speed How fast the character moves, in meters per second.
     * @param action The action it plays, as asked for, or null for none.
     * @return The state.
     */
    public static State automatic(float speed, String action) {
        if (action != null) {
            if (FLAP_ACTIONS.stream().anyMatch(action::startsWith)) {
                return State.FLAP;
            }
            if (GLIDE_ACTIONS.stream().anyMatch(action::startsWith)) {
                return State.GLIDE;
            }
        }
        return speed > SPREAD_SPEED ? State.SPREAD : State.FOLD;
    }

    /**
     * What the wings do, or are fading to.
     *
     * @return The state.
     */
    public State getState() {
        return state;
    }

    /**
     * Move the wings on by some time and turn their bones, after a solve.
     *
     * @param seconds How much time passed.
     * @param solver The solver, just solved; its wing bones are turned.
     */
    public void update(float seconds, @NonNull PoseSolver solver) {
        float step = Math.max(seconds, 0);
        time += step;
        fade(step);
        for (Wing wing : wings) {
            Vec3 offset = wing.poses().offset();
            if (offset != null) {
                // Mirrored for a right wing, across the body's middle
                shift.set(wing.mirrored() ? -offset.x() : offset.x(), offset.y(), offset.z());
                solver.shift(wing.bones()[0], shift);
            }
            for (int i = 0; i < wing.bones().length; ++i) {
                turnFor(wing, i, blended);
                if (Math.abs(blended.w) < 1 - 1e-7f) {
                    solver.turn(wing.bones()[i], blended);
                }
            }
        }
    }

    /**
     * Move the weights toward the state's, by as much as a fade over the time allows.
     *
     * @param seconds The time.
     */
    private void fade(float seconds) {
        int target = state.ordinal();
        float change = seconds / FADE;
        float others = 0;
        for (int s = 0; s < weights.length; ++s) {
            if (s != target) {
                weights[s] = Math.max(0, weights[s] - change);
                others += weights[s];
            }
        }
        weights[target] = 1 - others;
    }

    /**
     * How one of a wing's bones turns now: each state's turn, blended by the weights.
     *
     * @param wing The wing.
     * @param bone Which of its bones, from the root out.
     * @param out Where to put the turn, in model space.
     * @return out.
     */
    private Quaternionf turnFor(Wing wing, int bone, Quaternionf out) {
        out.set(0, 0, 0, 0);
        for (State s : STATES) {
            float weight = weights[s.ordinal()];
            if (weight <= 0) {
                continue;
            }
            stateTurn(wing, bone, s, part);
            // Sum on the same side of the sphere as the rest, then normalize
            if (out.dot(part) < 0) {
                part.mul(-1);
            }
            out.add(part.mul(weight));
        }
        return out.lengthSquared() > 0 ? out.normalize() : out.identity();
    }

    /**
     * How one of a wing's bones turns in one state.
     *
     * @param wing The wing.
     * @param bone Which of its bones.
     * @param of The state.
     * @param out Where to put the turn.
     * @return out.
     */
    private Quaternionf stateTurn(Wing wing, int bone, State of, Quaternionf out) {
        BodyPlan.WingPoses poses = wing.poses();
        out.identity();
        switch (of) {
            case SPREAD -> {
                // As at rest
            }
            case FOLD -> angles(poses.fold(), bone, wing.mirrored(), out);
            case GLIDE -> {
                angles(poses.glide(), bone, wing.mirrored(), out);
                double sway =
                        Math.toRadians(poses.sway())
                                * Math.sin(2 * Math.PI * poses.swayRate() * time);
                out.premul(swaying.rotationZ((float) (wing.mirrored() ? -sway : sway)));
            }
            case FLAP -> {
                List<Float> flap = poses.flap();
                if (bone < flap.size() && flap.get(bone) != null) {
                    double phase = 2 * Math.PI * (poses.flapRate() * time - poses.flapLag() * bone);
                    double angle = Math.toRadians(flap.get(bone)) * Math.sin(phase);
                    out.rotationZ((float) (wing.mirrored() ? -angle : angle));
                }
            }
        }
        return out;
    }

    /**
     * A bone's turn from a list of angles, mirrored for a right wing.
     *
     * @param angles The bones' angles, in degrees.
     * @param bone Which bone.
     * @param mirrored Whether the wing is on the right.
     * @param out Where to put the turn.
     */
    private static void angles(List<Vec3> angles, int bone, boolean mirrored, Quaternionf out) {
        if (bone >= angles.size() || angles.get(bone) == null) {
            out.identity();
            return;
        }
        Vec3 turn = angles.get(bone);
        // Mirroring across the middle keeps turns about x and flips those about y and z
        float sign = mirrored ? -1 : 1;
        out.rotationXYZ(
                (float) Math.toRadians(turn.x()),
                (float) Math.toRadians(turn.y() * sign),
                (float) Math.toRadians(turn.z() * sign));
    }
}
