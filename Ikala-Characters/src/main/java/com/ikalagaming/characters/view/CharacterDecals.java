package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.pose.Skeleton;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a body plan's decal places sit on a character: the box art is projected through, fixed to a
 * bone at rest and facing the character's front (+Z), and which of its parts show the decal.
 *
 * @author Ches Burks
 */
public final class CharacterDecals {
    /**
     * The matrix from the character's rest space into a decal place's box: x and y from 0 to 1
     * across the art, x to the viewer's right and y down the image as it is stored, and z from -1
     * at the box's back to 1 at its front. See {@link com.ikalagaming.graphics.SkinnedDecal}.
     *
     * @param skeleton The character's skeleton.
     * @param place The decal place.
     * @return The matrix, or null if the skeleton has no such bone.
     */
    public static Matrix4f restToBox(
            @NonNull Skeleton skeleton, @NonNull BodyPlan.DecalPlace place) {
        int bone = skeleton.index(place.bone());
        if (bone < 0) {
            return null;
        }
        Vector3f middle = PartAssembler.restPosition(skeleton, bone, new Vector3f());
        Vec3 offset = place.offsetOrZero();
        middle.add(offset.x(), offset.y(), offset.z());
        return new Matrix4f()
                .translation(0.5f, 0.5f, 0)
                .scale(1 / place.size(), -1 / place.size(), 2 / place.depth())
                .translate(-middle.x, -middle.y, -middle.z);
    }

    /**
     * Which of a character's parts show a decal place's art: body parts filling a slot it covers,
     * and layers covering one.
     *
     * @param plan The body plan.
     * @param place The decal place.
     * @param parts The definition each of the character's parts comes from, null where unknown.
     * @return Whether each part shows it.
     */
    public static boolean[] partsShowing(
            @NonNull BodyPlan plan, @NonNull BodyPlan.DecalPlace place, @NonNull List<Part> parts) {
        Set<String> covered = new HashSet<>(place.covers());
        boolean[] showing = new boolean[parts.size()];
        for (int p = 0; p < parts.size(); ++p) {
            Part part = parts.get(p);
            if (part == null) {
                continue;
            }
            for (String fill : part.fills()) {
                BodyPlan.Layer layer = part.kind() == Part.PartKind.LAYER ? plan.layer(fill) : null;
                boolean covers =
                        part.kind() == Part.PartKind.BODY
                                ? covered.contains(fill)
                                : layer != null
                                        && layer.covers().stream().anyMatch(covered::contains);
                showing[p] |= covers;
            }
        }
        return showing;
    }

    /** Static methods only. */
    private CharacterDecals() {}
}
