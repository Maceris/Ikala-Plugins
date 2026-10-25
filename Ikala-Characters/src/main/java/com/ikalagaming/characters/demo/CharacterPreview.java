package com.ikalagaming.characters.demo;

import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.CharacterFiles;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.Camera;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Shows a character read from a glTF file where the camera looks at the ground, or else in front of
 * it, facing the camera and playing one of its clips, to check imports in the editor. Its textures
 * are decoded from the file and its materials made from glTF's, so nothing but the file is needed.
 *
 * <p>A modular file, with parts named {@code *_default} like the RPG characters, shows the parts
 * {@link PartSlots} starts with; otherwise every part shows.
 *
 * @author Ches Burks
 */
@Slf4j
public final class CharacterPreview {
    /** How far in front of the camera the character stands if it can't see the ground. */
    private static final double DISTANCE = 3.5;

    /** The farthest the ground can be for the character to stand where the camera looks. */
    private static final double MAX_GROUND_DISTANCE = 30;

    /** Guards showing and hiding. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The plugin whose graphics context owns what is shown. */
    private final String plugin;

    /** The loaded rigs, to give an imported skeleton its definition's bone kinds. */
    private final Supplier<CharacterRegistry> registry;

    /** What is shown, or null. */
    private ShownCharacter shown;

    /**
     * Set up a preview showing nothing.
     *
     * @param plugin The plugin whose graphics context owns what it shows.
     * @param registry The loaded definitions, read each time something is shown.
     */
    public CharacterPreview(@NonNull String plugin, @NonNull Supplier<CharacterRegistry> registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /**
     * Show a character, replacing whatever was shown.
     *
     * @param file The glTF file.
     * @param clipName The clip to play, or null for idle, or the first.
     * @throws IOException If the file can't be read.
     * @throws IllegalArgumentException If it holds no usable character or no such clip.
     */
    public void show(@NonNull Path file, String clipName) throws IOException {
        show(CharacterFiles.read(file, registry.get()), clipName);
    }

    /**
     * Show a character moved onto a rig through the loaded rig map that covers its bones, replacing
     * whatever was shown.
     *
     * @param file The character's glTF file.
     * @param referenceFile A glTF file of the rig at its reference rest, like the mannequin, for
     *     the bones and twists the character doesn't give.
     * @param clipName The clip to play, or null for idle, or the first.
     * @throws IOException If a file can't be read.
     * @throws IllegalArgumentException If no rig map covers the character, or the reference isn't
     *     on the map's rig.
     */
    public void showRetargeted(@NonNull Path file, @NonNull Path referenceFile, String clipName)
            throws IOException {
        show(CharacterFiles.readRetargeted(file, referenceFile, registry.get()), clipName);
    }

    /**
     * Show an imported character, replacing whatever was shown.
     *
     * @param character The character.
     * @param clipName The clip to play, or null for idle, or the first.
     * @throws IllegalArgumentException If there is no such clip.
     */
    private void show(ImportedCharacter character, String clipName) {
        ImportedCharacter.ClipData clipData = pickClip(character, clipName);
        lock.lock();
        try {
            hide();
            Camera camera = GraphicsManager.getScene().getCamera();
            Vector3f view = camera.getInvViewMatrix().transformDirection(new Vector3f(0, 0, -1));
            Vector3d position = groundPoint(camera.getPosition(), view);
            Vector3f forward = new Vector3f(view.x, 0, view.z);
            if (forward.lengthSquared() < 1e-6f) {
                forward.set(0, 0, -1);
            }
            forward.normalize();
            if (position == null) {
                position =
                        new Vector3d(camera.getPosition())
                                .add(forward.x * DISTANCE, -1.6, forward.z * DISTANCE);
            }
            // Face the camera; characters face +Z
            Quaternionf facing =
                    new Quaternionf().rotateY((float) Math.atan2(-forward.x, -forward.z));
            List<Integer> parts = PartSlots.of(character).initialParts();
            shown = new ShownCharacter(plugin, character, position, facing, parts);
            shown.play(clipData);
            log.info(
                    "Showing {} on {}: {} bones, {} of {} parts, playing {}",
                    character.name(),
                    character.rig().id(),
                    character.rig().bones().size(),
                    parts.size(),
                    character.parts().size(),
                    clipData == null ? "nothing" : clipData.name());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pick the clip to play.
     *
     * @param character The character.
     * @param clipName The clip asked for, or null.
     * @return The clip, or null if the character has none.
     * @throws IllegalArgumentException If the clip asked for isn't there.
     */
    public static ImportedCharacter.ClipData pickClip(
            @NonNull ImportedCharacter character, String clipName) {
        if (clipName != null) {
            ImportedCharacter.ClipData clip = character.clip(clipName);
            if (clip == null) {
                throw new IllegalArgumentException(
                        "No clip "
                                + clipName
                                + "; the clips are "
                                + character.clips().stream()
                                        .map(ImportedCharacter.ClipData::name)
                                        .toList());
            }
            return clip;
        }
        ImportedCharacter.ClipData idle = character.clip("idle");
        if (idle != null) {
            return idle;
        }
        return character.clips().isEmpty() ? null : character.clips().get(0);
    }

    /**
     * Where the camera's view meets the ground, the plane at height 0, if it does within reach.
     *
     * @param camera The camera's position.
     * @param view The way it looks.
     * @return The point, or null if the view doesn't meet the ground in reach.
     */
    private static Vector3d groundPoint(org.joml.Vector3dc camera, Vector3f view) {
        if (view.y > -0.05f || camera.y() <= 0) {
            return null;
        }
        double distance = -camera.y() / view.y;
        if (distance > MAX_GROUND_DISTANCE) {
            return null;
        }
        return new Vector3d(camera).add(view.x * distance, -camera.y(), view.z * distance);
    }

    /** Stop and remove whatever is shown. */
    public void hide() {
        lock.lock();
        try {
            if (shown != null) {
                shown.close();
                shown = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
