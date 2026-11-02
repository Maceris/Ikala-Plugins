package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How far accessories move out from where they were fitted on the bare body, so they rest on what
 * is worn under them: along their socket's resting direction (see {@link BodyPlan.Rest}), by how
 * far the body part in the slot they rest on stands out, plus how far the clothing over it stands
 * out from it.
 *
 * @author Ches Burks
 */
public final class AccessoryRests {
    /** Floats per vertex: position, normal, tangent, bitangent, uv. */
    private static final int STRIDE = 14;

    /**
     * How far worn parts stand out from the bare body over a slot.
     *
     * @param plan The body plan.
     * @param slot The body slot.
     * @param worn The parts worn.
     * @return The distance in meters: how far the body part stands out, plus the thickest shown
     *     layer over it.
     */
    public static float standoff(
            @NonNull BodyPlan plan, @NonNull String slot, @NonNull Collection<Part> worn) {
        Set<String> hidden = new HashSet<>();
        worn.forEach(part -> hidden.addAll(part.hidesLayers()));
        float body = 0;
        float clothing = 0;
        for (Part part : worn) {
            if (part.kind() == Part.PartKind.BODY && part.fills().contains(slot)) {
                Float own = part.standoffOver(slot);
                body = Math.max(body, own != null ? own : plan.formOffset(part.form()));
            } else if (part.kind() == Part.PartKind.LAYER) {
                for (String fill : part.fills()) {
                    BodyPlan.Layer layer = plan.layer(fill);
                    if (layer == null || hidden.contains(fill) || !layer.covers().contains(slot)) {
                        continue;
                    }
                    Float own = part.standoffOver(slot);
                    clothing = Math.max(clothing, own != null ? own : layer.standoff());
                }
            }
        }
        // Clothing is pushed out by as far as the body part stands out, then stands out itself
        return body + clothing;
    }

    /**
     * How far an accessory on a socket moves.
     *
     * @param plan The body plan.
     * @param socket The socket, or null.
     * @param worn The parts worn.
     * @return The move in model space, zero if the socket has no resting place.
     */
    public static Vector3f push(
            @NonNull BodyPlan plan, BodyPlan.Socket socket, @NonNull Collection<Part> worn) {
        if (socket == null || socket.rests() == null) {
            return new Vector3f();
        }
        Vec3 out = socket.rests().out();
        return new Vector3f(out.x(), out.y(), out.z())
                .normalize(standoff(plan, socket.rests().slot(), worn));
    }

    /**
     * The socket an accessory part goes on: the first of the sockets it fits.
     *
     * @param plan The body plan.
     * @param part The part.
     * @return The socket, or null if it fits none of the plan's.
     */
    public static BodyPlan.Socket socketOf(@NonNull BodyPlan plan, @NonNull Part part) {
        for (String fit : part.fits()) {
            List<BodyPlan.Socket> found = plan.socketsFor(fit);
            if (!found.isEmpty()) {
                return found.getFirst();
            }
        }
        return null;
    }

    /**
     * A mesh moved whole.
     *
     * @param part The mesh.
     * @param by How far, in model space.
     * @return The moved mesh, or the same mesh for no move.
     */
    public static ImportedCharacter.Part moved(
            @NonNull ImportedCharacter.Part part, @NonNull Vector3fc by) {
        if (by.lengthSquared() == 0) {
            return part;
        }
        float[] vertices = part.vertices().clone();
        for (int base = 0; base < vertices.length; base += STRIDE) {
            vertices[base] += by.x();
            vertices[base + 1] += by.y();
            vertices[base + 2] += by.z();
        }
        return part.withVertices(
                vertices, new Vector3f(part.min()).add(by), new Vector3f(part.max()).add(by));
    }

    /** Static methods only. */
    private AccessoryRests() {}
}
