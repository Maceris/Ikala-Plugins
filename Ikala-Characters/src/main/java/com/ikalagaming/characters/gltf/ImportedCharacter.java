package com.ikalagaming.characters.gltf;

import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Skeleton;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A character read from a glTF file: its skeleton, its skinned meshes as parts, its clips, and the
 * materials and images the parts use. Vertices are in the skeleton's model space at rest, so a part
 * needs no transform of its own, and bone indices follow the rig's order.
 *
 * @param name What the character is called, from the file name.
 * @param rig The skeleton's bones, parents first.
 * @param rest Each bone's rest transform, by name.
 * @param parts The skinned meshes.
 * @param clips The animations.
 * @param materials The materials parts refer to by index.
 * @param images The images materials refer to by index.
 * @author Ches Burks
 */
public record ImportedCharacter(
        @NonNull String name,
        @NonNull Rig rig,
        @NonNull Map<String, Skeleton.Rest> rest,
        @NonNull List<Part> parts,
        @NonNull List<ClipData> clips,
        @NonNull List<MaterialData> materials,
        @NonNull List<ImageData> images) {

    /**
     * One skinned mesh.
     *
     * @param name The mesh's name in the file.
     * @param vertices Scene vertices at rest, 14 floats each: position, normal, tangent, bitangent,
     *     texture coordinates.
     * @param joints Four bone indices per vertex, in the rig's order.
     * @param weights Four weights per vertex.
     * @param indices Triangle indices.
     * @param material The index of its material, or -1 for none.
     * @param min The minimum corner of its rest bounds.
     * @param max The maximum corner of its rest bounds.
     */
    public record Part(
            @NonNull String name,
            float @NonNull [] vertices,
            int @NonNull [] joints,
            float @NonNull [] weights,
            int @NonNull [] indices,
            int material,
            @NonNull Vector3f min,
            @NonNull Vector3f max) {}

    /**
     * One animation, not yet bound to a skeleton.
     *
     * @param name The animation's name.
     * @param duration How long it lasts, in seconds.
     * @param looping Whether it repeats.
     * @param rotations Rotation tracks.
     * @param translations Translation tracks, only for bones that move.
     */
    public record ClipData(
            @NonNull String name,
            float duration,
            boolean looping,
            @NonNull List<Clip.RotationTrack> rotations,
            @NonNull List<Clip.TranslationTrack> translations) {
        /**
         * Bind the clip to a skeleton of its rig.
         *
         * @param skeleton The skeleton.
         * @return The clip.
         */
        public Clip bind(@NonNull Skeleton skeleton) {
            return new Clip(skeleton, name, duration, looping, rotations, translations);
        }
    }

    /**
     * A material, as glTF's metallic-roughness model describes it.
     *
     * @param name The material's name.
     * @param baseColor The base color factor, linear RGBA.
     * @param image The index of its base color image, or -1 for none.
     * @param metallic The metallic factor.
     * @param roughness The roughness factor.
     */
    public record MaterialData(
            @NonNull String name,
            float @NonNull [] baseColor,
            int image,
            float metallic,
            float roughness) {}

    /**
     * An encoded image, like a PNG.
     *
     * @param name The image's name.
     * @param mimeType Its type, like "image/png".
     * @param bytes The encoded bytes.
     */
    public record ImageData(@NonNull String name, String mimeType, byte @NonNull [] bytes) {}

    /**
     * Find a clip.
     *
     * @param clipName The clip's name.
     * @return The clip, or null if there is none by that name.
     */
    public ClipData clip(@NonNull String clipName) {
        return clips.stream().filter(c -> c.name().equals(clipName)).findFirst().orElse(null);
    }

    /**
     * A skeleton for this character's rig at its rest pose.
     *
     * @return The skeleton.
     */
    public Skeleton skeleton() {
        return new Skeleton(rig, rest);
    }

    /**
     * The same character on another rig with the same bones and parents, like a rig definition
     * whose bones have kinds and an ID. Bone indices are renumbered to that rig's order.
     *
     * @param other The rig.
     * @return The character on that rig.
     * @throws IllegalArgumentException If the rigs' bones or parents differ.
     */
    public ImportedCharacter onRig(@NonNull Rig other) {
        if (other.bones().size() != rig.bones().size()) {
            throw new IllegalArgumentException(
                    "The rig "
                            + other.id()
                            + " has "
                            + other.bones().size()
                            + " bones, not "
                            + rig.bones().size());
        }
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < other.bones().size(); ++i) {
            order.put(other.bones().get(i).name(), i);
        }
        int[] remap = new int[rig.bones().size()];
        for (int i = 0; i < rig.bones().size(); ++i) {
            Rig.Bone bone = rig.bones().get(i);
            Integer to = order.get(bone.name());
            Rig.Bone match = to == null ? null : other.bones().get(to);
            if (match == null || !java.util.Objects.equals(match.parent(), bone.parent())) {
                throw new IllegalArgumentException(
                        "The rig "
                                + other.id()
                                + " doesn't have "
                                + bone.name()
                                + " under "
                                + bone.parent());
            }
            remap[i] = to;
        }
        List<Part> moved = new ArrayList<>();
        for (Part part : parts) {
            int[] joints = part.joints().clone();
            for (int j = 0; j < joints.length; ++j) {
                joints[j] = remap[joints[j]];
            }
            moved.add(
                    new Part(
                            part.name(),
                            part.vertices(),
                            joints,
                            part.weights(),
                            part.indices(),
                            part.material(),
                            part.min(),
                            part.max()));
        }
        return new ImportedCharacter(name, other, rest, moved, clips, materials, images);
    }
}
