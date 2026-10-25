package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import lombok.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A bone hierarchy with standard names. The bones' rest transforms come from the rig's mesh asset;
 * listing the bones here lets plans, sliders and parts be checked without loading meshes.
 *
 * @param id This rig's ID.
 * @param bones The bones, each listed after its parent.
 * @author Ches Burks
 */
@Doc(
        "A bone hierarchy with standard names. Rest transforms come from the rig's mesh asset;"
                + " listing the bones lets plans, sliders and parts be checked without loading"
                + " meshes.")
public record Rig(
        @Doc("This rig's ID, like \"ikala:rig/biped\".") Id id,
        @Doc("The bones, each listed after its parent. Exactly one has no parent.")
                List<Bone> bones)
        implements Definition {

    /** The field listing the bones. */
    public static final String FIELD_BONES = "bones";

    /** What a bone name may contain. Names follow Blender's .L/.R convention. */
    static final Pattern BONE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    /** What a bone is for. */
    public enum BoneKind {
        /** Animated by clips and skinned by meshes. */
        DEFORM,
        /** A scale-only child for face and girth sliders, never animated or inherited. */
        SHAPE,
        /** An attach point with no weights, like socket.back. */
        SOCKET
    }

    /**
     * One bone.
     *
     * @param name The bone's name.
     * @param parent Its parent's name, or null for the root.
     * @param kind What it is for.
     */
    @Doc("One bone.")
    public record Bone(
            @Doc("The bone's name, like \"forearm.L\".") String name,
            @Doc("The parent bone's name. Only the root leaves it out.") @Optional String parent,
            @Doc("What the bone is for.") @Default("\"deform\"") BoneKind kind) {
        /** The field for the bone's name. */
        public static final String FIELD_NAME = "name";

        /** The field for the parent's name. */
        public static final String FIELD_PARENT = "parent";
    }

    /**
     * Find a bone.
     *
     * @param name The bone's name.
     * @return The bone, or null if this rig doesn't have it.
     */
    public Bone bone(@NonNull String name) {
        for (Bone bone : bones) {
            if (bone.name().equals(name)) {
                return bone;
            }
        }
        return null;
    }

    /**
     * Whether a bone is the parent of another.
     *
     * @param parent The possible parent's name.
     * @param child The child's name.
     * @return Whether both exist and the first is the second's parent.
     */
    public boolean isParent(@NonNull String parent, @NonNull String child) {
        Bone bone = bone(child);
        return bone != null && parent.equals(bone.parent());
    }

    @Override
    public void check(Checks checks) {
        Set<String> seen = new HashSet<>();
        int roots = 0;
        for (int i = 0; i < bones.size(); ++i) {
            Bone bone = bones.get(i);
            String path = JsonPath.at(FIELD_BONES, i);
            if (!BONE_NAME.matcher(bone.name()).matches()) {
                checks.error(
                        JsonPath.field(path, Bone.FIELD_NAME),
                        "The bone name '"
                                + bone.name()
                                + "' may only use letters, digits, _, . and -");
            }
            if (bone.parent() == null) {
                ++roots;
            } else if (!seen.contains(bone.parent())) {
                checks.error(
                        JsonPath.field(path, Bone.FIELD_PARENT),
                        bone(bone.parent()) == null
                                ? "There is no bone named '" + bone.parent() + "'"
                                : "The parent '" + bone.parent() + "' must be listed first");
            }
            if (!seen.add(bone.name())) {
                checks.error(
                        JsonPath.field(path, Bone.FIELD_NAME),
                        "The bone '" + bone.name() + "' is listed twice");
            }
        }
        if (roots != 1) {
            checks.error(
                    JsonPath.at(FIELD_BONES), "A rig needs exactly one root bone, not " + roots);
        }
    }
}
