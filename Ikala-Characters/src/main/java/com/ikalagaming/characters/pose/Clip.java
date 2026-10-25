package com.ikalagaming.characters.pose;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * An authored animation: local rotation keys for bones, and translation keys for the few that move,
 * usually just the root. Clips are made once per body plan; since every variant of a plan has the
 * same bones in the same order, a clip works on all of their skeletons.
 *
 * <p>Immutable, so one clip can be sampled by many characters on many threads at once.
 *
 * @author Ches Burks
 */
public final class Clip {
    /**
     * Rotation keys for one bone.
     *
     * @param bone The bone's name.
     * @param times Key times in seconds, increasing.
     * @param rotations Key rotations, 4 floats each in x, y, z, w order.
     */
    public record RotationTrack(
            @NonNull String bone, @NonNull float[] times, @NonNull float[] rotations) {}

    /**
     * Translation keys for one bone.
     *
     * @param bone The bone's name.
     * @param times Key times in seconds, increasing.
     * @param translations Key translations, 3 floats each, in meters.
     */
    public record TranslationTrack(
            @NonNull String bone, @NonNull float[] times, @NonNull float[] translations) {}

    /** The clip's name. */
    @Getter private final String name;

    /** How long it lasts, in seconds. */
    @Getter private final float duration;

    /** Whether it repeats, like a walk, or holds its last frame, like a death. */
    @Getter private final boolean looping;

    /** Which bone each rotation track drives. */
    private final int[] rotationBones;

    /** Each rotation track's key times. */
    private final float[][] rotationTimes;

    /** Each rotation track's keys. */
    private final float[][] rotationKeys;

    /** Which bone each translation track drives. */
    private final int[] translationBones;

    /** Each translation track's key times. */
    private final float[][] translationTimes;

    /** Each translation track's keys. */
    private final float[][] translationKeys;

    /**
     * Create a clip.
     *
     * @param skeleton A skeleton of the plan it is for, to find bones by name.
     * @param name The clip's name.
     * @param duration How long it lasts, in seconds.
     * @param looping Whether it repeats.
     * @param rotations Rotation tracks.
     * @param translations Translation tracks.
     * @throws IllegalArgumentException If a track names a missing bone or its keys are malformed.
     */
    public Clip(
            @NonNull Skeleton skeleton,
            @NonNull String name,
            float duration,
            boolean looping,
            @NonNull List<RotationTrack> rotations,
            @NonNull List<TranslationTrack> translations) {
        if (duration <= 0) {
            throw new IllegalArgumentException("The clip " + name + " must last some time");
        }
        this.name = name;
        this.duration = duration;
        this.looping = looping;

        rotationBones = new int[rotations.size()];
        rotationTimes = new float[rotations.size()][];
        rotationKeys = new float[rotations.size()][];
        for (int i = 0; i < rotations.size(); ++i) {
            RotationTrack track = rotations.get(i);
            rotationBones[i] = bone(skeleton, track.bone());
            rotationTimes[i] = checkTimes(track.bone(), track.times(), track.rotations(), 4);
            rotationKeys[i] = track.rotations().clone();
        }
        translationBones = new int[translations.size()];
        translationTimes = new float[translations.size()][];
        translationKeys = new float[translations.size()][];
        for (int i = 0; i < translations.size(); ++i) {
            TranslationTrack track = translations.get(i);
            translationBones[i] = bone(skeleton, track.bone());
            translationTimes[i] = checkTimes(track.bone(), track.times(), track.translations(), 3);
            translationKeys[i] = track.translations().clone();
        }
    }

    /**
     * Find a track's bone.
     *
     * @param skeleton The skeleton.
     * @param name The bone's name.
     * @return Its index.
     * @throws IllegalArgumentException If there is no such bone.
     */
    private int bone(Skeleton skeleton, String name) {
        int index = skeleton.index(name);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "The clip " + this.name + " animates " + name + ", which isn't in the rig");
        }
        return index;
    }

    /**
     * Check a track's keys.
     *
     * @param bone The bone, for messages.
     * @param times The key times.
     * @param values The key values.
     * @param size Floats per key.
     * @return A copy of the times.
     * @throws IllegalArgumentException If they are malformed.
     */
    private float[] checkTimes(String bone, float[] times, float[] values, int size) {
        if (times.length == 0 || values.length != times.length * size) {
            throw new IllegalArgumentException(
                    "The clip " + name + " has mismatched keys for " + bone);
        }
        for (int i = 1; i < times.length; ++i) {
            if (times[i] <= times[i - 1]) {
                throw new IllegalArgumentException(
                        "The clip " + name + " has keys out of order for " + bone);
            }
        }
        return times.clone();
    }

    /**
     * Turn a playing time into a time within the clip: wrapped if it loops, held at the ends if it
     * doesn't.
     *
     * @param time The time, in seconds since it started.
     * @return The time within the clip.
     */
    public float localTime(float time) {
        if (looping) {
            float wrapped = time % duration;
            return wrapped < 0 ? wrapped + duration : wrapped;
        }
        return Math.clamp(time, 0, duration);
    }

    /**
     * Set the bones this clip animates to how they are at a time. Other bones are left alone.
     *
     * @param time The time, in seconds since it started.
     * @param pose The pose to write into.
     */
    public void sample(float time, @NonNull Pose pose) {
        float t = localTime(time);
        Quaternionf fromRotation = pose.scratchRotation;
        Quaternionf toRotation = pose.scratchRotation2;
        Vector3f fromTranslation = pose.scratchTranslation;
        Vector3f toTranslation = pose.scratchTranslation2;
        for (int i = 0; i < rotationBones.length; ++i) {
            float[] times = rotationTimes[i];
            float[] keys = rotationKeys[i];
            int key = keyBefore(times, t);
            setRotation(fromRotation, keys, key);
            if (key + 1 < times.length) {
                setRotation(toRotation, keys, key + 1);
                fromRotation.slerp(toRotation, blend(times, key, t));
            }
            pose.setRotation(rotationBones[i], fromRotation);
        }
        for (int i = 0; i < translationBones.length; ++i) {
            float[] times = translationTimes[i];
            float[] keys = translationKeys[i];
            int key = keyBefore(times, t);
            fromTranslation.set(keys[key * 3], keys[key * 3 + 1], keys[key * 3 + 2]);
            if (key + 1 < times.length) {
                toTranslation.set(keys[key * 3 + 3], keys[key * 3 + 4], keys[key * 3 + 5]);
                fromTranslation.lerp(toTranslation, blend(times, key, t));
            }
            pose.setTranslation(translationBones[i], fromTranslation);
        }
    }

    /**
     * Read a rotation key.
     *
     * @param out Where to put it.
     * @param keys The keys.
     * @param key Which key.
     */
    private static void setRotation(Quaternionf out, float[] keys, int key) {
        out.set(keys[key * 4], keys[key * 4 + 1], keys[key * 4 + 2], keys[key * 4 + 3]);
    }

    /**
     * Find the last key at or before a time, or the first key if the time is before it.
     *
     * @param times The key times.
     * @param time The time.
     * @return The key's index.
     */
    private static int keyBefore(float[] times, float time) {
        int low = 0;
        int high = times.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (times[middle] <= time) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    /**
     * How far a time is from one key to the next.
     *
     * @param times The key times.
     * @param key The key before the time.
     * @param time The time.
     * @return 0 at the key, 1 at the next one.
     */
    private static float blend(float[] times, int key, float time) {
        return Math.clamp((time - times[key]) / (times[key + 1] - times[key]), 0, 1);
    }
}
