package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.Skeleton;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Plays a character's actions as two layers. The movement layer runs on the whole body: an action
 * like {@code idle} or {@code move}, which crossfades when it changes, and blends between clips by
 * speed where the action has speeds. The action layer plays over it, like an attack, a cast or a
 * wave, on the action's bone group so the legs keep walking, fading in, and out again when a clip
 * that doesn't loop ends. Clips' events go to a listener.
 *
 * <p>Root bones stay over their rest position across the ground (x and z), keeping only their
 * height, so clips authored moving and in place blend together; whatever moves the character moves
 * its root.
 *
 * <p>Requests ({@link #setMovement}, {@link #play}, {@link #stop}) are safe from any thread and
 * take effect on the next {@link #update}, which only the thread that poses the character calls.
 *
 * @author Ches Burks
 */
public final class Animator {
    /** The slowest a speed-blended clip plays when moving slower than its slowest clip. */
    static final float MIN_RATE = 0.25f;

    /** The fastest a speed-blended clip plays when moving faster than its fastest clip. */
    static final float MAX_RATE = 2;

    /**
     * What an action layer is doing, for editors and gameplay.
     *
     * @param action What its action resolved to.
     * @param clip The clip playing, or the one weighted most when blending by speed.
     * @param time How far into that clip, in seconds of clip time.
     */
    public record Playing(@NonNull ActionLookup.Resolved action, ActionClip clip, float time) {}

    /** One action playing. */
    private final class Track {
        /** What it resolved to, or null when nothing plays. */
        ActionLookup.Resolved resolved;

        /** The clips, by speed when blending. */
        List<ActionClip> clips = List.of();

        /** The clip picked, when not blending. */
        ActionClip single;

        /** Whether it blends its clips by speed. */
        boolean blending;

        /** Seconds of clip time into the picked clip. */
        float time;

        /** How far through a cycle, 0 to 1, when blending. */
        float phase;

        /** The slower clip of the two blended. */
        int lower;

        /** How far toward the faster clip, 0 to 1. */
        float blend;

        /**
         * Start an action from its beginning.
         *
         * @param action What it resolved to, or null for nothing.
         */
        void start(ActionLookup.Resolved action) {
            resolved = action;
            clips = action == null ? List.of() : library.apply(action);
            time = 0;
            phase = 0;
            blend = 0;
            lower = 0;
            blending =
                    clips.size() > 1
                            && action.action().blendsBySpeed()
                            && clips.stream().allMatch(ActionClip::hasSpeed);
            single = null;
            if (blending) {
                List<ActionClip> sorted = new ArrayList<>(clips);
                sorted.sort(Comparator.comparingDouble(ActionClip::getSpeed));
                clips = sorted;
            } else if (!clips.isEmpty()) {
                single = pick(clips);
            }
        }

        /**
         * Copy another track's state.
         *
         * @param other The track.
         */
        void copy(Track other) {
            resolved = other.resolved;
            clips = other.clips;
            single = other.single;
            blending = other.blending;
            time = other.time;
            phase = other.phase;
            lower = other.lower;
            blend = other.blend;
        }

        /**
         * Whether anything plays.
         *
         * @return True if it has a clip.
         */
        boolean active() {
            return !clips.isEmpty();
        }

        /**
         * Whether a clip that doesn't loop has played to its end.
         *
         * @param before How long before the end counts, in seconds of real time.
         * @return True if it has.
         */
        boolean ending(float before) {
            if (single == null || single.getClip().isLooping()) {
                return false;
            }
            return single.duration() - time <= before * single.getRate();
        }

        /**
         * Move time on, reporting events.
         *
         * @param seconds Real seconds passed.
         * @param moving How fast the character moves, in meters per second.
         * @param sink Gets events, or null to drop them.
         */
        void advance(float seconds, float moving, Consumer<AnimationSet.Event> sink) {
            if (!active()) {
                return;
            }
            if (!blending) {
                float before = time;
                time += seconds * single.getRate();
                if (sink != null) {
                    single.events(before, time, sink);
                }
                return;
            }
            float scale = 1;
            ActionClip slowest = clips.get(0);
            ActionClip fastest = clips.get(clips.size() - 1);
            if (moving <= slowest.getSpeed()) {
                lower = 0;
                blend = 0;
                scale = rate(moving, slowest.getSpeed());
            } else if (moving >= fastest.getSpeed()) {
                lower = clips.size() - 2;
                blend = 1;
                scale = rate(moving, fastest.getSpeed());
            } else {
                lower = 0;
                while (clips.get(lower + 1).getSpeed() < moving) {
                    ++lower;
                }
                float from = clips.get(lower).getSpeed();
                float to = clips.get(lower + 1).getSpeed();
                blend = (moving - from) / (to - from);
            }
            ActionClip a = clips.get(lower);
            ActionClip b = clips.get(lower + 1);
            float cycle =
                    (a.duration() / a.getRate()) * (1 - blend)
                            + (b.duration() / b.getRate()) * blend;
            float before = phase;
            phase += seconds * scale / cycle;
            ActionClip leading = blend < 0.5f ? a : b;
            if (sink != null) {
                leading.events(before * leading.duration(), phase * leading.duration(), sink);
            }
            phase -= (float) Math.floor(phase);
            time = phase * leading.duration();
        }

        /**
         * Pose the bones it animates; others stay at rest.
         *
         * @param out The pose to write.
         * @param scratch A pose to blend with.
         */
        void sample(Pose out, Pose scratch) {
            out.reset();
            if (!active()) {
                return;
            }
            if (!blending) {
                single.getClip().sample(time, out);
                return;
            }
            ActionClip a = clips.get(lower);
            a.getClip().sample(phase * a.duration(), out);
            if (blend > 0) {
                ActionClip b = clips.get(lower + 1);
                scratch.reset();
                b.getClip().sample(phase * b.duration(), scratch);
                out.blend(scratch, blend, null);
            }
        }

        /**
         * What it is doing.
         *
         * @return What it plays, or null when nothing does.
         */
        Playing playing() {
            if (resolved == null) {
                return null;
            }
            ActionClip clip = blending ? clips.get(blend < 0.5f ? lower : lower + 1) : single;
            return new Playing(resolved, clip, time);
        }
    }

    /** Finds actions. */
    private final ActionLookup lookup;

    /** Finds the clips a resolved action plays, like {@link ClipLibrary#clips}. */
    private final Function<ActionLookup.Resolved, List<ActionClip>> library;

    /** The character's skeleton. */
    private final Skeleton skeleton;

    /** The character's plan, for bone groups. */
    private final BodyPlan plan;

    /** Picks among an action's clips. */
    private final RandomGenerator random;

    /** Guards the requests. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The movement action asked for. */
    private String movementRequest = ActionLookup.IDLE;

    /** How fast the character moves, in meters per second. */
    private float speedRequest;

    /** An action asked for and not started yet, or null. */
    private String actionRequest;

    /** Whether the requested action holds its last frame instead of fading out. */
    private boolean holdRequest;

    /** Whether the action layer was asked to stop. */
    private boolean stopRequest;

    /** Gets clips' events, or null. */
    private volatile Consumer<AnimationSet.Event> listener;

    /** The movement playing. */
    private final Track movement = new Track();

    /** The movement fading out, while a new one fades in. */
    private final Track fading = new Track();

    /** How far the new movement has faded in, 0 to 1. */
    private float movementWeight = 1;

    /** How long the movement fade takes, in seconds. */
    private float movementFade;

    /** The action playing over the movement. */
    private final Track action = new Track();

    /** How much the action shows, 0 to 1. */
    private float actionWeight;

    /** Whether the action holds its last frame. */
    private boolean holding;

    /** Whether the action is fading out for good. */
    private boolean stopping;

    /** The bones the action plays on, or null for all. */
    private boolean[] mask;

    /** Scratch poses. */
    private final Pose fadingPose;

    /** Scratch poses. */
    private final Pose actionPose;

    /** Scratch poses. */
    private final Pose scratch;

    /** Scratch for a root's posed translation. */
    private final Vector3f posed = new Vector3f();

    /** Scratch for a root's rest translation. */
    private final Vector3f rest = new Vector3f();

    /** What the editor sees: the latest movement, set at the end of each update. */
    private volatile Playing movementPlaying;

    /** What the editor sees: the latest action, or null. */
    private volatile Playing actionPlaying;

    /** How fast the character moved at the last update, in meters per second. */
    private volatile float speedPlayed;

    /**
     * Start standing idle.
     *
     * @param lookup Finds the character's actions.
     * @param library Finds the clips a resolved action plays, like {@link ClipLibrary#clips}.
     * @param skeleton Its skeleton.
     * @param plan Its plan.
     * @param random Picks among an action's clips.
     */
    public Animator(
            @NonNull ActionLookup lookup,
            @NonNull Function<ActionLookup.Resolved, List<ActionClip>> library,
            @NonNull Skeleton skeleton,
            @NonNull BodyPlan plan,
            @NonNull RandomGenerator random) {
        this.lookup = lookup;
        this.library = library;
        this.skeleton = skeleton;
        this.plan = plan;
        this.random = random;
        fadingPose = new Pose(skeleton);
        actionPose = new Pose(skeleton);
        scratch = new Pose(skeleton);
    }

    /**
     * The actions the character has.
     *
     * @return The lookup.
     */
    public ActionLookup lookup() {
        return lookup;
    }

    /**
     * Set the movement, like {@code idle}, {@code idle.combat} or {@code move}.
     *
     * @param movementAction The action.
     * @param speed How fast the character moves, in meters per second.
     */
    public void setMovement(@NonNull String movementAction, float speed) {
        lock.lock();
        try {
            movementRequest = movementAction;
            speedRequest = Math.max(0, speed);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Play an action over the movement, replacing any playing. One that doesn't loop fades out at
     * its end; one that loops plays until {@link #stop stopped}.
     *
     * @param actionName The action, like {@code attack.melee.1}.
     * @param hold Whether one that doesn't loop holds its last frame instead, like a death.
     */
    public void play(@NonNull String actionName, boolean hold) {
        lock.lock();
        try {
            actionRequest = actionName;
            holdRequest = hold;
            stopRequest = false;
        } finally {
            lock.unlock();
        }
    }

    /** Fade out the action playing over the movement. */
    public void stop() {
        lock.lock();
        try {
            actionRequest = null;
            stopRequest = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set what gets clips' events, on the thread that calls {@link #update}.
     *
     * @param sink The listener, or null for none.
     */
    public void setListener(Consumer<AnimationSet.Event> sink) {
        listener = sink;
    }

    /**
     * What the movement layer is doing, as of the last update.
     *
     * @return What it plays, or null before the first update or if nothing does.
     */
    public Playing movement() {
        return movementPlaying;
    }

    /**
     * What the action layer is doing, as of the last update.
     *
     * @return What it plays, or null if nothing does.
     */
    public Playing action() {
        return actionPlaying;
    }

    /**
     * How fast the character moved at the last update.
     *
     * @return The speed, in meters per second.
     */
    public float speed() {
        return speedPlayed;
    }

    /**
     * Move time on and pose the character.
     *
     * @param seconds Real seconds since the last update.
     * @param pose The pose to write.
     */
    public void update(float seconds, @NonNull Pose pose) {
        String wantedMovement;
        float speed;
        String wantedAction;
        boolean hold;
        boolean stop;
        lock.lock();
        try {
            wantedMovement = movementRequest;
            speed = speedRequest;
            wantedAction = actionRequest;
            hold = holdRequest;
            stop = stopRequest;
            actionRequest = null;
            stopRequest = false;
        } finally {
            lock.unlock();
        }
        Consumer<AnimationSet.Event> sink = listener;

        if (movement.resolved == null || !movement.resolved.requested().equals(wantedMovement)) {
            changeMovement(lookup.resolve(wantedMovement));
        }
        if (wantedAction != null) {
            startAction(wantedAction, hold);
        } else if (stop && action.active()) {
            stopping = true;
        }

        movement.advance(seconds, speed, sink);
        if (movementWeight < 1) {
            fading.advance(seconds, speed, null);
            movementWeight =
                    movementFade <= 0 ? 1 : Math.min(1, movementWeight + seconds / movementFade);
        }
        if (action.active()) {
            action.advance(seconds, speed, sink);
            updateActionWeight(seconds);
        }

        movement.sample(pose, scratch);
        if (movementWeight < 1 && fading.active()) {
            fading.sample(fadingPose, scratch);
            fadingPose.blend(pose, movementWeight, null);
            pose.set(fadingPose);
        }
        if (action.active() && actionWeight > 0) {
            action.sample(actionPose, scratch);
            pose.blend(actionPose, actionWeight, mask);
        }
        keepInPlace(pose);
        speedPlayed = speed;
        movementPlaying = movement.playing();
        actionPlaying = action.active() ? action.playing() : null;
    }

    /**
     * Put root bones back over their rest position across the ground.
     *
     * @param pose The pose.
     */
    private void keepInPlace(Pose pose) {
        for (int b = 0; b < skeleton.size(); ++b) {
            if (skeleton.parent(b) >= 0) {
                continue;
            }
            pose.translation(b, posed);
            skeleton.restTranslation(b, rest);
            pose.setTranslation(b, posed.set(rest.x, posed.y, rest.z));
        }
    }

    /**
     * Crossfade to another movement.
     *
     * @param resolved What it resolved to, or null for none.
     */
    private void changeMovement(ActionLookup.Resolved resolved) {
        if (movement.active()) {
            fading.copy(movement);
            movementWeight = 0;
            movementFade = resolved == null ? 0 : resolved.action().fade();
        }
        movement.start(resolved);
    }

    /**
     * Start an action over the movement, fading in from however much the last one showed.
     *
     * @param name The action.
     * @param hold Whether it holds its last frame.
     */
    private void startAction(String name, boolean hold) {
        ActionLookup.Resolved resolved = lookup.resolve(name);
        action.start(resolved);
        holding = hold;
        stopping = false;
        mask = resolved == null ? null : mask(resolved.action().mask());
        if (!action.active()) {
            actionWeight = 0;
        }
    }

    /**
     * Fade the action in, or out at its end or when stopped.
     *
     * @param seconds Real seconds passed.
     */
    private void updateActionWeight(float seconds) {
        float fade = action.resolved.action().fade();
        boolean out = stopping || (!holding && action.ending(fade));
        float step = fade <= 0 ? 1 : seconds / fade;
        actionWeight = Math.clamp(actionWeight + (out ? -step : step), 0, 1);
        if (out && actionWeight <= 0) {
            action.start(null);
            stopping = false;
        }
    }

    /**
     * The bones a bone group covers: its bones and everything below them.
     *
     * @param group The group, or null for the whole body.
     * @return Which bones, or null for all.
     */
    private boolean[] mask(String group) {
        List<String> names = group == null ? null : plan.boneGroups().get(group);
        if (names == null) {
            return null;
        }
        boolean[] listed = new boolean[skeleton.size()];
        for (String name : names) {
            int bone = skeleton.index(name);
            if (bone >= 0) {
                listed[bone] = true;
            }
        }
        boolean[] covered = new boolean[skeleton.size()];
        for (int b = 0; b < covered.length; ++b) {
            for (int up = b; up >= 0 && !covered[b]; up = skeleton.parent(up)) {
                covered[b] = listed[up];
            }
        }
        return covered;
    }

    /**
     * Pick one of an action's clips by weight.
     *
     * @param clips The clips.
     * @return The one picked.
     */
    private ActionClip pick(List<ActionClip> clips) {
        if (clips.size() == 1) {
            return clips.get(0);
        }
        float total = 0;
        for (ActionClip clip : clips) {
            total += clip.getWeight();
        }
        float roll = random.nextFloat() * total;
        for (ActionClip clip : clips) {
            roll -= clip.getWeight();
            if (roll < 0) {
                return clip;
            }
        }
        return clips.get(clips.size() - 1);
    }

    /**
     * How fast to play a clip to match a speed outside the action's clips.
     *
     * @param moving The speed.
     * @param clipSpeed The nearest clip's speed.
     * @return The rate.
     */
    private static float rate(float moving, float clipSpeed) {
        if (clipSpeed <= 0) {
            return 1;
        }
        return Math.clamp(moving / clipSpeed, MIN_RATE, MAX_RATE);
    }
}
