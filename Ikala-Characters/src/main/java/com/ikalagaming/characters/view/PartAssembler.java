package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.graphics.SkinnedPart;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one character out of part definitions: each part's mesh for a variant, read from the glTF
 * file its definition names and put on the plan's rig, all merged together with their materials,
 * images and clips. Every file is read once, however many parts come from it.
 *
 * <p>A part names its mesh as {@code file.glb#mesh}, relative to its namespace's content folder. A
 * glTF mesh with several materials comes in as several parts, all belonging to the one part
 * definition.
 *
 * @author Ches Burks
 */
@Slf4j
public final class PartAssembler {
    /** What separates a mesh's file from its name in a part's mesh path. */
    public static final char MESH_SEPARATOR = '#';

    /** A tint mask channel that takes no palette color. */
    public static final int NO_CHANNEL = -1;

    /** What joins a body part's mesh name to a seam's in the name of its cap mesh. */
    public static final String CAP_INFIX = "_cap_";

    /** Floats per vertex: position, normal, tangent, bitangent and texture coordinates. */
    private static final int VERTEX_FLOATS = 14;

    /** The tint mask channels, in order. */
    private static final List<String> MASK_CHANNELS = List.of("r", "g", "b", "a");

    /** The most a color's channel holds, see {@link Color}. */
    private static final float COLOR_MAX = 255;

    /**
     * A character built from parts.
     *
     * @param character The merged character, on the plan's rig.
     * @param partIds Which part definition each of the character's parts comes from.
     * @param channels For each of the character's parts, the palette channel each tint mask channel
     *     takes, as a position in {@link BodyPlan#allChannels()}, or {@link #NO_CHANNEL}.
     * @param fallbacks For each of the character's parts, the color each tint mask channel shows
     *     while the design leaves its palette channel uncolored: its material slot's default,
     *     packed as {@link SkinnedPart#pack} packs, or white for none.
     * @param forms For each of the character's parts, the clothing form its mesh is for, or null
     *     for the part's plain mesh.
     */
    public record Assembled(
            @NonNull ImportedCharacter character,
            @NonNull List<Id> partIds,
            @NonNull List<int[]> channels,
            @NonNull List<int[]> fallbacks,
            @NonNull List<String> forms) {
        /**
         * The character's parts that come from a part definition's plain mesh.
         *
         * @param part The part definition's ID.
         * @return The indices of its meshes' parts.
         */
        public List<Integer> partsOf(@NonNull Id part) {
            return partsOf(part, null);
        }

        /**
         * The character's parts that come from a part definition's mesh for a clothing form.
         *
         * @param part The part definition's ID.
         * @param form The form, or null for the plain mesh.
         * @return The indices of its meshes' parts.
         */
        public List<Integer> partsOf(@NonNull Id part, String form) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < partIds.size(); ++i) {
                if (partIds.get(i).equals(part) && java.util.Objects.equals(forms.get(i), form)) {
                    indices.add(i);
                }
            }
            return indices;
        }
    }

    /**
     * The mesh a part uses on a variant.
     *
     * @param part The part.
     * @param variant The variant.
     * @return Its mesh path, or null if it has none for the variant.
     */
    public static String meshFor(@NonNull Part part, @NonNull String variant) {
        String mesh = part.meshes().get(variant);
        return mesh != null ? mesh : part.meshes().get(Part.ANY_VARIANT);
    }

    /**
     * A layer's mesh for a variant and clothing form: the form's own mesh for the variant, else for
     * any variant, else the same for each thinner form, else the plain mesh for the variant.
     *
     * @param part The part.
     * @param variant The variant.
     * @param form The clothing form, or null for the plain mesh.
     * @param plan The plan, whose forms go thinnest first.
     * @return The mesh asset, or null if the part has none for the variant.
     */
    public static String meshFor(
            @NonNull Part part, @NonNull String variant, String form, @NonNull BodyPlan plan) {
        for (int index = form == null ? -1 : plan.formIndex(form); index >= 0; --index) {
            String suffix = Part.FORM_SEPARATOR + plan.forms().get(index).id();
            String mesh = part.meshes().get(variant + suffix);
            if (mesh == null) {
                mesh = part.meshes().get(Part.ANY_VARIANT + suffix);
            }
            if (mesh != null) {
                return mesh;
            }
        }
        return meshFor(part, variant);
    }

    /**
     * The clothing forms a part has meshes of its own for, for a variant.
     *
     * @param part The part.
     * @param variant The variant.
     * @param plan The plan.
     * @return The forms' names, thinnest first.
     */
    public static List<String> formsOf(
            @NonNull Part part, @NonNull String variant, @NonNull BodyPlan plan) {
        List<String> found = new ArrayList<>();
        for (BodyPlan.Form form : plan.forms()) {
            String suffix = Part.FORM_SEPARATOR + form.id();
            if (part.meshes().containsKey(variant + suffix)
                    || part.meshes().containsKey(Part.ANY_VARIANT + suffix)) {
                found.add(form.id());
            }
        }
        return found;
    }

    /**
     * The name of the mesh that closes a body part's seam ring, shown where the neighbor across the
     * seam is open, like a skeleton forearm below a regular upper arm.
     *
     * @param mesh The body part's mesh name.
     * @param seam The seam.
     * @return The cap mesh's name in the same file.
     */
    public static String capMesh(@NonNull String mesh, @NonNull String seam) {
        return mesh + CAP_INFIX + seam;
    }

    /**
     * The seams where each body part shows a cap: those it welds where the body part across is
     * open. Seams inside a part that fills several slots aren't between parts, so have none.
     *
     * @param parts The parts.
     * @param plan The plan.
     * @return Each capped part's capped seams.
     */
    public static Map<Part, List<String>> cappedSeams(
            @NonNull Collection<Part> parts, @NonNull BodyPlan plan) {
        Map<String, List<Part>> bySeam = new HashMap<>();
        for (Part part : parts) {
            for (String seam : part.outerSeams(plan)) {
                bySeam.computeIfAbsent(seam, s -> new ArrayList<>()).add(part);
            }
        }
        Map<Part, List<String>> capped = new LinkedHashMap<>();
        for (Part part : parts) {
            for (String seam : part.outerSeams(plan)) {
                if (part.seamMode(seam) != Part.SeamMode.WELD) {
                    continue;
                }
                boolean openAcross =
                        bySeam.get(seam).stream()
                                .anyMatch(
                                        other ->
                                                other != part
                                                        && other.seamMode(seam)
                                                                == Part.SeamMode.OPEN);
                if (openAcross) {
                    capped.computeIfAbsent(part, p -> new ArrayList<>()).add(seam);
                }
            }
        }
        return capped;
    }

    /**
     * Build a character out of parts. Parts with no mesh for the variant, or whose file can't be
     * read, are left out with a warning.
     *
     * @param parts The parts, all for the plan.
     * @param plan The plan.
     * @param variant The variant, which picks each part's mesh.
     * @param registry The loaded definitions, which find the parts' files.
     * @param reference The rig's reference file, for moving characters from other rigs onto it.
     * @return The character, or null if no part could be loaded.
     */
    public static Assembled assemble(
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull String variant,
            @NonNull CharacterRegistry registry,
            @NonNull Path reference) {
        return assemble(parts, plan, variant, registry, reference, new HashMap<>());
    }

    /**
     * Build a character out of parts, reusing files read before. Reading and moving a file onto the
     * plan's rig is the slow part, so an editor keeps the files it has read.
     *
     * @param parts The parts, all for the plan.
     * @param plan The plan.
     * @param variant The variant, which picks each part's mesh.
     * @param registry The loaded definitions, which find the parts' files.
     * @param reference The rig's reference file, for moving characters from other rigs onto it.
     * @param files Files read before, on the plan's rig, added to; null for a file that couldn't be
     *     read. It must be safe to use from the calling thread.
     * @return The character, or null if no part could be loaded.
     */
    public static Assembled assemble(
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull String variant,
            @NonNull CharacterRegistry registry,
            @NonNull Path reference,
            @NonNull Map<Path, ImportedCharacter> files) {
        return assemble(parts, plan, variant, registry, reference, files, false);
    }

    /**
     * Build a character out of parts as {@link #assemble(Collection, BodyPlan, String,
     * CharacterRegistry, Path, Map)} does, and with every layer's meshes for each clothing form
     * too, for an editor that switches between them as body parts change; see {@link
     * Assembled#partsOf(Id, String)}.
     *
     * @param parts The parts, all for the plan.
     * @param plan The plan.
     * @param variant The variant, which picks each part's mesh.
     * @param registry The loaded definitions, which find the parts' files.
     * @param reference The rig's reference file, for moving characters from other rigs onto it.
     * @param files Files read before, on the plan's rig, added to.
     * @param allForms Whether to add each layer's meshes for every clothing form it has.
     * @return The character, or null if no part could be loaded.
     */
    public static Assembled assemble(
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull String variant,
            @NonNull CharacterRegistry registry,
            @NonNull Path reference,
            @NonNull Map<Path, ImportedCharacter> files,
            boolean allForms) {
        return assemble(parts, plan, variant, registry, reference, files, allForms, Map.of());
    }

    /**
     * Build a character out of parts as {@link #assemble(Collection, BodyPlan, String,
     * CharacterRegistry, Path, Map, boolean)} does, with layers whose meshes have form targets
     * pushed out over the slots of bulky body parts (see {@link PartForms}).
     *
     * @param parts The parts, all for the plan.
     * @param plan The plan.
     * @param variant The variant, which picks each part's mesh.
     * @param registry The loaded definitions, which find the parts' files.
     * @param reference The rig's reference file, for moving characters from other rigs onto it.
     * @param files Files read before, on the plan's rig, added to.
     * @param allForms Whether to add each layer's meshes for every clothing form it has.
     * @param slotForms The clothing form of the body part in each body slot; see {@link
     *     com.ikalagaming.characters.design.WearRules#slotForms}.
     * @return The character, or null if no part could be loaded. Unless every form is added, the
     *     parts are all worn together, so accessories move out to rest on them (see {@link
     *     AccessoryRests}).
     */
    public static Assembled assemble(
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull String variant,
            @NonNull CharacterRegistry registry,
            @NonNull Path reference,
            @NonNull Map<Path, ImportedCharacter> files,
            boolean allForms,
            @NonNull Map<String, String> slotForms) {
        List<String> channels = plan.allChannels();
        Map<Part, List<String>> caps = cappedSeams(parts, plan);
        Merger merger = new Merger(PartForms.amounts(plan, slotForms));
        // Placements turn about the sockets of the rig's reference body, read when first needed
        Skeleton[] referenceSkeleton = {null};
        for (Part part : parts) {
            Vector3f push =
                    allForms || part.kind() != Part.PartKind.ACCESSORY
                            ? null
                            : AccessoryRests.push(
                                    plan, AccessoryRests.socketOf(plan, part), parts, variant);
            addMesh(
                    part,
                    meshFor(part, variant),
                    null,
                    push,
                    caps,
                    plan,
                    variant,
                    registry,
                    reference,
                    files,
                    merger,
                    channels,
                    referenceSkeleton);
            if (allForms && part.kind() == Part.PartKind.LAYER) {
                for (String form : formsOf(part, variant, plan)) {
                    addMesh(
                            part,
                            meshFor(part, variant, form, plan),
                            form,
                            null,
                            Map.of(),
                            plan,
                            variant,
                            registry,
                            reference,
                            files,
                            merger,
                            channels,
                            referenceSkeleton);
                }
            }
        }
        return merger.result();
    }

    /**
     * Add one of a part's meshes, and its caps, to a character being built.
     *
     * @param part The part.
     * @param mesh The mesh asset, or null to add nothing.
     * @param form The clothing form the mesh is for, or null for the part's plain mesh.
     * @param push How far an accessory moves out to rest on what is worn, or null.
     * @param caps The seams where each part shows a cap.
     * @param plan The plan.
     * @param variant The variant.
     * @param registry The loaded definitions.
     * @param reference The rig's reference file.
     * @param files Files read before, added to.
     * @param merger Where the meshes go.
     * @param channels The palette channels tints are numbered by.
     * @param referenceSkeleton The reference body's skeleton, read when first needed, in [0].
     */
    private static void addMesh(
            Part part,
            String mesh,
            String form,
            Vector3f push,
            Map<Part, List<String>> caps,
            BodyPlan plan,
            String variant,
            CharacterRegistry registry,
            Path reference,
            Map<Path, ImportedCharacter> files,
            Merger merger,
            List<String> channels,
            Skeleton[] referenceSkeleton) {
        if (mesh == null) {
            return;
        }
        int split = mesh.indexOf(MESH_SEPARATOR);
        String fileName = split < 0 ? mesh : mesh.substring(0, split);
        String meshName = split < 0 ? null : mesh.substring(split + 1);
        Path file = registry.asset(part.id().namespace(), fileName);
        if (file == null) {
            log.warn("{} names {}, but its namespace has no folder on disk", part.id(), mesh);
            return;
        }
        ImportedCharacter source = files.get(file);
        if (source == null && !files.containsKey(file)) {
            source = read(file, reference, registry, plan);
            files.put(file, source);
        }
        if (source != null) {
            Part.Placement placement =
                    part.kind() == Part.PartKind.ACCESSORY
                            ? part.placementFor(variant)
                            : Part.Placement.NONE;
            if (push != null && push.lengthSquared() > 0) {
                placement = placement.movedBy(push);
            }
            if (placement != Part.Placement.NONE && referenceSkeleton[0] == null) {
                ImportedCharacter body = files.get(reference);
                if (body == null && !files.containsKey(reference)) {
                    body = read(reference, reference, registry, plan);
                    files.put(reference, body);
                }
                referenceSkeleton[0] = body == null ? source.skeleton() : body.skeleton();
            }
            merger.add(source, part, meshName, channels, placement, referenceSkeleton[0], form);
            for (String seam : caps.getOrDefault(part, List.of())) {
                if (meshName == null) {
                    log.warn("{} has no mesh name to find its {} cap by", part.id(), seam);
                    continue;
                }
                merger.add(
                        source,
                        part,
                        capMesh(meshName, seam),
                        channels,
                        Part.Placement.NONE,
                        null,
                        null);
            }
        }
    }

    /**
     * Read a file onto the plan's rig.
     *
     * @param file The file.
     * @param reference The rig's reference file.
     * @param registry The loaded definitions.
     * @param plan The plan.
     * @return The character, or null if it can't be read or isn't on the plan's rig.
     */
    private static ImportedCharacter read(
            Path file, Path reference, CharacterRegistry registry, BodyPlan plan) {
        try {
            ImportedCharacter character = CharacterFiles.readAny(file, reference, registry);
            if (!character.rig().id().equals(plan.rig())) {
                log.warn("{} isn't on {} or a rig mapped to it", file, plan.rig());
                return null;
            }
            return character;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Couldn't read {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** Merges the chosen meshes of several characters on the same rig into one. */
    private static final class Merger {
        /** How far layers push out over each body slot; see {@link PartForms#amounts}. */
        private final Map<String, Float> amounts;

        /** The rig and rest pose, from the first character. */
        private ImportedCharacter first;

        /** The merged parts. */
        private final List<ImportedCharacter.Part> parts = new ArrayList<>();

        /** Which part definition each merged part comes from. */
        private final List<Id> partIds = new ArrayList<>();

        /** The clothing form each part's mesh is for, or null for a plain mesh. */
        private final List<String> forms = new ArrayList<>();

        /** Each merged part's tint mask channels' colors while their channels are uncolored. */
        private final List<int[]> fallbacks = new ArrayList<>();

        /** Each merged part's tint mask channels' palette channels. */
        private final List<int[]> channels = new ArrayList<>();

        /** The merged materials. */
        private final List<ImportedCharacter.MaterialData> materials = new ArrayList<>();

        /** The merged images. */
        private final List<ImportedCharacter.ImageData> images = new ArrayList<>();

        /** The clips by name, the first character's of each name. */
        private final Map<String, ImportedCharacter.ClipData> clips = new LinkedHashMap<>();

        /** Where each character's materials start among the merged ones. */
        private final Map<ImportedCharacter, Integer> materialStarts = new HashMap<>();

        /**
         * A merger.
         *
         * @param amounts How far layers push out over each body slot.
         */
        Merger(Map<String, Float> amounts) {
            this.amounts = amounts;
        }

        /**
         * Add a part's meshes from a character.
         *
         * @param source The character read from the part's file.
         * @param part The part definition.
         * @param meshName The glTF mesh's name, or null for every mesh in the file.
         * @param channelNames The palette channels tints are numbered by.
         * @param placement Where an accessory's meshes move on their socket.
         * @param reference The rig's reference body, whose sockets placements turn about; null when
         *     there is no placement.
         * @param form The clothing form the mesh is for, or null for the part's plain mesh.
         */
        void add(
                ImportedCharacter source,
                Part part,
                String meshName,
                List<String> channelNames,
                Part.Placement placement,
                Skeleton reference,
                String form) {
            if (first == null) {
                first = source;
            }
            int materialStart = materialStarts.computeIfAbsent(source, this::addMaterials);
            boolean found = false;
            for (ImportedCharacter.Part piece : source.parts()) {
                // A mesh with several materials comes in as name.0, name.1 and so on; caps come
                // only by name, where the neighbor across their seam is open
                boolean matches =
                        meshName == null
                                ? !piece.name().contains(CAP_INFIX)
                                : piece.name().equals(meshName)
                                        || piece.name().startsWith(meshName + ".");
                if (!matches) {
                    continue;
                }
                found = true;
                if (placement != Part.Placement.NONE) {
                    piece = placed(piece, reference, placement);
                }
                if (part.kind() == Part.PartKind.LAYER && !amounts.isEmpty()) {
                    piece = PartForms.displace(piece, amounts);
                }
                int material = piece.material() < 0 ? -1 : piece.material() + materialStart;
                parts.add(
                        new ImportedCharacter.Part(
                                piece.name(),
                                piece.vertices(),
                                piece.joints(),
                                piece.weights(),
                                piece.indices(),
                                material,
                                piece.min(),
                                piece.max(),
                                piece.formTargets()));
                partIds.add(part.id());
                forms.add(form);
                String materialName =
                        piece.material() < 0
                                ? null
                                : source.materials().get(piece.material()).name();
                channels.add(maskChannels(part, materialName, channelNames));
                fallbacks.add(maskFallbacks(part, materialName));
            }
            if (!found) {
                log.warn("{} names the mesh {}, which isn't in its file", part.id(), meshName);
            }
            source.clips().forEach(clip -> clips.putIfAbsent(clip.name(), clip));
        }

        /**
         * Add a character's materials and images to the merged ones.
         *
         * @param source The character.
         * @return Where its materials start among the merged ones.
         */
        private int addMaterials(ImportedCharacter source) {
            int materialStart = materials.size();
            int imageStart = images.size();
            images.addAll(source.images());
            for (ImportedCharacter.MaterialData data : source.materials()) {
                materials.add(data.shifted(imageStart));
            }
            return materialStart;
        }

        /**
         * Which palette channel each tint mask channel of a mesh takes, from the part's material
         * slot of the mesh's material.
         *
         * @param part The part.
         * @param materialName The mesh's material's name, or null.
         * @param channelNames The palette channels tints are numbered by.
         * @return The channels, r, g, b and a.
         */
        private static int[] maskChannels(
                Part part, String materialName, List<String> channelNames) {
            int[] mapped = new int[MASK_CHANNELS.size()];
            Arrays.fill(mapped, NO_CHANNEL);
            for (Part.MaterialSlot slot : part.materialSlots()) {
                if (materialName != null && !slot.name().equals(materialName)) {
                    continue;
                }
                for (int c = 0; c < MASK_CHANNELS.size(); ++c) {
                    String channel = slot.channels().get(MASK_CHANNELS.get(c));
                    if (channel != null) {
                        mapped[c] = channelNames.indexOf(channel);
                    }
                }
                break;
            }
            return mapped;
        }

        /**
         * The color each of a part's tint mask channels shows while its palette channel is
         * uncolored: its material slot's default.
         *
         * @param part The part definition.
         * @param materialName The mesh's material's name, or null for the part's first slot.
         * @return The packed colors, white where the slot has no default.
         */
        private static int[] maskFallbacks(Part part, String materialName) {
            int[] colors = new int[MASK_CHANNELS.size()];
            Arrays.fill(colors, SkinnedPart.WHITE);
            for (Part.MaterialSlot slot : part.materialSlots()) {
                if (materialName != null && !slot.name().equals(materialName)) {
                    continue;
                }
                for (int c = 0; c < MASK_CHANNELS.size(); ++c) {
                    Color color = slot.defaults().get(MASK_CHANNELS.get(c));
                    if (color != null) {
                        colors[c] =
                                SkinnedPart.pack(
                                        color.red() / COLOR_MAX,
                                        color.green() / COLOR_MAX,
                                        color.blue() / COLOR_MAX);
                    }
                }
                break;
            }
            return colors;
        }

        /**
         * The merged character.
         *
         * @return It, or null if nothing was added.
         */
        Assembled result() {
            if (first == null) {
                return null;
            }
            ImportedCharacter merged =
                    new ImportedCharacter(
                            first.name(),
                            first.rig(),
                            first.rest(),
                            parts,
                            new ArrayList<>(clips.values()),
                            materials,
                            images);
            return new Assembled(merged, partIds, channels, fallbacks, forms);
        }
    }

    /**
     * Where a bone is at rest, in model space.
     *
     * @param skeleton The skeleton.
     * @param bone The bone's index.
     * @param out Where to put it.
     * @return out.
     */
    public static Vector3f restPosition(Skeleton skeleton, int bone, Vector3f out) {
        return skeleton.inverseBind(bone, new Matrix4f()).invert().getTranslation(out);
    }

    /**
     * Move an accessory's mesh by its placement, about its socket on the rig's reference body. A
     * rigid accessory is weighted to its socket bone alone, so its first vertex's bone is the
     * socket.
     *
     * @param piece The mesh.
     * @param reference The rig's reference body.
     * @param placement The placement.
     * @return The moved mesh.
     */
    static ImportedCharacter.Part placed(
            ImportedCharacter.Part piece, Skeleton reference, Part.Placement placement) {
        if (piece.joints().length == 0) {
            return piece;
        }
        Vector3f socket = restPosition(reference, piece.joints()[0], new Vector3f());
        Matrix4f move = placement.matrix(socket, new Matrix4f());
        // A scale that differs by axis leans normals the other way from the surface
        Matrix3f normals = move.normal(new Matrix3f());
        float[] vertices = piece.vertices().clone();
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f v = new Vector3f();
        for (int base = 0; base < vertices.length; base += VERTEX_FLOATS) {
            move.transformPosition(v.set(vertices[base], vertices[base + 1], vertices[base + 2]));
            put(v, vertices, base);
            min.min(v);
            max.max(v);
            v.set(vertices[base + 3], vertices[base + 4], vertices[base + 5]);
            put(normals.transform(v).normalize(), vertices, base + 3);
            // Tangent and bitangent lie along the surface, so they stretch with it
            for (int direction = base + 6; direction < base + 12; direction += 3) {
                v.set(vertices[direction], vertices[direction + 1], vertices[direction + 2]);
                put(move.transformDirection(v).normalize(), vertices, direction);
            }
        }
        return new ImportedCharacter.Part(
                piece.name(),
                vertices,
                piece.joints(),
                piece.weights(),
                piece.indices(),
                piece.material(),
                min,
                max);
    }

    /**
     * Write a vector into an array.
     *
     * @param v The vector.
     * @param out The array.
     * @param at Where its x goes.
     */
    private static void put(Vector3f v, float[] out, int at) {
        out[at] = v.x;
        out[at + 1] = v.y;
        out[at + 2] = v.z;
    }

    /** Static methods only. */
    private PartAssembler() {}
}
