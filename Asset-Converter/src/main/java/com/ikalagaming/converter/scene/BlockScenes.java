package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.AssetConvert;
import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.content.ContextScene;
import com.ikalagaming.converter.content.GameContent;
import com.ikalagaming.converter.scene.ImportedScene.ImportedMaterial;
import com.ikalagaming.converter.scene.ImportedScene.Mesh;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ImportedScene.TextureRef;
import com.ikalagaming.converter.scene.ImportedScene.TextureSlot;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds scenes of blocks, as the game draws them, for the 3D viewer: each block's look gives its
 * model, its texture and normal map, and its render material's values. A block whose look or model
 * can't be found is a plain gray cube, and noted. Uses Factory-Core's classes through {@link
 * BlockContent}, so only touch it when {@link GameContent#available()}. Off the render thread.
 */
@Slf4j
public final class BlockScenes {
    /** A missing block's color. */
    private static final Vector4f MISSING_COLOR = new Vector4f(0.5f, 0.5f, 0.5f, 1);

    /** Half a block, in meters. */
    private static final float HALF = 0.5f;

    /** Not made. */
    private BlockScenes() {}

    /**
     * A built scene.
     *
     * @param scene The scene, ready to place.
     * @param values Each material's values from its render material, by the scene's material index,
     *     null where there is none; set them on the placed materials, whose textures come from the
     *     scene.
     * @param missing The blocks that couldn't be found, drawn gray.
     * @param subject The material index of the block looked at, or -1 if the scene has none.
     */
    public record Built(
            @NonNull ImportedScene scene,
            @NonNull List<Material> values,
            @NonNull List<String> missing,
            int subject) {}

    /**
     * Build a context scene around the block a look file describes, or with gray stand-ins where
     * the scene names the block looked at, if there is no look.
     *
     * @param context Where the blocks go.
     * @param lookFile The look of the block looked at, or null.
     * @return The scene, named after the context scene.
     */
    public static Built build(@NonNull ContextScene context, Path lookFile) {
        final BlockContent.Look subject =
                lookFile == null ? null : BlockContent.readLook(lookFile).orElse(null);
        return build(context.name(), context, subject);
    }

    /**
     * The ID of the block a look file describes, from where it is.
     *
     * @param lookFile The look file.
     * @return The block's ID, or empty if the file isn't in a content folder's looks.
     */
    public static Optional<String> blockId(@NonNull Path lookFile) {
        return BlockContent.idOf(lookFile, BlockContent.LOOKS, BlockContent.LOOK_EXTENSION);
    }

    /**
     * Build one block standing on its own.
     *
     * @param look The block's look.
     * @return The scene.
     */
    public static Built single(@NonNull BlockContent.Look look) {
        return build(
                look.id(),
                new ContextScene(
                        look.id(),
                        0,
                        List.of(new ContextScene.Cell(0, 0, 0, ContextScene.SUBJECT))),
                look);
    }

    /**
     * Build a context scene around a block.
     *
     * @param name What to call the scene.
     * @param context Where the blocks go.
     * @param subject The block looked at, for the scene's {@value ContextScene#SUBJECT} cells, or
     *     null to draw them gray.
     * @return The scene.
     */
    public static Built build(
            @NonNull String name, @NonNull ContextScene context, BlockContent.Look subject) {
        // One mesh and material for each kind of block, in the order they first appear
        final Map<String, Integer> kinds = new LinkedHashMap<>();
        final List<Mesh> meshes = new ArrayList<>();
        final List<ImportedMaterial> materials = new ArrayList<>();
        final List<Material> values = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        final Matrix4d identity = new Matrix4d();
        final Node root = new Node(name, identity, null, identity, new int[0]);
        for (ContextScene.Cell cell : context.cells()) {
            final Integer kind =
                    kinds.computeIfAbsent(
                            cell.block(),
                            block -> {
                                addKind(block, subject, meshes, materials, values, missing);
                                return meshes.size() - 1;
                            });
            // A missing block's stand-in cube is centered on its origin, where models start at
            // the cell's corner
            final double shift = missing.contains(meshes.get(kind).name()) ? HALF : 0;
            final Matrix4d at =
                    new Matrix4d()
                            .translation(cell.x() + shift, cell.y() + shift, cell.z() + shift);
            root.addChild(new Node(label(cell, subject), at, root, root.world(), new int[] {kind}));
        }
        final ImportedScene scene =
                new ImportedScene(
                        // A block's ID has a colon, which a file name can't on Windows
                        Path.of(name.replace(':', '_')),
                        meshes,
                        materials,
                        root,
                        List.of(),
                        1,
                        1,
                        List.of(),
                        0);
        return new Built(
                scene,
                values,
                List.copyOf(missing),
                subject == null ? -1 : kinds.getOrDefault(ContextScene.SUBJECT, -1));
    }

    /**
     * A cell's node name: its block and where it is.
     *
     * @param cell The cell.
     * @param subject The block looked at.
     * @return The name.
     */
    private static String label(ContextScene.Cell cell, BlockContent.Look subject) {
        final String block =
                ContextScene.SUBJECT.equals(cell.block()) && subject != null
                        ? subject.id()
                        : cell.block();
        return block + " at " + cell.x() + ", " + cell.y() + ", " + cell.z();
    }

    /**
     * Add a kind of block's mesh and material.
     *
     * @param block The block's ID, or {@value ContextScene#SUBJECT}.
     * @param subject The block looked at, or null.
     * @param meshes Receives its mesh.
     * @param materials Receives its material.
     * @param values Receives its render material's values, or null.
     * @param missing Receives its ID if it couldn't be found.
     */
    private static void addKind(
            String block,
            BlockContent.Look subject,
            List<Mesh> meshes,
            List<ImportedMaterial> materials,
            List<Material> values,
            List<String> missing) {
        final int index = meshes.size();
        final Optional<BlockContent.Look> look =
                ContextScene.SUBJECT.equals(block)
                        ? Optional.ofNullable(subject)
                        : BlockContent.look(block);
        final String name = look.map(BlockContent.Look::id).orElse(block);
        final MeshData model = look.flatMap(BlockScenes::model).orElse(null);
        if (look.isEmpty() || model == null) {
            missing.add(name);
            // Centered on the origin, so its cells are moved half a block, see CENTERED
            meshes.add(new Mesh(name, PreviewShapes.cube(HALF), index));
            materials.add(
                    new ImportedMaterial(
                            name, new Vector4f(MISSING_COLOR), 0.8f, 0, new Vector3f(), Map.of()));
            values.add(null);
            return;
        }
        meshes.add(new Mesh(name, model, index));
        final Map<TextureSlot, TextureRef> textures = new EnumMap<>(TextureSlot.class);
        BlockContent.textureFile(look.get())
                .ifPresent(
                        file ->
                                textures.put(
                                        TextureSlot.BASE_COLOR,
                                        new TextureRef(look.get().texture(), file)));
        BlockContent.normalFile(look.get())
                .ifPresent(
                        file ->
                                textures.put(
                                        TextureSlot.NORMAL,
                                        new TextureRef(file.getFileName().toString(), file)));
        final Material stored =
                BlockContent.materialFile(look.get()).map(BlockScenes::readMaterial).orElse(null);
        if (stored != null && stored.getEmissive().lengthSquared() > 0) {
            // A glowing block glows as its texture shows, as the game draws it
            final TextureRef base = textures.get(TextureSlot.BASE_COLOR);
            if (base != null) {
                textures.put(TextureSlot.EMISSIVE, base);
            }
        }
        materials.add(
                new ImportedMaterial(
                        name, new Vector4f(1), Float.NaN, Float.NaN, new Vector3f(), textures));
        values.add(stored);
    }

    /**
     * Read a look's model.
     *
     * @param look The look.
     * @return The mesh, or empty if it can't be found or read.
     */
    private static Optional<MeshData> model(BlockContent.Look look) {
        final Optional<Path> file = BlockContent.modelFile(look);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        try {
            return AssetFile.open(file.get()).section(MeshSection.TAG).map(MeshSection::decode);
        } catch (RuntimeException e) {
            log.warn("Could not read the block model {}: {}", file.get(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Read a render material's values.
     *
     * @param file The material.
     * @return The values, or null if it can't be read.
     */
    private static Material readMaterial(Path file) {
        try {
            return AssetConvert.readMaterial(file).material();
        } catch (RuntimeException e) {
            log.warn("Could not read the render material {}: {}", file, e.getMessage());
            return null;
        }
    }
}
