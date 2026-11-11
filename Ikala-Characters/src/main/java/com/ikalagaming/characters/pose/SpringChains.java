package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.definition.BodyPlan;

import lombok.NonNull;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Secondary motion for a character's extension chains, like a cape, tail or wings: each joint of a
 * chain is a point that is pulled toward where the clips pose it, falls, slows down, trails behind
 * as the character moves and keeps out of the body's capsules, while the bones between keep their
 * lengths. A capsule can run along another chain, like a tail under a cape: it counts only while
 * that chain is worn, and the chain it is on moves first. After each solve the chain's bones are
 * turned to point at the points.
 *
 * <p>The points step at a fixed {@value #STEPS_PER_SECOND} steps a second whatever the frame rate,
 * so a chain swings the same at 30 frames a second as at 144. They live in model space, so turning
 * or moving the whole character doesn't swing them; only the pose and the character's speed through
 * the air do.
 *
 * @author Ches Burks
 */
public final class SpringChains {
    /** How many steps the points take each second. */
    public static final int STEPS_PER_SECOND = 120;

    /** Earth's gravity, in meters per second squared, down model space's y axis. */
    public static final float GRAVITY = 9.81f;

    /** The most steps taken for one update; after a longer pause the points start over. */
    private static final int MAX_STEPS = 12;

    /** How many times each step settles lengths and capsules, which disturb each other. */
    private static final int SETTLE_PASSES = 2;

    /** Directions shorter than this are left alone. */
    private static final float TINY = 1e-6f;

    /** The length of a step, in seconds. */
    private static final float STEP = 1f / STEPS_PER_SECOND;

    /**
     * A capsule the points keep out of.
     *
     * @param from One end's bone.
     * @param to The other end's bone.
     * @param radius How thick it is, in meters.
     * @param out For a one-way capsule, the side points are pushed to, in model space; null for one
     *     points are pushed straight out of.
     */
    public record Capsule(int from, int to, float radius, Vector3fc out) {}

    /** One chain being simulated. */
    private static final class Chain {
        /** Its bones, from the root out. */
        final int[] bones;

        /** How it swings. */
        final BodyPlan.Spring spring;

        /** The capsules it keeps out of. */
        final List<Capsule> capsules;

        /** Each joint's point, from the first bone's tip out, 3 floats each. */
        final float[] points;

        /** Each point a step ago. */
        final float[] previous;

        /** Scratch: the pull straightening each joint, 3 floats each. */
        final float[] bending;

        /** Whether the points hold a place yet. */
        boolean started;

        Chain(int[] bones, BodyPlan.Spring spring, List<Capsule> capsules) {
            this.bones = bones;
            this.spring = spring;
            this.capsules = capsules;
            points = new float[bones.length * 3];
            previous = new float[bones.length * 3];
            bending = new float[bones.length * 3];
        }
    }

    /** The skeleton the chains are on. */
    private final Skeleton skeleton;

    /** The chains. */
    private final List<Chain> chains = new ArrayList<>();

    /** Every chain's capsules, each once. */
    private final List<Capsule> allCapsules;

    /** Time not stepped yet, in seconds. */
    private float pending;

    /** Scratch: where clips pose each joint, the chain's head first, 3 floats each. */
    private float[] posed = new float[0];

    /** Scratch. */
    private final Vector3f a = new Vector3f();

    /** Scratch. */
    private final Vector3f b = new Vector3f();

    /** Scratch. */
    private final Vector3f c = new Vector3f();

    /** Scratch. */
    private final Vector3f start = new Vector3f();

    /** Scratch. */
    private final Vector3f along = new Vector3f();

    /** Scratch. */
    private final Vector3f away = new Vector3f();

    /** Scratch. */
    private final Quaternionf turn = new Quaternionf();

    /**
     * Set up secondary motion for some of a plan's chains. Chains without a spring, or whose bones
     * the skeleton lacks, are left to hold their pose.
     *
     * @param skeleton The skeleton.
     * @param plan The plan, which gives the chains and their springs.
     * @param names The chains to move, like those the parts worn use.
     */
    public SpringChains(
            @NonNull Skeleton skeleton, @NonNull BodyPlan plan, @NonNull Collection<String> names) {
        this.skeleton = skeleton;
        // Which chain each chain bone is on, so colliders on chains not worn are left out
        Map<String, String> chainOf = new HashMap<>();
        plan.extensions()
                .forEach(chain -> chain.bones().forEach(bone -> chainOf.put(bone, chain.id())));
        // Chains that others collide with go first, so the others meet them where they swung to
        List<String> ordered = new ArrayList<>(names);
        ordered.sort(
                Comparator.comparing(
                        name -> collidesWithWorn(plan.extension(name), chainOf, names)));
        for (String name : ordered) {
            BodyPlan.Extension extension = plan.extension(name);
            if (extension == null || extension.spring() == null) {
                continue;
            }
            int[] bones = new int[extension.bones().size()];
            boolean found = true;
            for (int i = 0; i < bones.length; ++i) {
                bones[i] = skeleton.index(extension.bones().get(i));
                found &= bones[i] >= 0;
            }
            List<Capsule> capsules = new ArrayList<>();
            for (BodyPlan.Collider collider : extension.spring().colliders()) {
                int from = skeleton.index(collider.from());
                int to = skeleton.index(collider.to());
                // A capsule along a chain no part is worn on, like a tail under a cape, isn't there
                boolean bare =
                        Stream.of(collider.from(), collider.to())
                                .map(chainOf::get)
                                .anyMatch(chain -> chain != null && !names.contains(chain));
                if (from >= 0 && to >= 0 && !bare) {
                    Vector3f out =
                            collider.out() == null
                                    ? null
                                    : new Vector3f(
                                                    collider.out().x(),
                                                    collider.out().y(),
                                                    collider.out().z())
                                            .normalize();
                    capsules.add(new Capsule(from, to, collider.radius(), out));
                }
            }
            if (found && bones.length > 0) {
                chains.add(new Chain(bones, extension.spring(), capsules));
                posed = new float[Math.max(posed.length, (bones.length + 1) * 3)];
            }
        }
        allCapsules = chains.stream().flatMap(chain -> chain.capsules.stream()).distinct().toList();
    }

    /**
     * The capsules the chains keep out of, for debug views.
     *
     * @return Every chain's capsules, each once.
     */
    public List<Capsule> capsules() {
        return allCapsules;
    }

    /**
     * The bones the chains move, for debug views.
     *
     * @return Each chain's bones, from the root out.
     */
    public List<int[]> chainBones() {
        return chains.stream().map(chain -> chain.bones.clone()).toList();
    }

    /**
     * Whether a chain keeps out of another worn chain, so must move after it.
     *
     * @param extension The chain, or null.
     * @param chainOf The chain each chain bone is on.
     * @param worn The chains worn.
     * @return True if one of its colliders is on another worn chain.
     */
    private static boolean collidesWithWorn(
            BodyPlan.Extension extension, Map<String, String> chainOf, Collection<String> worn) {
        if (extension == null || extension.spring() == null) {
            return false;
        }
        return extension.spring().colliders().stream()
                .flatMap(collider -> Stream.of(collider.from(), collider.to()))
                .map(chainOf::get)
                .anyMatch(
                        chain ->
                                chain != null
                                        && !chain.equals(extension.id())
                                        && worn.contains(chain));
    }

    /**
     * Whether any chain moves.
     *
     * @return True if there is a chain with a spring.
     */
    public boolean isEmpty() {
        return chains.isEmpty();
    }

    /**
     * Where a chain's joints are, for tests.
     *
     * @param chain Which chain, in the order they were set up.
     * @return A copy of its points, each bone's tip from the root out, 3 floats each.
     */
    float[] points(int chain) {
        return chains.get(chain).points.clone();
    }

    /** Start over from the posed places on the next update, as after a jump to another spot. */
    public void reset() {
        for (Chain chain : chains) {
            chain.started = false;
        }
        pending = 0;
    }

    /**
     * Move the chains on by some time and turn their bones to match, after a solve.
     *
     * @param seconds How much time passed.
     * @param solver The solver, just solved with the clips' pose; its chain bones are turned.
     * @param speed How fast the character moves forward, in meters per second, which pushes the
     *     chains back by each one's drag.
     */
    public void update(float seconds, @NonNull PoseSolver solver, float speed) {
        pending += Math.max(seconds, 0);
        int steps = (int) (pending * STEPS_PER_SECOND);
        pending -= steps * STEP;
        if (steps > MAX_STEPS) {
            reset();
            steps = 0;
        }
        for (Chain chain : chains) {
            posedJoints(chain, solver);
            if (!chain.started) {
                System.arraycopy(posed, 3, chain.points, 0, chain.points.length);
                System.arraycopy(posed, 3, chain.previous, 0, chain.previous.length);
                chain.started = true;
            }
            for (int s = 0; s < steps; ++s) {
                step(chain, solver, speed);
            }
            pointBones(chain, solver);
        }
    }

    /**
     * Where the clips pose a chain's joints: its first bone's head, then each bone's tip. A bone's
     * tip is the next bone's head; the last bone's is as far along its own y axis as the bone
     * before it is long.
     *
     * @param chain The chain.
     * @param solver The solver, just solved.
     */
    private void posedJoints(Chain chain, PoseSolver solver) {
        int[] bones = chain.bones;
        for (int i = 0; i < bones.length; ++i) {
            solver.model(bones[i]).getTranslation(a);
            put(a, posed, i);
        }
        Matrix4fc last = solver.model(bones[bones.length - 1]);
        float length;
        if (bones.length > 1) {
            length = distance(posed, bones.length - 1, bones.length - 2);
        } else {
            length = skeleton.restTranslation(bones[0], b).length();
        }
        last.getColumn(1, b).normalize(length);
        last.getTranslation(a).add(b);
        put(a, posed, bones.length);
    }

    /**
     * Move a chain's points on by one step: pulled toward their posed places, by gravity and by the
     * air the character moves through, then held to their bones' lengths and out of the capsules.
     *
     * @param chain The chain.
     * @param solver The solver, for the capsules' bones.
     * @param speed How fast the character moves forward.
     */
    private void step(Chain chain, PoseSolver solver, float speed) {
        BodyPlan.Spring spring = chain.spring;
        float keep = Math.max(0, 1 - spring.damping() * STEP);
        float[] points = chain.points;
        float[] previous = chain.previous;
        boolean bent = bending(chain);
        for (int i = 0; i < chain.bones.length; ++i) {
            get(points, i, a);
            get(previous, i, b);
            // The pull toward the posed place, gravity, and the air pushing back: characters
            // face +Z
            get(posed, i + 1, c).sub(a).mul(spring.stiffness());
            c.y -= GRAVITY * spring.gravity();
            c.z -= speed * spring.drag();
            if (bent) {
                c.add(chain.bending[i * 3], chain.bending[i * 3 + 1], chain.bending[i * 3 + 2]);
            }
            // Verlet: on by the last step's move, kept by the damping, plus the pull
            b.negate().add(a).mul(keep).add(a).fma(STEP * STEP, c);
            put(a, previous, i);
            put(b, points, i);
        }
        for (int pass = 0; pass < SETTLE_PASSES; ++pass) {
            for (int i = 0; i < chain.bones.length; ++i) {
                float length = distance(posed, i + 1, i);
                get(posed, 0, b);
                if (i > 0) {
                    get(points, i - 1, b);
                }
                get(points, i, a);
                // Back to the bone's length from the joint before, then out of the capsules:
                // pushed out first, the length pulled a point straight back in
                a.sub(b);
                if (a.lengthSquared() > TINY) {
                    a.normalize(length);
                } else {
                    get(posed, i + 1, a).sub(get(posed, i, c));
                }
                keepOut(chain, solver, a.add(b));
                put(a, points, i);
            }
        }
    }

    /**
     * Find the pull that straightens each joint between two bones toward the bend clips pose there,
     * by the chain's bend stiffness, so a bend spreads over neighboring joints rather than folding
     * at one. A joint pulled one way pulls its neighbors back half as much each, so the pulls only
     * bend the chain, never move it.
     *
     * @param chain The chain.
     * @return Whether there is any pull, in {@code chain.bending}.
     */
    private boolean bending(Chain chain) {
        float stiffness = chain.spring.bend();
        float[] bending = chain.bending;
        Arrays.fill(bending, 0);
        if (stiffness <= 0) {
            return false;
        }
        float[] points = chain.points;
        // Point i is bone i's tip, posed at posed[i + 1]; the joint before the first is the head
        for (int i = 0; i + 1 < chain.bones.length; ++i) {
            if (i == 0) {
                get(posed, 0, a);
            } else {
                get(points, i - 1, a);
            }
            get(points, i + 1, b);
            // How far the joint is from the middle of its neighbors, less how far clips pose it
            a.add(b).mul(0.5f).sub(get(points, i, c));
            get(posed, i, b).add(get(posed, i + 2, start)).mul(0.5f).sub(get(posed, i + 1, along));
            a.sub(b).mul(stiffness);
            add(bending, i, a, 1);
            if (i > 0) {
                add(bending, i - 1, a, -0.5f);
            }
            add(bending, i + 1, a, -0.5f);
        }
        return true;
    }

    /**
     * Add a scaled vector to one of an array's vectors.
     *
     * @param to The array, 3 floats a vector.
     * @param index Which vector.
     * @param by The vector added.
     * @param scale How much of it.
     */
    private static void add(float[] to, int index, Vector3f by, float scale) {
        to[index * 3] += by.x * scale;
        to[index * 3 + 1] += by.y * scale;
        to[index * 3 + 2] += by.z * scale;
    }

    /**
     * Push a point out of every capsule it is inside.
     *
     * @param chain The chain, whose capsules count.
     * @param solver The solver, for the capsules' bones.
     * @param point The point, moved.
     */
    private void keepOut(Chain chain, PoseSolver solver, Vector3f point) {
        for (Capsule capsule : chain.capsules) {
            solver.model(capsule.from()).getTranslation(start);
            solver.model(capsule.to()).getTranslation(along).sub(start);
            float lengthSquared = along.lengthSquared();
            float raw =
                    lengthSquared < TINY ? 0 : point.sub(start, away).dot(along) / lengthSquared;
            if (capsule.out() != null) {
                // Beside the capsule's length only: past its ends a point is free
                if (raw >= 0 && raw <= 1) {
                    behind(point, start.fma(raw, along), along, capsule.radius(), capsule.out());
                }
                continue;
            }
            // The nearest point on the capsule's line, and the way out from it
            start.fma(Math.clamp(raw, 0f, 1f), along);
            point.sub(start, away);
            float distance = away.length();
            if (distance < capsule.radius() && distance > TINY) {
                point.set(start).fma(capsule.radius() / distance, away);
            }
        }
    }

    /**
     * Keep a point on the out side of a one-way capsule: one inside it, or anywhere on the near
     * side within its width, like a cape's joint between the body and a tail, moves along the way
     * out to the capsule's far side, so the chain goes round behind it rather than through it.
     *
     * @param point The point, moved.
     * @param onLine The point on the capsule's line level with it.
     * @param line The line's direction, any length.
     * @param radius The capsule's radius.
     * @param out The way out, of length 1.
     */
    private void behind(
            Vector3f point, Vector3fc onLine, Vector3fc line, float radius, Vector3fc out) {
        // Across the line: the way out o, and the point's offset w, split along o and beside it
        float lineSquared = line.lengthSquared();
        Vector3f w = point.sub(onLine, away);
        Vector3f o = c.set(out);
        if (lineSquared > TINY) {
            w.fma(-w.dot(line) / lineSquared, line);
            o.fma(-o.dot(line) / lineSquared, line);
        }
        float oLength = o.length();
        if (oLength < TINY) {
            return;
        }
        o.div(oLength);
        float ahead = w.dot(o);
        float besideSquared = w.lengthSquared() - ahead * ahead;
        if (besideSquared >= radius * radius) {
            return;
        }
        float reach = (float) Math.sqrt(radius * radius - besideSquared);
        if (ahead < reach) {
            // Moving along out moves across the line oLength as far
            point.fma((reach - ahead) / oLength, out);
        }
    }

    /**
     * Turn a chain's bones, root out, so each one's tip lies on its point.
     *
     * @param chain The chain.
     * @param solver The solver, whose chain bones are turned.
     */
    private void pointBones(Chain chain, PoseSolver solver) {
        int[] bones = chain.bones;
        for (int i = 0; i < bones.length; ++i) {
            Matrix4fc bone = solver.model(bones[i]);
            bone.getTranslation(a);
            if (i + 1 < bones.length) {
                solver.model(bones[i + 1]).getTranslation(b);
            } else {
                float length = distance(posed, i + 1, i);
                bone.getColumn(1, b).normalize(length).add(a);
            }
            b.sub(a);
            get(chain.points, i, c).sub(a);
            if (b.lengthSquared() < TINY || c.lengthSquared() < TINY) {
                continue;
            }
            turn.rotationTo(b, c);
            solver.turn(bones[i], turn);
        }
    }

    /**
     * Read a point.
     *
     * @param from The points, 3 floats each.
     * @param index Which.
     * @param out Where to put it.
     * @return out.
     */
    private static Vector3f get(float[] from, int index, Vector3f out) {
        return out.set(from[index * 3], from[index * 3 + 1], from[index * 3 + 2]);
    }

    /**
     * Write a point.
     *
     * @param point The point.
     * @param to The points, 3 floats each.
     * @param index Which.
     */
    private static void put(Vector3fc point, float[] to, int index) {
        to[index * 3] = point.x();
        to[index * 3 + 1] = point.y();
        to[index * 3 + 2] = point.z();
    }

    /**
     * How far apart two points are.
     *
     * @param points The points, 3 floats each.
     * @param first One.
     * @param second The other.
     * @return The distance.
     */
    private static float distance(float[] points, int first, int second) {
        float x = points[first * 3] - points[second * 3];
        float y = points[first * 3 + 1] - points[second * 3 + 1];
        float z = points[first * 3 + 2] - points[second * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
