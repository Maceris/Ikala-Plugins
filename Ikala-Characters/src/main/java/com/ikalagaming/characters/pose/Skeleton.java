package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.definition.Rig;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A rig's bones with their rest transforms, ready for posing. Bones are numbered in rig order, so
 * every parent comes before its children and poses can be solved in one pass.
 *
 * <p>Immutable, so one skeleton can be shared by every character of a plan.
 *
 * @author Ches Burks
 */
public final class Skeleton {
    /** Floats per bone in a matrix palette. */
    public static final int MATRIX_FLOATS = 16;

    /**
     * A bone's rest transform, relative to its parent, as it comes from the mesh asset.
     *
     * @param translation The offset from the parent, in meters.
     * @param rotation The rotation from the parent.
     */
    public record Rest(@NonNull Vector3fc translation, @NonNull Quaternionfc rotation) {}

    /** The bones' names, by index. */
    private final String[] names;

    /** The bones' parents' indices, by index, with -1 for the root. */
    private final int[] parents;

    /** What each bone is for. */
    private final Rig.BoneKind[] kinds;

    /** Rest translations, 3 floats per bone. */
    private final float[] restTranslations;

    /** Rest rotations, 4 floats per bone in x, y, z, w order. */
    private final float[] restRotations;

    /** The inverse of each bone's rest transform in model space, 16 floats per bone. */
    private final float[] inverseBind;

    /** Bone indices by name. */
    private final Map<String, Integer> indices = new HashMap<>();

    /**
     * Create a skeleton from a rig and the rest transforms of its bones.
     *
     * @param rig The rig, which gives the bones, their order and parents.
     * @param rest Rest transforms by bone name, which must cover every bone.
     * @throws IllegalArgumentException If a bone has no rest transform.
     */
    public Skeleton(@NonNull Rig rig, @NonNull Map<String, Rest> rest) {
        List<Rig.Bone> bones = rig.bones();
        int count = bones.size();
        names = new String[count];
        parents = new int[count];
        kinds = new Rig.BoneKind[count];
        restTranslations = new float[count * 3];
        restRotations = new float[count * 4];
        inverseBind = new float[count * MATRIX_FLOATS];

        for (int i = 0; i < count; ++i) {
            Rig.Bone bone = bones.get(i);
            Rest transform = rest.get(bone.name());
            if (transform == null) {
                throw new IllegalArgumentException("No rest transform for the bone " + bone.name());
            }
            names[i] = bone.name();
            kinds[i] = bone.kind();
            indices.put(bone.name(), i);
            parents[i] = bone.parent() == null ? -1 : indices.get(bone.parent());
            Vector3fc translation = transform.translation();
            restTranslations[i * 3] = translation.x();
            restTranslations[i * 3 + 1] = translation.y();
            restTranslations[i * 3 + 2] = translation.z();
            Quaternionfc rotation = transform.rotation();
            restRotations[i * 4] = rotation.x();
            restRotations[i * 4 + 1] = rotation.y();
            restRotations[i * 4 + 2] = rotation.z();
            restRotations[i * 4 + 3] = rotation.w();
        }

        Matrix4f[] model = new Matrix4f[count];
        Quaternionf rotation = new Quaternionf();
        for (int i = 0; i < count; ++i) {
            rotation.set(
                    restRotations[i * 4],
                    restRotations[i * 4 + 1],
                    restRotations[i * 4 + 2],
                    restRotations[i * 4 + 3]);
            Matrix4f local =
                    new Matrix4f()
                            .translation(
                                    restTranslations[i * 3],
                                    restTranslations[i * 3 + 1],
                                    restTranslations[i * 3 + 2])
                            .rotate(rotation);
            model[i] = parents[i] < 0 ? local : new Matrix4f(model[parents[i]]).mul(local);
            new Matrix4f(model[i]).invert().get(inverseBind, i * MATRIX_FLOATS);
        }
    }

    /**
     * How many bones there are.
     *
     * @return The count.
     */
    public int size() {
        return names.length;
    }

    /**
     * Find a bone.
     *
     * @param name The bone's name.
     * @return Its index, or -1 if there is no such bone.
     */
    public int index(@NonNull String name) {
        return indices.getOrDefault(name, -1);
    }

    /**
     * A bone's name.
     *
     * @param bone The bone's index.
     * @return Its name.
     */
    public String name(int bone) {
        return names[bone];
    }

    /**
     * A bone's parent.
     *
     * @param bone The bone's index.
     * @return The parent's index, or -1 for the root.
     */
    public int parent(int bone) {
        return parents[bone];
    }

    /**
     * What a bone is for.
     *
     * @param bone The bone's index.
     * @return Its kind.
     */
    public Rig.BoneKind kind(int bone) {
        return kinds[bone];
    }

    /**
     * Get a bone's rest translation.
     *
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public Vector3f restTranslation(int bone, @NonNull Vector3f out) {
        return out.set(
                restTranslations[bone * 3],
                restTranslations[bone * 3 + 1],
                restTranslations[bone * 3 + 2]);
    }

    /**
     * Get a bone's rest rotation.
     *
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public Quaternionf restRotation(int bone, @NonNull Quaternionf out) {
        return out.set(
                restRotations[bone * 4],
                restRotations[bone * 4 + 1],
                restRotations[bone * 4 + 2],
                restRotations[bone * 4 + 3]);
    }

    /**
     * Get the inverse of a bone's rest transform in model space.
     *
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public Matrix4f inverseBind(int bone, @NonNull Matrix4f out) {
        return out.set(inverseBind, bone * MATRIX_FLOATS);
    }
}
