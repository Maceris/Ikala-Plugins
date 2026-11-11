package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.pose.SpringChains;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.DebugDraw;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.debug.DebugArrow;
import com.ikalagaming.graphics.scene.debug.DebugLabel;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugSphere;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the shown character's bones over it as posed, after its wings and springs: a line from each
 * bone's head to its child's, the extension chains' bones and joints marked, and the capsules the
 * chains keep out of. For seeing why a cape folds or where a wing sits.
 *
 * @author Ches Burks
 */
final class BoneView {
    /** Body bones: light gray. */
    private static final int BODY_COLOR = Color.rgba(220, 220, 220, 255);

    /** Extension chain bones and joints: orange. */
    private static final int CHAIN_COLOR = Color.rgba(255, 140, 0, 255);

    /** Capsules points are pushed straight out of: cyan. */
    private static final int CAPSULE_COLOR = Color.rgba(0, 220, 255, 255);

    /** One-way capsules, and the side they push to: magenta. */
    private static final int ONE_WAY_COLOR = Color.rgba(255, 60, 200, 255);

    /** Chain bone names: white. */
    private static final int NAME_COLOR = Color.rgba(255, 255, 255, 255);

    /** How big the chain joints' markers are, in meters. */
    private static final double JOINT_RADIUS = 0.01;

    /** Draws the shapes. */
    private final DebugDraw debug;

    /** The bones as last posed, {@link ShownCharacter#BONE_FLOATS} floats each. */
    private float[] bones = new float[0];

    /** Whether each bone has a child, for the skeleton last drawn. */
    private boolean[] parents = new boolean[0];

    /** The skeleton {@link #parents} was found for. */
    private Skeleton parentsOf;

    /** Scratch. */
    private final Vector3f model = new Vector3f();

    /**
     * Set up the view.
     *
     * @param debug Draws the shapes.
     */
    BoneView(@NonNull DebugDraw debug) {
        this.debug = debug;
    }

    /**
     * Draw the bones for the next frame.
     *
     * @param shown The shown character.
     * @param onChain Which bones are on worn extension chains, by index.
     * @param springs The chains swinging, for their capsules, or null.
     * @param names Whether to name the chains' bones.
     */
    void draw(
            @NonNull ShownCharacter shown,
            boolean @NonNull [] onChain,
            SpringChains springs,
            boolean names) {
        Skeleton skeleton = shown.getSkeleton();
        int size = skeleton.size() * ShownCharacter.BONE_FLOATS;
        if (bones.length != size) {
            bones = new float[size];
        }
        if (!shown.posedBones(bones)) {
            return;
        }
        if (parentsOf != skeleton) {
            parentsOf = skeleton;
            parents = new boolean[skeleton.size()];
            for (int b = 0; b < skeleton.size(); ++b) {
                if (skeleton.parent(b) >= 0) {
                    parents[skeleton.parent(b)] = true;
                }
            }
        }
        for (int b = 0; b < skeleton.size(); ++b) {
            boolean chain = b < onChain.length && onChain[b];
            int parent = skeleton.parent(b);
            if (parent >= 0) {
                debug.draw(
                        new DebugLine(
                                head(shown, parent),
                                head(shown, b),
                                chain ? CHAIN_COLOR : BODY_COLOR,
                                true));
            }
            if (!chain) {
                continue;
            }
            Vector3d at = head(shown, b);
            debug.draw(new DebugSphere(at, JOINT_RADIUS, CHAIN_COLOR, true));
            if (!parents[b] && parent >= 0) {
                // A chain's last bone reaches as far along its y axis as the bone before it is
                // long, as the springs take it
                Vector3d tip = tip(shown, b, head(shown, parent).distance(at));
                debug.draw(new DebugLine(at, tip, CHAIN_COLOR, true));
                debug.draw(new DebugSphere(tip, JOINT_RADIUS, CHAIN_COLOR, true));
            }
            if (names) {
                debug.label(new DebugLabel(at, skeleton.name(b), NAME_COLOR));
            }
        }
        if (springs != null) {
            drawCapsules(shown, springs.capsules());
        }
    }

    /**
     * Draw the capsules: a line along each, its ends' spheres, and for a one-way capsule an arrow
     * to the side it pushes to.
     *
     * @param shown The shown character.
     * @param capsules The capsules.
     */
    private void drawCapsules(ShownCharacter shown, List<SpringChains.Capsule> capsules) {
        for (SpringChains.Capsule capsule : capsules) {
            int color = capsule.out() == null ? CAPSULE_COLOR : ONE_WAY_COLOR;
            Vector3d from = head(shown, capsule.from());
            Vector3d to = head(shown, capsule.to());
            debug.draw(new DebugLine(from, to, color, true));
            debug.draw(new DebugSphere(from, capsule.radius(), color, false));
            debug.draw(new DebugSphere(to, capsule.radius(), color, false));
            if (capsule.out() != null) {
                Vector3d middle = new Vector3d(from).add(to).mul(0.5);
                shown.facing().transform(model.set(capsule.out()));
                Vector3d side = new Vector3d(middle).fma(capsule.radius(), new Vector3d(model));
                debug.draw(new DebugArrow(middle, side, color, true));
            }
        }
    }

    /**
     * A bone's head in world space.
     *
     * @param shown The shown character.
     * @param bone The bone.
     * @return The point.
     */
    private Vector3d head(ShownCharacter shown, int bone) {
        int at = bone * ShownCharacter.BONE_FLOATS;
        shown.facing().transform(model.set(bones[at], bones[at + 1], bones[at + 2]));
        return new Vector3d(shown.position()).add(model.x, model.y, model.z);
    }

    /**
     * A point along a bone's y axis in world space.
     *
     * @param shown The shown character.
     * @param bone The bone.
     * @param length How far along.
     * @return The point.
     */
    private Vector3d tip(ShownCharacter shown, int bone, double length) {
        int at = bone * ShownCharacter.BONE_FLOATS;
        shown.facing().transform(model.set(bones[at + 3], bones[at + 4], bones[at + 5]));
        return head(shown, bone).fma(length, new Vector3d(model));
    }
}
