package com.ikalagaming.characters.pose;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns a pose and proportions into model space bone transforms, and those into the skinning
 * matrices a character's mesh is drawn with. Holds scratch space, so use one per thread.
 *
 * @author Ches Burks
 */
public final class PoseSolver {
    /** The skeleton it solves for. */
    private final Skeleton skeleton;

    /** Each bone's transform in model space, from the last solve. */
    private final Matrix4f[] model;

    /** Scratch for the solve. */
    private final Matrix4f local = new Matrix4f();

    /** Scratch for the palette. */
    private final Matrix4f skin = new Matrix4f();

    /** Scratch for the palette. */
    private final Matrix4f inverseBind = new Matrix4f();

    /** Scratch for the solve. */
    private final Quaternionf rotation = new Quaternionf();

    /** Scratch for the solve. */
    private final Vector3f translation = new Vector3f();

    /**
     * Create a solver.
     *
     * @param skeleton The skeleton it solves for.
     */
    public PoseSolver(@NonNull Skeleton skeleton) {
        this.skeleton = skeleton;
        model = new Matrix4f[skeleton.size()];
        for (int i = 0; i < model.length; ++i) {
            model[i] = new Matrix4f();
        }
    }

    /**
     * Compose the pose down the hierarchy into model space. Each bone's offset from its parent is
     * scaled by its length, and the whole character by the overall scale.
     *
     * @param pose The pose.
     * @param proportions The proportions.
     */
    public void solve(@NonNull Pose pose, @NonNull Proportions proportions) {
        for (int bone = 0; bone < model.length; ++bone) {
            pose.translation(bone, translation).mul(proportions.translationScale(bone));
            local.translation(translation).rotate(pose.rotation(bone, rotation));
            int parent = skeleton.parent(bone);
            if (parent < 0) {
                model[bone].scaling(proportions.getOverallScale()).mul(local);
            } else {
                model[parent].mul(local, model[bone]);
            }
        }
    }

    /**
     * A bone's transform in model space, from the last solve, for attaching items to sockets. Shape
     * scales aren't included, since they don't pass to what is attached.
     *
     * @param bone The bone's index.
     * @return The transform, which changes on the next solve.
     */
    public Matrix4fc model(int bone) {
        return model[bone];
    }

    /**
     * Write the skinning matrices from the last solve: each bone's model transform, with its shape
     * scale, times its inverse bind matrix. At rest with neutral proportions they are all identity.
     *
     * @param proportions The proportions the solve used, for the shape scales.
     * @param out Where to write, 16 floats per bone, column major.
     * @param offset Where in out to start.
     */
    public void writePalette(@NonNull Proportions proportions, @NonNull float[] out, int offset) {
        for (int bone = 0; bone < model.length; ++bone) {
            model[bone]
                    .scale(
                            proportions.shape(bone, 0),
                            proportions.shape(bone, 1),
                            proportions.shape(bone, 2),
                            skin)
                    .mul(skeleton.inverseBind(bone, inverseBind))
                    .get(out, offset + bone * Skeleton.MATRIX_FLOATS);
        }
    }
}
