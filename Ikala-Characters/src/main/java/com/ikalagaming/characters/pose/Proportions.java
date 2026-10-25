package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Slider;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A character's bone scales from its slider values. Length scales stretch the offset from a bone's
 * parent, moving its children, so rotation-only clips still fit. Shape scales only scale a bone's
 * own vertices on chosen axes, for girth and faces, and aren't passed on.
 *
 * <p>Longer legs also raise the hips and lengthen strides: the root's and its children's
 * translations are scaled by the leg scale, the ratio of the first IK leg's length to its rest
 * length.
 *
 * @author Ches Burks
 */
public final class Proportions {
    /** The skeleton these are for. */
    @Getter private final Skeleton skeleton;

    /** Length scales, 1 per bone. */
    private final float[] length;

    /** Shape scales, 3 per bone, on the bone's local x, y and z. */
    private final float[] shape;

    /** A uniform scale for the whole character. */
    @Getter private final float overallScale;

    /** How much longer the legs are than at rest. */
    @Getter private final float legScale;

    /**
     * Create proportions.
     *
     * @param skeleton The skeleton.
     * @param length Length scales, 1 per bone, which are kept.
     * @param shape Shape scales, 3 per bone, which are kept.
     * @param overallScale A uniform scale for the whole character.
     * @param legScale How much to scale the root's and its children's translations.
     */
    private Proportions(
            Skeleton skeleton, float[] length, float[] shape, float overallScale, float legScale) {
        this.skeleton = skeleton;
        this.length = length;
        this.shape = shape;
        this.overallScale = overallScale;
        this.legScale = legScale;
    }

    /**
     * Proportions with every scale at 1.
     *
     * @param skeleton The skeleton.
     * @return The proportions.
     */
    public static Proportions neutral(@NonNull Skeleton skeleton) {
        float[] length = new float[skeleton.size()];
        float[] shape = new float[skeleton.size() * 3];
        Arrays.fill(length, 1);
        Arrays.fill(shape, 1);
        return new Proportions(skeleton, length, shape, 1, 1);
    }

    /**
     * Work out proportions from slider values. Sliders without a value are at 0. Several ops on a
     * bone multiply.
     *
     * @param skeleton The skeleton.
     * @param plan The plan, whose first IK leg gives the leg scale.
     * @param sliders The plan's sliders.
     * @param values Slider values by ID: one number, or three for vector sliders.
     * @param overallScale A uniform scale for the whole character.
     * @return The proportions.
     * @throws IllegalArgumentException If a value has the wrong count for its slider, or a slider
     *     names a bone the skeleton lacks.
     */
    public static Proportions fromSliders(
            @NonNull Skeleton skeleton,
            @NonNull BodyPlan plan,
            @NonNull List<Slider> sliders,
            @NonNull Map<Id, float[]> values,
            float overallScale) {
        Proportions neutral = neutral(skeleton);
        float[] length = neutral.length;
        float[] shape = neutral.shape;
        for (Slider slider : sliders) {
            float[] value = values.getOrDefault(slider.id(), new float[slider.vector() ? 3 : 1]);
            if (value.length != (slider.vector() ? 3 : 1)) {
                throw new IllegalArgumentException(
                        "The slider "
                                + slider.id()
                                + " takes "
                                + (slider.vector() ? 3 : 1)
                                + " values, not "
                                + value.length);
            }
            for (Slider.Op op : slider.ops()) {
                for (String name : op.bones()) {
                    int bone = skeleton.index(name);
                    if (bone < 0) {
                        throw new IllegalArgumentException(
                                "The slider " + slider.id() + " uses the missing bone " + name);
                    }
                    if (op.type() == Slider.OpType.LENGTH) {
                        length[bone] *= op.scale(value[0]);
                        continue;
                    }
                    for (int axis = 0; axis < 3; ++axis) {
                        if (op.scalesAxis(axis)) {
                            shape[bone * 3 + axis] *= op.scale(value[slider.vector() ? axis : 0]);
                        }
                    }
                }
            }
        }
        return new Proportions(
                skeleton, length, shape, overallScale, legScale(skeleton, plan, length));
    }

    /**
     * Work out how much longer the first IK leg is than at rest.
     *
     * @param skeleton The skeleton.
     * @param plan The plan.
     * @param length The length scales.
     * @return The leg scale, or 1 if the plan has no IK legs.
     */
    private static float legScale(Skeleton skeleton, BodyPlan plan, float[] length) {
        if (plan.ik() == null || plan.ik().feet().isEmpty()) {
            return 1;
        }
        List<String> chain = plan.ik().feet().get(0).chain();
        Vector3f offset = new Vector3f();
        float rest = 0;
        float scaled = 0;
        // Each bone's offset from its parent is the length of the segment above it
        for (int i = 1; i < chain.size(); ++i) {
            int bone = skeleton.index(chain.get(i));
            if (bone < 0) {
                return 1;
            }
            float segment = skeleton.restTranslation(bone, offset).length();
            rest += segment;
            scaled += segment * length[bone];
        }
        return rest > 0 ? scaled / rest : 1;
    }

    /**
     * How much a bone's offset from its parent is scaled, including the leg scale for the root and
     * its children.
     *
     * @param bone The bone's index.
     * @return The scale.
     */
    public float translationScale(int bone) {
        int parent = skeleton.parent(bone);
        boolean nearRoot = parent < 0 || skeleton.parent(parent) < 0;
        return nearRoot ? length[bone] * legScale : length[bone];
    }

    /**
     * A bone's length scale.
     *
     * @param bone The bone's index.
     * @return The scale.
     */
    public float length(int bone) {
        return length[bone];
    }

    /**
     * A bone's shape scale on one of its local axes.
     *
     * @param bone The bone's index.
     * @param axis 0 for x, 1 for y, 2 for z.
     * @return The scale.
     */
    public float shape(int bone, int axis) {
        return shape[bone * 3 + axis];
    }
}
