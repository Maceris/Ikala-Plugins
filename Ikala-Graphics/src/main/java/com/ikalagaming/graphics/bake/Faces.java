package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Whether one placement's face is hidden by its neighbor, for the plugin that knows the grid to
 * work out each placement's face mask before composing a section. Graphics doesn't decide this
 * itself, since only that plugin knows which placements are next to each other.
 *
 * <p>A face is hidden when everything it touches is covered by the neighbor's face on the other
 * side, and the neighbor blocks sight there:
 *
 * <ul>
 *   <li>An opaque neighbor hides anything.
 *   <li>A translucent neighbor only hides the same translucent material, so glass next to glass has
 *       no face between them, but glass in front of stone still shows the stone.
 *   <li>A cut out neighbor hides nothing, since you can see through its holes.
 * </ul>
 */
public final class Faces {

    /**
     * Whether a placement's face is hidden by the neighbor on that side.
     *
     * @param hidden The placement whose face may be hidden.
     * @param hiddenRotation Its rotation, one of {@link CubeRotations}.
     * @param face The side the neighbor is on, after rotation.
     * @param neighbor The neighbor on that side.
     * @param neighborRotation The neighbor's rotation.
     * @return True if the face can be dropped. False if there is nothing on that face.
     */
    public static boolean hides(
            @NonNull BakeSource hidden,
            int hiddenRotation,
            @NonNull Face face,
            @NonNull BakeSource neighbor,
            int neighborRotation) {
        if (!blocksSight(neighbor, hidden)) {
            return false;
        }
        final Face hiddenFace = CubeRotations.turn(CubeRotations.inverse(hiddenRotation), face);
        final FaceCoverage hiddenCoverage = hidden.getCoverage(hiddenFace);
        if (hiddenCoverage.isEmpty()) {
            return false;
        }
        final Face neighborFace =
                CubeRotations.turn(CubeRotations.inverse(neighborRotation), face.opposite());
        final FaceCoverage neighborCoverage = neighbor.getCoverage(neighborFace);
        if (neighborCoverage.isEmpty()) {
            return false;
        }
        final long[] touches =
                CubeRotations.turnMask(hiddenCoverage.touches(), hiddenRotation, hiddenFace);
        final long[] covers =
                CubeRotations.turnMask(neighborCoverage.covers(), neighborRotation, neighborFace);
        return FaceCoverage.contains(touches, covers);
    }

    /**
     * Which of a placement's faces its neighbors hide, as a face mask for composing.
     *
     * @param placement The placement.
     * @param rotation Its rotation.
     * @param neighbors The neighbor on each side, by face ordinal, null where there is none.
     * @param neighborRotations Each neighbor's rotation, by face ordinal.
     * @return The hidden faces, one bit per {@link Face#bit()}, after rotation.
     */
    public static int hiddenFaces(
            @NonNull BakeSource placement,
            int rotation,
            @Nullable BakeSource @NonNull [] neighbors,
            int @NonNull [] neighborRotations) {
        int mask = 0;
        for (Face face : Face.ALL) {
            final BakeSource neighbor = neighbors[face.ordinal()];
            if (neighbor != null
                    && hides(
                            placement,
                            rotation,
                            face,
                            neighbor,
                            neighborRotations[face.ordinal()])) {
                mask |= face.bit();
            }
        }
        return mask;
    }

    /**
     * Whether a neighbor's material stops the other placement from being seen through it.
     *
     * @param neighbor The neighbor in front.
     * @param hidden The placement behind it.
     * @return True if it blocks sight.
     */
    private static boolean blocksSight(@NonNull BakeSource neighbor, @NonNull BakeSource hidden) {
        return switch (neighbor.getTransparency()) {
            case OPAQUE -> true;
            case CUTOUT -> false;
            case TRANSLUCENT ->
                    hidden.getTransparency() == Material.Transparency.TRANSLUCENT
                            && Objects.equals(neighbor.getMaterial(), hidden.getMaterial());
        };
    }

    /** Static helpers only. */
    private Faces() {}
}
