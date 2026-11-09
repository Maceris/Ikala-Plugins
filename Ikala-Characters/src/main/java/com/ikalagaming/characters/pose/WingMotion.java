package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;

import lombok.NonNull;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Wing poses made in code for a character's wing chains: folded against the back, spread as they
 * are at rest, held out to glide with a slow sway, or flapping, each bone a little behind the one
 * before. Changing between them fades over {@value #FADE} seconds. The bones are turned after each
 * solve, before any springs swing them, so the springs follow the wings.
 *
 * @author Ches Burks
 */
public final class WingMotion {
    /** How long a change between poses takes, in seconds. */
    public static final float FADE = 0.4f;

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
    private record Wing(int[] bones, BodyPlan.WingPoses poses, boolean mirrored) {}

    /** The wings. */
    private final List<Wing> wings = new ArrayList<>();

    /** How much each state shows, summing to 1. */
    private final float[] weights = new float[STATES.length];

    /** What the wings are doing, or fading to. */
    private volatile State state;

    /** Seconds the wings have moved, for flapping and swaying. */
    private float time;

    /** Scratch. */
    private final Quaternionf blended = new Quaternionf();

    /** Scratch. */
    private final Quaternionf part = new Quaternionf();

    /** Scratch. */
    private final Quaternionf swaying = new Quaternionf();

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
        for (String name : names) {
            BodyPlan.Extension extension = plan.extension(name);
            if (extension == null || extension.wing() == null) {
                continue;
            }
            int[] bones = new int[extension.bones().size()];
            boolean found = true;
            for (int i = 0; i < bones.length; ++i) {
                bones[i] = skeleton.index(extension.bones().get(i));
                found &= bones[i] >= 0;
            }
            if (found) {
                wings.add(new Wing(bones, extension.wing(), extension.rightSide()));
            }
        }
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
     * Change what the wings do, fading over {@link #FADE} seconds. Any thread may call this.
     *
     * @param newState What they do.
     */
    public void setState(@NonNull State newState) {
        state = newState;
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
