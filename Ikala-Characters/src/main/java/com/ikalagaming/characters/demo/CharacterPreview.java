package com.ikalagaming.characters.demo;

import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.GltfImporter;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.SkinnedInstanceHandle;
import com.ikalagaming.graphics.SkinnedMeshHandle;
import com.ikalagaming.graphics.SkinnedPart;
import com.ikalagaming.graphics.Skins;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.scene.Camera;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Shows a character read from a glTF file where the camera looks at the ground, or else in front of
 * it, facing the camera and playing one of its clips, to check imports in the editor. Its textures
 * are decoded from the file and its materials made from glTF's, so nothing but the file is needed.
 *
 * <p>A file with parts named {@code *_default}, like the modular RPG characters, shows those and
 * the first hair, since the rest are alternatives worn over them; otherwise every part shows.
 *
 * @author Ches Burks
 */
@Slf4j
public final class CharacterPreview {
    /** How far in front of the camera the character stands if it can't see the ground. */
    private static final double DISTANCE = 3.5;

    /** The farthest the ground can be for the character to stand where the camera looks. */
    private static final double MAX_GROUND_DISTANCE = 30;

    /** Updates per second. */
    private static final int RATE = 60;

    /** How much room to leave around the bones for the skin, in meters. */
    private static final float BOUNDS_MARGIN = 0.4f;

    /** What default parts' names contain. */
    private static final String DEFAULT_PART = "_default";

    /** Guards starting and stopping. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The plugin whose graphics context owns what is shown. */
    private final String plugin;

    /** The loaded rigs, to give an imported skeleton its definition's bone kinds. */
    private final Supplier<CharacterRegistry> registry;

    /** Runs the updates, or null while nothing is shown. */
    private ScheduledExecutorService timer;

    /** The shown instance. */
    private SkinnedInstanceHandle instance;

    /** The registered meshes. */
    private final List<SkinnedMeshHandle> meshes = new ArrayList<>();

    /** The loaded textures. */
    private final List<TextureHandle> textures = new ArrayList<>();

    /** The materials added to the scene's cache. */
    private final List<Material> materials = new ArrayList<>();

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
        ImportedCharacter character = GltfImporter.read(file);
        Rig definition = matchingRig(character.rig());
        if (definition != null) {
            character = character.onRig(definition);
        }
        ImportedCharacter.ClipData clipData = pickClip(character, clipName);
        lock.lock();
        try {
            hide();
            start(character, clipData);
            log.info(
                    "Showing {}: {} bones, {} of {} parts, playing {}",
                    character.name(),
                    character.rig().bones().size(),
                    meshes.size(),
                    character.parts().size(),
                    clipData == null ? "nothing" : clipData.name());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Find a loaded rig with exactly the imported skeleton's bones.
     *
     * @param imported The imported rig.
     * @return The definition, or null if none matches.
     */
    private Rig matchingRig(Rig imported) {
        Set<String> names = new HashSet<>();
        imported.bones().forEach(bone -> names.add(bone.name()));
        for (Rig rig : registry.get().all(Rig.class)) {
            Set<String> theirs = new HashSet<>();
            rig.bones().forEach(bone -> theirs.add(bone.name()));
            if (theirs.equals(names)) {
                return rig;
            }
        }
        return null;
    }

    /**
     * Pick the clip to play.
     *
     * @param character The character.
     * @param clipName The clip asked for, or null.
     * @return The clip, or null if the character has none.
     * @throws IllegalArgumentException If the clip asked for isn't there.
     */
    private static ImportedCharacter.ClipData pickClip(
            ImportedCharacter character, String clipName) {
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
     * Whether a part shows by default.
     *
     * @param character The character.
     * @param part The part.
     * @return True if it shows.
     */
    private static boolean shows(ImportedCharacter character, ImportedCharacter.Part part) {
        boolean modular = character.parts().stream().anyMatch(p -> p.name().contains(DEFAULT_PART));
        if (!modular) {
            return true;
        }
        String name = part.name().toLowerCase(Locale.ROOT);
        return name.contains(DEFAULT_PART) || name.endsWith("hair_1");
    }

    /**
     * Load the character into graphics and start posing it. The lock must be held.
     *
     * @param character The character.
     * @param clipData The clip to play, or null.
     */
    private void start(ImportedCharacter character, ImportedCharacter.ClipData clipData) {
        var graphics = GraphicsManager.forPlugin(plugin);
        Skins skins = graphics.skins();
        Textures textureApi = graphics.textures();

        List<TextureHandle> imageTextures = new ArrayList<>();
        for (ImportedCharacter.ImageData image : character.images()) {
            TextureHandle texture = decode(textureApi, image);
            imageTextures.add(texture);
            if (texture != null) {
                textures.add(texture);
            }
        }
        List<Material> partMaterials = new ArrayList<>();
        for (ImportedCharacter.MaterialData data : character.materials()) {
            Material material = new Material();
            float[] color = data.baseColor();
            // Materials hold sRGB colors, glTF's factors are linear
            material.getBaseColor()
                    .set(encode(color[0]), encode(color[1]), encode(color[2]), color[3]);
            material.setMetallic(data.metallic());
            material.setRoughness(data.roughness());
            if (data.image() >= 0 && data.image() < imageTextures.size()) {
                material.setTexture(imageTextures.get(data.image()));
            }
            GraphicsManager.getScene().getMaterialCache().addMaterial(material);
            materials.add(material);
            partMaterials.add(material);
        }

        List<SkinnedPart> parts = new ArrayList<>();
        for (ImportedCharacter.Part part : character.parts()) {
            if (!shows(character, part)) {
                continue;
            }
            SkinnedMeshHandle mesh =
                    skins.registerMesh(
                            part.vertices(),
                            part.joints(),
                            part.weights(),
                            null,
                            part.indices(),
                            part.min(),
                            part.max());
            meshes.add(mesh);
            Material material =
                    part.material() >= 0 && part.material() < partMaterials.size()
                            ? partMaterials.get(part.material())
                            : null;
            parts.add(new SkinnedPart(mesh, material));
        }

        Skeleton skeleton = character.skeleton();
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
        Quaternionf facing = new Quaternionf().rotateY((float) Math.atan2(-forward.x, -forward.z));
        instance = skins.place(skeleton.size(), position, facing, 1);
        skins.setParts(instance, parts);

        Clip clip = clipData == null ? null : clipData.bind(skeleton);
        Pose pose = new Pose(skeleton);
        PoseSolver solver = new PoseSolver(skeleton);
        Proportions proportions = Proportions.neutral(skeleton);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        Vector3f bone = new Vector3f();
        final SkinnedInstanceHandle shown = instance;
        final long started = System.nanoTime();
        timer =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "Character preview");
                            thread.setDaemon(true);
                            return thread;
                        });
        timer.scheduleAtFixedRate(
                () -> {
                    try {
                        pose.reset();
                        if (clip != null) {
                            clip.sample((System.nanoTime() - started) / 1e9f, pose);
                        }
                        solver.solve(pose, proportions);
                        solver.writePalette(proportions, palette, 0);
                        min.set(Float.POSITIVE_INFINITY);
                        max.set(Float.NEGATIVE_INFINITY);
                        for (int b = 0; b < skeleton.size(); ++b) {
                            solver.model(b).getTranslation(bone);
                            min.min(bone);
                            max.max(bone);
                        }
                        min.sub(BOUNDS_MARGIN, BOUNDS_MARGIN, BOUNDS_MARGIN);
                        max.add(BOUNDS_MARGIN, BOUNDS_MARGIN, BOUNDS_MARGIN);
                        skins.setPose(shown, palette, 0, min, max);
                    } catch (RuntimeException e) {
                        log.warn("The character preview stopped updating", e);
                        throw e;
                    }
                },
                0,
                1000 / RATE,
                TimeUnit.MILLISECONDS);
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

    /**
     * Decode an image and load it as a texture.
     *
     * @param textureApi The textures API.
     * @param image The image.
     * @return The texture, or null if it can't be decoded.
     */
    private static TextureHandle decode(Textures textureApi, ImportedCharacter.ImageData image) {
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
        } catch (IOException e) {
            decoded = null;
        }
        if (decoded == null) {
            log.warn("Couldn't decode the image {} ({})", image.name(), image.mimeType());
            return null;
        }
        final int width = decoded.getWidth();
        final int height = decoded.getHeight();
        // Direct, as uploads read it natively; it is copied to staging memory before load returns
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        int[] row = new int[width];
        for (int y = 0; y < height; ++y) {
            decoded.getRGB(0, y, width, 1, row, 0, width);
            for (int argb : row) {
                pixels.put((byte) (argb >> 16));
                pixels.put((byte) (argb >> 8));
                pixels.put((byte) argb);
                pixels.put((byte) (argb >>> 24));
            }
        }
        pixels.flip();
        return textureApi.load(pixels, Format.R8G8B8A8_SRGB, width, height, TextureWrap.REPEAT);
    }

    /**
     * Encode a linear value with the sRGB curve.
     *
     * @param linear The linear value, 0 to 1.
     * @return The encoded value.
     */
    private static float encode(float linear) {
        float value = Math.clamp(linear, 0, 1);
        return value <= 0.003_130_8f
                ? value * 12.92f
                : (float) (1.055 * Math.pow(value, 1 / 2.4) - 0.055);
    }

    /** Stop and remove whatever is shown. */
    public void hide() {
        lock.lock();
        try {
            if (timer != null) {
                timer.shutdownNow();
                timer = null;
            }
            var graphics = GraphicsManager.forPlugin(plugin);
            if (!graphics.isClosed()) {
                Skins skins = graphics.skins();
                if (instance != null) {
                    skins.remove(instance);
                }
                meshes.forEach(skins::releaseMesh);
                textures.forEach(graphics.textures()::release);
                materials.forEach(GraphicsManager.getScene().getMaterialCache()::removeMaterial);
            }
            instance = null;
            meshes.clear();
            textures.clear();
            materials.clear();
        } finally {
            lock.unlock();
        }
    }
}
