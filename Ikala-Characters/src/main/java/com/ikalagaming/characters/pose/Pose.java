package com.ikalagaming.characters.pose;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Every bone's local rotation and translation, relative to its parent, as clips set them. Stored in
 * flat arrays so posing a crowd doesn't allocate.
 *
 * @author Ches Burks
 */
public final class Pose {
    /** The skeleton this pose is for. */
    @Getter private final Skeleton skeleton;

    /** Local rotations, 4 floats per bone in x, y, z, w order. */
    private final float[] rotations;

    /** Local translations, 3 floats per bone. */
    private final float[] translations;

    /** Scratch for clips sampling into this pose, so shared clips needn't hold any. */
    final Quaternionf scratchRotation = new Quaternionf();

    /** Scratch for clips sampling into this pose. */
    final Quaternionf scratchRotation2 = new Quaternionf();

    /** Scratch for clips sampling into this pose. */
    final Vector3f scratchTranslation = new Vector3f();

    /** Scratch for clips sampling into this pose. */
    final Vector3f scratchTranslation2 = new Vector3f();

    /**
     * Create a pose, starting at rest.
     *
     * @param skeleton The skeleton it is for.
     */
    public Pose(@NonNull Skeleton skeleton) {
        this.skeleton = skeleton;
        rotations = new float[skeleton.size() * 4];
        translations = new float[skeleton.size() * 3];
        reset();
    }

    /** Put every bone back at rest. */
    public void reset() {
        Quaternionf rotation = new Quaternionf();
        Vector3f translation = new Vector3f();
        for (int i = 0; i < skeleton.size(); ++i) {
            setRotation(i, skeleton.restRotation(i, rotation));
            setTranslation(i, skeleton.restTranslation(i, translation));
        }
    }

    /**
     * Copy another pose of the same skeleton.
     *
     * @param other The pose.
     */
    public void set(@NonNull Pose other) {
        System.arraycopy(other.rotations, 0, rotations, 0, rotations.length);
        System.arraycopy(other.translations, 0, translations, 0, translations.length);
    }

    /**
     * Blend toward another pose of the same skeleton: rotations by normalized lerp along the
     * shorter arc, translations by lerp.
     *
     * @param other The pose to blend toward.
     * @param weight How far, 0 for this pose and 1 for the other.
     * @param bones Which bones to blend, or null for all of them.
     */
    public void blend(@NonNull Pose other, float weight, boolean[] bones) {
        if (weight <= 0) {
            return;
        }
        float keep = 1 - weight;
        for (int b = 0; b < skeleton.size(); ++b) {
            if (bones != null && !bones[b]) {
                continue;
            }
            int r = b * 4;
            float dot =
                    rotations[r] * other.rotations[r]
                            + rotations[r + 1] * other.rotations[r + 1]
                            + rotations[r + 2] * other.rotations[r + 2]
                            + rotations[r + 3] * other.rotations[r + 3];
            float toward = dot < 0 ? -weight : weight;
            float x = rotations[r] * keep + other.rotations[r] * toward;
            float y = rotations[r + 1] * keep + other.rotations[r + 1] * toward;
            float z = rotations[r + 2] * keep + other.rotations[r + 2] * toward;
            float w = rotations[r + 3] * keep + other.rotations[r + 3] * toward;
            float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            if (length > 0) {
                setRotation(b, x / length, y / length, z / length, w / length);
            }
            int t = b * 3;
            for (int i = 0; i < 3; ++i) {
                translations[t + i] =
                        translations[t + i] * keep + other.translations[t + i] * weight;
            }
        }
    }

    /**
     * Set a bone's local rotation.
     *
     * @param bone The bone's index.
     * @param rotation The rotation.
     */
    public void setRotation(int bone, @NonNull Quaternionfc rotation) {
        setRotation(bone, rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    /**
     * Set a bone's local rotation.
     *
     * @param bone The bone's index.
     * @param x The quaternion's x.
     * @param y The quaternion's y.
     * @param z The quaternion's z.
     * @param w The quaternion's w.
     */
    public void setRotation(int bone, float x, float y, float z, float w) {
        rotations[bone * 4] = x;
        rotations[bone * 4 + 1] = y;
        rotations[bone * 4 + 2] = z;
        rotations[bone * 4 + 3] = w;
    }

    /**
     * Get a bone's local rotation.
     *
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public Quaternionf rotation(int bone, @NonNull Quaternionf out) {
        return out.set(
                rotations[bone * 4],
                rotations[bone * 4 + 1],
                rotations[bone * 4 + 2],
                rotations[bone * 4 + 3]);
    }

    /**
     * Set a bone's local translation.
     *
     * @param bone The bone's index.
     * @param translation The offset from its parent, in meters.
     */
    public void setTranslation(int bone, @NonNull Vector3fc translation) {
        translations[bone * 3] = translation.x();
        translations[bone * 3 + 1] = translation.y();
        translations[bone * 3 + 2] = translation.z();
    }

    /**
     * Get a bone's local translation.
     *
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public Vector3f translation(int bone, @NonNull Vector3f out) {
        return out.set(
                translations[bone * 3], translations[bone * 3 + 1], translations[bone * 3 + 2]);
    }
}
