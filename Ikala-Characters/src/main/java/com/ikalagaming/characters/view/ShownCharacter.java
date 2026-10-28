package com.ikalagaming.characters.view;

import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
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

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * An imported character in the world: its textures, materials and meshes loaded into graphics, an
 * instance placed, and a timer posing it from a clip and proportions. Every part's mesh is loaded,
 * so changing which parts show is cheap. Setters can be called from any thread; the next update
 * picks them up.
 *
 * @author Ches Burks
 */
@Slf4j
public final class ShownCharacter implements AutoCloseable {
    /** Updates per second. */
    private static final int RATE = 60;

    /** How much room to leave around the bones for the skin, in meters. */
    private static final float BOUNDS_MARGIN = 0.4f;

    /** The plugin whose graphics context owns what is shown. */
    private final String plugin;

    /**
     * The character shown.
     *
     * @return The character.
     */
    @Getter private final ImportedCharacter character;

    /**
     * The skeleton it is posed with.
     *
     * @return The skeleton.
     */
    @Getter private final Skeleton skeleton;

    /** Runs the updates. */
    private final ScheduledExecutorService timer;

    /** The placed instance. */
    private final SkinnedInstanceHandle instance;

    /** Each part's mesh, by the part's index. */
    private final List<SkinnedMeshHandle> meshes = new ArrayList<>();

    /** Each part's material, by the part's index, or null. */
    private final List<Material> partMaterials = new ArrayList<>();

    /** Each part's tint mask, by the part's index, or null. */
    private final List<TextureHandle> partMasks = new ArrayList<>();

    /** Which tint slot each part's mask channels take, packed, by the part's index. */
    private final List<Integer> partSlots = new ArrayList<>();

    /** The loaded textures. */
    private final List<TextureHandle> textures = new ArrayList<>();

    /** The materials added to the scene's cache. */
    private final List<Material> materials = new ArrayList<>();

    /** The clip playing, or null to stand at rest. */
    private volatile Clip clip;

    /** The proportions it is posed with. */
    private volatile Proportions proportions;

    /** Whether the clip's time moves on. */
    private volatile boolean playing = true;

    /** Set to start the clip from the beginning on the next update. */
    private volatile boolean restart;

    /** Whether this was closed. */
    private boolean closed;

    /** Where its root stands. */
    private final Vector3d position;

    /** Which way it faces. */
    private final Quaternionf facing;

    /** Its effects, moved with it each pose, or null for none. */
    private volatile CharacterFx fx;

    /**
     * Load a character and place it, standing at rest until a clip is played.
     *
     * @param plugin The plugin whose graphics context owns it.
     * @param character The character.
     * @param position Where its root stands.
     * @param facing Which way it faces; characters face +Z.
     * @param shown The indices of the parts to show.
     */
    public ShownCharacter(
            @NonNull String plugin,
            @NonNull ImportedCharacter character,
            @NonNull Vector3d position,
            @NonNull Quaternionf facing,
            @NonNull Collection<Integer> shown) {
        this(plugin, character, null, null, position, facing, shown);
    }

    /**
     * Load a character with mask regions on its parts, which {@link #setHiddenRegions} hides, and
     * place it, standing at rest until a clip is played.
     *
     * @param plugin The plugin whose graphics context owns it.
     * @param character The character.
     * @param regions Each part's mask region per vertex, or null for a part never hidden; null for
     *     no regions at all.
     * @param channels For each part, the tint slot each tint mask channel (r, g, b, a) takes, or -1
     *     for none; null for no tinting. See {@link #tintSlots}.
     * @param position Where its root stands.
     * @param facing Which way it faces; characters face +Z.
     * @param shown The indices of the parts to show.
     */
    public ShownCharacter(
            @NonNull String plugin,
            @NonNull ImportedCharacter character,
            List<int[]> regions,
            List<int[]> channels,
            @NonNull Vector3d position,
            @NonNull Quaternionf facing,
            @NonNull Collection<Integer> shown) {
        this.plugin = plugin;
        this.character = character;
        this.position = new Vector3d(position);
        this.facing = new Quaternionf(facing);
        skeleton = character.skeleton();
        proportions = Proportions.neutral(skeleton);
        var graphics = GraphicsManager.forPlugin(plugin);
        Skins skins = graphics.skins();

        // Tint masks hold weights, not colors, so they load linear
        Set<Integer> maskImages = new HashSet<>();
        for (ImportedCharacter.MaterialData data : character.materials()) {
            if (data.tintMask() >= 0) {
                maskImages.add(data.tintMask());
            }
        }
        List<TextureHandle> imageTextures = new ArrayList<>();
        for (int i = 0; i < character.images().size(); ++i) {
            ImportedCharacter.ImageData image = character.images().get(i);
            TextureHandle texture =
                    decode(
                            graphics.textures(),
                            image,
                            maskImages.contains(i) ? Format.R8G8B8A8_UNORM : Format.R8G8B8A8_SRGB);
            imageTextures.add(texture);
            if (texture != null) {
                textures.add(texture);
            }
        }
        // Parts share their file's materials; colors come from the instance's tints
        List<Material> byIndex = new ArrayList<>();
        for (ImportedCharacter.MaterialData data : character.materials()) {
            Material material = material(data, imageTextures);
            GraphicsManager.getScene().getMaterialCache().addMaterial(material);
            materials.add(material);
            byIndex.add(material);
        }
        for (int p = 0; p < character.parts().size(); ++p) {
            ImportedCharacter.Part part = character.parts().get(p);
            meshes.add(
                    skins.registerMesh(
                            part.vertices(),
                            part.joints(),
                            part.weights(),
                            regions == null ? null : regions.get(p),
                            part.indices(),
                            part.min(),
                            part.max()));
            boolean known = part.material() >= 0 && part.material() < byIndex.size();
            Material material = known ? byIndex.get(part.material()) : null;
            int maskImage = known ? character.materials().get(part.material()).tintMask() : -1;
            TextureHandle mask =
                    maskImage >= 0 && maskImage < imageTextures.size()
                            ? imageTextures.get(maskImage)
                            : null;
            partMaterials.add(material);
            partMasks.add(mask);
            boolean textured = material != null && material.getTexture() != null;
            int[] mapped = channels == null ? null : channels.get(p);
            partSlots.add(tintSlots(mapped, mask != null, textured));
        }
        instance = skins.place(skeleton.size(), position, facing, 1);
        showParts(shown);

        timer =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "Shown character");
                            thread.setDaemon(true);
                            return thread;
                        });
        timer.scheduleAtFixedRate(new Updater(skins), 0, 1000 / RATE, TimeUnit.MILLISECONDS);
    }

    /**
     * Show only some parts.
     *
     * @param shown The indices of the parts to show.
     */
    public void showParts(@NonNull Collection<Integer> shown) {
        List<SkinnedPart> parts = new ArrayList<>();
        for (int index : shown) {
            parts.add(
                    new SkinnedPart(
                            meshes.get(index),
                            partMaterials.get(index),
                            partMasks.get(index),
                            partSlots.get(index)));
        }
        GraphicsManager.forPlugin(plugin).skins().setParts(instance, parts);
    }

    /**
     * Hide mask regions of the parts, like the body under armor.
     *
     * @param hidden One bit per region, set to hide it.
     */
    public void setHiddenRegions(long hidden) {
        GraphicsManager.forPlugin(plugin).skins().setHiddenRegions(instance, hidden);
    }

    /**
     * Which tint slot each of a part's mask channels takes. A part with a mask takes them all as
     * mapped. A part without one takes its red channel's color all over if it is untextured; a
     * textured part without a mask isn't tinted, since one color over a painted texture only
     * muddies it. Slots past the instance's tint colors are left out.
     *
     * @param channels The tint slot each mask channel (r, g, b, a) takes, or -1; null for none.
     * @param masked Whether it has a tint mask.
     * @param textured Whether its material has a texture.
     * @return The packed slots, see {@link SkinnedPart#slots}.
     */
    static int tintSlots(int[] channels, boolean masked, boolean textured) {
        if (channels == null || channels[0] < 0 && !masked) {
            return SkinnedPart.NO_TINT;
        }
        if (masked) {
            return SkinnedPart.slots(
                    slot(channels[0]), slot(channels[1]), slot(channels[2]), slot(channels[3]));
        }
        return textured ? SkinnedPart.NO_TINT : SkinnedPart.slots(slot(channels[0]), -1, -1, -1);
    }

    /**
     * A channel's tint slot, if the instance has one for it.
     *
     * @param channel The channel's position, or -1.
     * @return The slot, or -1.
     */
    private static int slot(int channel) {
        return channel < Skins.TINT_SLOTS ? channel : -1;
    }

    /**
     * Whether a part takes tint colors at all, see {@link #tintSlots}.
     *
     * @param part The part's index.
     * @return True if some channel of it is tinted.
     */
    public boolean isTinted(int part) {
        return partSlots.get(part) != SkinnedPart.NO_TINT;
    }

    /**
     * Change the tint colors, one per palette channel in the order the parts' channels number them,
     * up to {@value Skins#TINT_SLOTS}.
     *
     * @param colors sRGB colors, three floats from 0 to 1 per channel; white changes nothing.
     */
    public void setTints(float @NonNull [] colors) {
        GraphicsManager.forPlugin(plugin).skins().setTints(instance, colors);
    }

    /**
     * Make a material from the file's.
     *
     * @param data The file's material.
     * @param imageTextures The loaded images, by index, null where one couldn't be decoded.
     * @return The material.
     */
    private static Material material(
            ImportedCharacter.MaterialData data, List<TextureHandle> imageTextures) {
        Material material = new Material();
        float[] color = data.baseColor();
        // Materials hold sRGB colors, glTF's factors are linear
        material.getBaseColor().set(encode(color[0]), encode(color[1]), encode(color[2]), color[3]);
        material.setMetallic(data.metallic());
        material.setRoughness(data.roughness());
        if (data.image() >= 0 && data.image() < imageTextures.size()) {
            material.setTexture(imageTextures.get(data.image()));
        }
        return material;
    }

    /**
     * Play a clip from its start.
     *
     * @param clipData The clip, or null to stand at rest.
     */
    public void play(ImportedCharacter.ClipData clipData) {
        clip = clipData == null ? null : clipData.bind(skeleton);
        restart = true;
    }

    /**
     * Stop or start the clip's time, keeping where it is.
     *
     * @param play Whether time moves on.
     */
    public void setPlaying(boolean play) {
        playing = play;
    }

    /**
     * Change the proportions it is posed with.
     *
     * @param newProportions The proportions, for this character's skeleton.
     */
    public void setProportions(@NonNull Proportions newProportions) {
        proportions = newProportions;
    }

    /**
     * Give it effects, which follow its bones from the next pose on. Its earlier effects are
     * stopped.
     *
     * @param newFx The effects, or null for none.
     */
    public void setFx(CharacterFx newFx) {
        CharacterFx old = fx;
        fx = newFx;
        if (old != null && old != newFx) {
            old.close();
        }
    }

    /** Poses the character on the timer's thread, which alone touches the pose and solver. */
    private final class Updater implements Runnable {
        /** The skins API. */
        private final Skins skins;

        /** The pose. */
        private final Pose pose = new Pose(skeleton);

        /** Solves the pose. */
        private final PoseSolver solver = new PoseSolver(skeleton);

        /** The skinning matrices. */
        private final float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];

        /** The bounds' minimum corner. */
        private final Vector3f min = new Vector3f();

        /** The bounds' maximum corner. */
        private final Vector3f max = new Vector3f();

        /** Scratch for a bone's position. */
        private final Vector3f bone = new Vector3f();

        /** How far into the clip, in seconds. */
        private float time;

        /** When the last update ran, in nanoseconds. */
        private long last = System.nanoTime();

        /**
         * Set up the updates.
         *
         * @param skins The skins API.
         */
        Updater(Skins skins) {
            this.skins = skins;
        }

        @Override
        public void run() {
            try {
                long now = System.nanoTime();
                if (restart) {
                    restart = false;
                    time = 0;
                } else if (playing) {
                    time += (now - last) / 1e9f;
                }
                last = now;
                final Clip playingClip = clip;
                final Proportions current = proportions;
                pose.reset();
                if (playingClip != null) {
                    playingClip.sample(time, pose);
                }
                solver.solve(pose, current);
                solver.writePalette(current, palette, 0);
                min.set(Float.POSITIVE_INFINITY);
                max.set(Float.NEGATIVE_INFINITY);
                for (int b = 0; b < skeleton.size(); ++b) {
                    solver.model(b).getTranslation(bone);
                    min.min(bone);
                    max.max(bone);
                }
                min.sub(BOUNDS_MARGIN, BOUNDS_MARGIN, BOUNDS_MARGIN);
                max.add(BOUNDS_MARGIN, BOUNDS_MARGIN, BOUNDS_MARGIN);
                skins.setPose(instance, palette, 0, min, max);
                final CharacterFx effects = fx;
                if (effects != null) {
                    effects.update(solver, position, facing);
                }
            } catch (RuntimeException e) {
                log.warn("The shown character {} stopped updating", character.name(), e);
                throw e;
            }
        }
    }

    /**
     * Decode an image and load it as a texture.
     *
     * @param textureApi The textures API.
     * @param image The image.
     * @param format The format to load it as: sRGB for colors, unsigned normalized for masks.
     * @return The texture, or null if it can't be decoded.
     */
    private static TextureHandle decode(
            Textures textureApi, ImportedCharacter.ImageData image, Format format) {
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
        return textureApi.load(pixels, format, width, height, TextureWrap.REPEAT);
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

    /** Stop posing it and remove it from the world, releasing what it loaded. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        timer.shutdownNow();
        try {
            // So an update in flight doesn't pose the instance after it is removed
            timer.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        setFx(null);
        var graphics = GraphicsManager.forPlugin(plugin);
        if (graphics.isClosed()) {
            return;
        }
        Skins skins = graphics.skins();
        skins.remove(instance);
        meshes.forEach(skins::releaseMesh);
        textures.forEach(graphics.textures()::release);
        materials.forEach(GraphicsManager.getScene().getMaterialCache()::removeMaterial);
    }
}
