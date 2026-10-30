package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.design.LookStack;
import com.ikalagaming.characters.gltf.GltfImporter;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reads held items' meshes and places them on a character's socket bones, for {@link
 * ShownCharacter#setHeld}. Each file is read once. A held mesh is stored with its grip at the
 * origin, skinned to one bone; every part of the file whose name is the mesh's, or the mesh's and a
 * dot and number for its other materials, is held. Safe from any thread.
 *
 * @author Ches Burks
 */
@Slf4j
public final class HeldItems {
    /** Read files, or null for one that couldn't be read. */
    private final Map<Path, ImportedCharacter> files = new HashMap<>();

    /** Meshes already warned about. */
    private final Set<String> warned = new HashSet<>();

    /** Guards the caches. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The meshes for a character's held items.
     *
     * @param held The held items, from the look stack.
     * @param parts The parts the character wears, which may move sockets.
     * @param plan The character's plan.
     * @param skeleton The character's skeleton.
     * @param registry The loaded definitions, which find files.
     * @return The meshes, leaving out items whose files or meshes can't be found.
     */
    public List<ShownCharacter.HeldMesh> meshes(
            @NonNull List<LookStack.Held> held,
            @NonNull Collection<Part> parts,
            @NonNull BodyPlan plan,
            @NonNull Skeleton skeleton,
            @NonNull CharacterRegistry registry) {
        List<ShownCharacter.HeldMesh> meshes = new ArrayList<>();
        for (LookStack.Held item : held) {
            String mesh = item.look().mesh();
            int split = mesh.lastIndexOf(AnimationSet.CLIP_SEPARATOR);
            Path file = registry.asset(item.item().namespace(), mesh.substring(0, split));
            ImportedCharacter source = file == null ? null : read(file);
            if (source == null) {
                warn(mesh, "its file can't be read");
                continue;
            }
            Vector3f moved = new Vector3f();
            String boneName =
                    CharacterFx.socketBone(item.socket().id(), parts, plan, skeleton, moved);
            int bone = boneName == null ? -1 : skeleton.index(boneName);
            if (bone < 0) {
                warn(mesh, "the character has no bone for the socket " + item.socket().id());
                continue;
            }
            Matrix4f attach = attach(moved, item.attach());
            String name = mesh.substring(split + 1);
            boolean found = false;
            for (int p = 0; p < source.parts().size(); ++p) {
                String partName = source.parts().get(p).name();
                if (partName.equals(name) || partName.startsWith(name + ".")) {
                    meshes.add(new ShownCharacter.HeldMesh(source, p, bone, attach));
                    found = true;
                }
            }
            if (!found) {
                warn(mesh, "the file has no mesh by that name");
            }
        }
        return meshes;
    }

    /**
     * Where a held mesh sits in its bone's space: moved as a part moves its socket, then as the
     * item attaches.
     *
     * @param moved How far a worn part moves the socket.
     * @param attach How the item attaches, or null.
     * @return The matrix.
     */
    static Matrix4f attach(Vector3f moved, Item.Attach attach) {
        Matrix4f matrix = new Matrix4f().translation(moved);
        if (attach == null) {
            return matrix;
        }
        Vec3 offset = attach.offset();
        if (offset != null) {
            matrix.translate(offset.x(), offset.y(), offset.z());
        }
        Vec3 rotate = attach.rotate();
        if (rotate != null) {
            matrix.rotateXYZ(
                    (float) Math.toRadians(rotate.x()),
                    (float) Math.toRadians(rotate.y()),
                    (float) Math.toRadians(rotate.z()));
        }
        return matrix;
    }

    /**
     * A file, read the first time.
     *
     * @param file The file.
     * @return It, or null if it couldn't be read.
     */
    private ImportedCharacter read(Path file) {
        lock.lock();
        try {
            if (files.containsKey(file)) {
                return files.get(file);
            }
            ImportedCharacter source = null;
            try {
                source = GltfImporter.read(file);
            } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
                log.warn("Couldn't read held items from {}: {}", file, e.getMessage());
            }
            files.put(file, source);
            return source;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Warn about a mesh once.
     *
     * @param mesh The mesh.
     * @param why Why it is left out.
     */
    private void warn(String mesh, String why) {
        lock.lock();
        try {
            if (warned.add(mesh + why)) {
                log.warn("Leaving out the held mesh {}: {}", mesh, why);
            }
        } finally {
            lock.unlock();
        }
    }
}
