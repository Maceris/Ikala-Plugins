package com.ikalagaming.characters.view;

import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.RigMap;
import com.ikalagaming.characters.gltf.GltfImporter;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.retarget.Retargeter;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads characters from glTF files onto the loaded rigs: straight onto a rig with the same bones,
 * or moved onto one through a rig map that covers the file's bones.
 *
 * @author Ches Burks
 */
public final class CharacterFiles {
    /**
     * Read a character, putting it on the loaded rig with exactly its bones if there is one.
     *
     * @param file The glTF file.
     * @param registry The loaded definitions.
     * @return The character.
     * @throws IOException If the file can't be read.
     * @throws IllegalArgumentException If it holds no usable character.
     */
    public static ImportedCharacter read(@NonNull Path file, @NonNull CharacterRegistry registry)
            throws IOException {
        ImportedCharacter character = GltfImporter.read(file);
        Rig definition = matchingRig(character.rig(), registry);
        return definition == null ? character : character.onRig(definition);
    }

    /**
     * Read a character moved onto a rig through the loaded rig map that covers its bones.
     *
     * @param file The character's glTF file.
     * @param referenceFile A glTF file of the rig at its reference rest, like the mannequin, for
     *     the bones and twists the character doesn't give.
     * @param registry The loaded definitions.
     * @return The moved character.
     * @throws IOException If a file can't be read.
     * @throws IllegalArgumentException If no rig map covers the character, or the reference isn't
     *     on the map's rig.
     */
    public static ImportedCharacter readRetargeted(
            @NonNull Path file, @NonNull Path referenceFile, @NonNull CharacterRegistry registry)
            throws IOException {
        ImportedCharacter source = GltfImporter.read(file);
        RigMap map = coveringMap(source.rig(), registry);
        if (map == null) {
            throw new IllegalArgumentException("No loaded rig map covers the skeleton's bones");
        }
        Rig target = registry.get(Rig.class, map.rig());
        ImportedCharacter reference = GltfImporter.read(referenceFile).onRig(target);
        return Retargeter.retarget(source, map, target, reference.rest());
    }

    /**
     * Read a character onto a loaded rig whichever way works: straight on if a rig has its bones,
     * else moved through a rig map, else as it is in the file.
     *
     * @param file The character's glTF file.
     * @param referenceFile A glTF file of the rig at its reference rest, used if it is moved.
     * @param registry The loaded definitions.
     * @return The character.
     * @throws IOException If a file can't be read.
     * @throws IllegalArgumentException If it holds no usable character.
     */
    public static ImportedCharacter readAny(
            @NonNull Path file, @NonNull Path referenceFile, @NonNull CharacterRegistry registry)
            throws IOException {
        ImportedCharacter character = GltfImporter.read(file);
        Rig definition = matchingRig(character.rig(), registry);
        if (definition != null) {
            return character.onRig(definition);
        }
        RigMap map = coveringMap(character.rig(), registry);
        if (map == null) {
            return character;
        }
        Rig target = registry.get(Rig.class, map.rig());
        ImportedCharacter reference = GltfImporter.read(referenceFile).onRig(target);
        return Retargeter.retarget(character, map, target, reference.rest());
    }

    /**
     * Find a loaded rig with exactly the imported skeleton's bones.
     *
     * @param imported The imported rig.
     * @param registry The loaded definitions.
     * @return The definition, or null if none matches.
     */
    private static Rig matchingRig(Rig imported, CharacterRegistry registry) {
        Set<String> names = names(imported.bones());
        for (Rig rig : registry.all(Rig.class)) {
            if (names(rig.bones()).equals(names)) {
                return rig;
            }
        }
        return null;
    }

    /**
     * The names of some bones.
     *
     * @param bones The bones.
     * @return Their names.
     */
    private static Set<String> names(List<Rig.Bone> bones) {
        Set<String> names = new HashSet<>();
        bones.forEach(bone -> names.add(bone.name()));
        return names;
    }

    /**
     * Find a loaded rig map that maps or drops every bone of a skeleton.
     *
     * @param imported The skeleton's rig.
     * @param registry The loaded definitions.
     * @return The map, or null if none does.
     */
    private static RigMap coveringMap(Rig imported, CharacterRegistry registry) {
        for (RigMap map : registry.all(RigMap.class)) {
            boolean covers =
                    imported.bones().stream()
                            .allMatch(
                                    bone ->
                                            map.bones().containsKey(bone.name())
                                                    || map.drop().contains(bone.name()));
            if (covers) {
                return map;
            }
        }
        return null;
    }

    /** Static methods only. */
    private CharacterFiles() {}
}
