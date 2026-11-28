package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.AssetConvert;
import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MaterialText;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.flags.SliderFlags;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Edits the materials of a scene in the workbench: pick one, or make a new one or a copy, change
 * its colors and factors, fill its texture slots ({@link AssetSlot}s) from the asset index, and put
 * it on the selected mesh. Changes show at once, since materials are written to the GPU every
 * frame. Materials open from and save to files of their own, binary ({@code .ika}) or text ({@code
 * .material.yml}), naming their textures by paths relative to the file. Render thread only.
 */
@Slf4j
public class MaterialEditor {
    /**
     * The brightest emission the slider offers, in cd/m^2: about a bare filament. A phone screen is
     * a few hundred, a frosted bulb some tens of thousands.
     */
    private static final float MAX_EMISSION = 2_000_000;

    /** The extension the file dialogs list for a material's text form. */
    private static final String TEXT_EXTENSION = "yml";

    /** What a texture slot takes: texture files and images. */
    private static final Set<String> TEXTURE_TYPES = Set.of(EditorTypes.TEXTURE, EditorTypes.IMAGE);

    /** Loads a texture from a file, handing it over on the render thread. */
    @FunctionalInterface
    public interface TextureLoader {
        /**
         * Load a texture.
         *
         * @param file The image.
         * @param colorSpace The color space to read it in.
         * @param loaded Given the texture on the render thread.
         */
        void load(
                @NonNull Path file,
                @NonNull ColorSpace colorSpace,
                @NonNull Consumer<TextureHandle> loaded);
    }

    /** A texture slot of a material. */
    private enum Slot {
        /** Base color, sRGB. */
        BASE_COLOR("Base color", ColorSpace.SRGB, Material::getTexture, Material::setTexture),
        /** Normal map, linear. */
        NORMAL("Normal", ColorSpace.LINEAR, Material::getNormalMap, Material::setNormalMap),
        /** Packed occlusion, roughness and metal, linear. */
        PACKED(
                "Occlusion, roughness, metal",
                ColorSpace.LINEAR,
                Material::getOcclusionRoughnessMetal,
                Material::setOcclusionRoughnessMetal),
        /** Emissive, sRGB. */
        EMISSIVE("Emissive", ColorSpace.SRGB, Material::getEmissiveMap, Material::setEmissiveMap);

        /** Its name, for the panel. */
        final String label;

        /** The color space its textures load in. */
        final ColorSpace colorSpace;

        /** Reads the slot. */
        final java.util.function.Function<Material, TextureHandle> getter;

        /** Writes the slot. */
        final BiConsumer<Material, TextureHandle> setter;

        Slot(
                String label,
                ColorSpace colorSpace,
                java.util.function.Function<Material, TextureHandle> getter,
                BiConsumer<Material, TextureHandle> setter) {
            this.label = label;
            this.colorSpace = colorSpace;
            this.getter = getter;
            this.setter = setter;
        }
    }

    /** Loads textures for the slots. */
    private final TextureLoader loader;

    /** Shows file dialogs. */
    private final UI ui;

    /** Where each texture loaded here came from, to name it relative to a saved material. */
    private final Map<TextureHandle, Path> texturePaths = new HashMap<>();

    /** The file each material was opened from or saved to, to save to again. */
    private final Map<Material, Path> materialFiles = new HashMap<>();

    /** How the last open or save went, or empty. */
    private String fileStatus = "";

    /**
     * The file each material's texture slots name, by slot, kept apart from the textures loaded so
     * a missing file still shows, red, and is saved.
     */
    private final Map<Material, Path[]> slotFiles = new HashMap<>();

    /** Draws the texture slots, or null where the editor is never drawn. */
    @Setter private @Nullable AssetSlot slots;

    /** The material being edited, an index into the scene's materials, or -1. */
    private int selected = -1;

    /** The edited material's name, for typing. */
    private final IkString name = new IkString(128);

    /** Which material {@link #name} holds the name of. */
    private int named = -1;

    /** Scratch for color pickers. */
    private final float[] color4 = new float[4];

    /** Scratch for color pickers. */
    private final float[] color3 = new float[3];

    /** Scratch for sliders. */
    private final float[] value = new float[1];

    /**
     * Set up the editor.
     *
     * @param ui Shows file dialogs.
     * @param loader Loads textures for the slots.
     */
    public MaterialEditor(@NonNull UI ui, @NonNull TextureLoader loader) {
        this.ui = ui;
        this.loader = loader;
    }

    /**
     * Edit a material.
     *
     * @param index Its index in the scene's materials, or -1 for none.
     */
    public void select(int index) {
        selected = index;
    }

    /**
     * Save the material being edited to a file, as Save file does.
     *
     * @param placement The scene.
     * @param file The file.
     * @return Whether a material was being edited.
     */
    public boolean saveSelected(@NonNull ScenePlacement placement, @NonNull Path file) {
        if (selected < 0 || selected >= placement.getMaterials().size()) {
            return false;
        }
        save(placement, placement.getMaterials().get(selected), file);
        return true;
    }

    /** Forget the material being edited, when the scene closes. */
    public void clear() {
        selected = -1;
        named = -1;
        texturePaths.clear();
        materialFiles.clear();
        slotFiles.clear();
        fileStatus = "";
    }

    /**
     * Copy a material's settings and textures into a new one.
     *
     * @param source The material.
     * @return The copy.
     */
    static Material copy(@NonNull Material source) {
        Material material = new Material();
        material.getBaseColor().set(source.getBaseColor());
        material.setAnisotropic(source.getAnisotropic());
        material.setClearcoat(source.getClearcoat());
        material.setClearcoatGloss(source.getClearcoatGloss());
        material.setMetallic(source.getMetallic());
        material.setRoughness(source.getRoughness());
        material.setSheen(source.getSheen());
        material.setSheenTint(source.getSheenTint());
        material.setSpecular(source.getSpecular());
        material.setSpecularTint(source.getSpecularTint());
        material.setSubsurface(source.getSubsurface());
        material.setTexture(source.getTexture());
        material.setNormalMap(source.getNormalMap());
        material.setOcclusionRoughnessMetal(source.getOcclusionRoughnessMetal());
        material.setPackedOcclusion(source.isPackedOcclusion());
        material.setEmissiveMap(source.getEmissiveMap());
        material.getEmissive().set(source.getEmissive());
        material.setNormalGreenDown(source.isNormalGreenDown());
        material.setTransparency(source.getTransparency());
        return material;
    }

    /**
     * Draw the editor.
     *
     * @param frame The current frame.
     * @param placement The scene.
     * @param node The selected node, or null.
     * @param sceneMesh The selected mesh of the node, a scene mesh index, or -1.
     */
    public void draw(
            @NonNull UiFrame frame,
            @NonNull ScenePlacement placement,
            @Nullable Node node,
            int sceneMesh) {
        final int count = placement.getMaterials().size();
        if (selected >= count) {
            selected = -1;
        }
        final String preview = selected < 0 ? "Pick a material" : placement.nameOf(selected);
        if (IkGui.beginCombo("##material", preview)) {
            for (int i = 0; i < count; ++i) {
                if (IkGui.selectable(placement.nameOf(i) + "##" + i, i == selected)) {
                    selected = i;
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.button("New")) {
            Material material = new Material();
            material.getBaseColor().set(0.8f, 0.8f, 0.8f, 1);
            material.setRoughness(0.5f);
            material.setSpecular(MaterialText.DEFAULT_SPECULAR);
            material.setNormalGreenDown(placement.isNormalGreenDown());
            selected = placement.addMaterial("Material " + (count + 1), material);
        }
        IkGui.sameLine();
        if (IkGui.button("Duplicate") && selected >= 0) {
            selected =
                    placement.addMaterial(
                            placement.nameOf(selected) + " copy",
                            copy(placement.getMaterials().get(selected)));
        }
        drawFiles(placement);

        if (node != null && sceneMesh >= 0) {
            drawAssignment(placement, node, sceneMesh);
        }
        if (selected < 0) {
            IkGui.textDisabled("Pick a material, or a mesh in the scene");
            return;
        }
        drawFields(placement, placement.getMaterials().get(selected));
    }

    /**
     * Draw what the selected mesh is drawn with, and buttons to edit or replace it.
     *
     * @param placement The scene.
     * @param node The selected node.
     * @param sceneMesh The selected mesh.
     */
    private void drawAssignment(ScenePlacement placement, Node node, int sceneMesh) {
        final Material current = placement.materialOf(node, sceneMesh);
        final int index = placement.getMaterials().indexOf(current);
        IkGui.textWrapped(
                "Selected mesh: "
                        + placement.getImported().meshes().get(sceneMesh).name()
                        + ", drawn with "
                        + (index < 0 ? "?" : placement.nameOf(index)));
        if (IkGui.button("Edit its material") && index >= 0) {
            selected = index;
        }
        if (selected >= 0 && selected != index) {
            IkGui.sameLine();
            if (IkGui.button("Put this one on it")) {
                placement.assign(node, sceneMesh, placement.getMaterials().get(selected));
            }
        }
        IkGui.sameLine();
        if (IkGui.button("Its own")) {
            placement.assign(node, sceneMesh, null);
        }
    }

    /**
     * Draw a material's settings.
     *
     * @param placement The scene.
     * @param material The material.
     */
    private void drawFields(ScenePlacement placement, Material material) {
        if (named != selected) {
            name.set(placement.nameOf(selected));
            named = selected;
        }
        if (IkGui.inputText("Name", name)) {
            placement.rename(selected, name.get());
        }
        drawValues(material);

        IkGui.separatorText("Textures");
        if (slots != null) {
            for (Slot slot : Slot.values()) {
                drawSlot(slots, placement, material, slot);
            }
        }
    }

    /**
     * Draw a material's values: its colors, factors and switches, changed in place, so the scenes
     * drawing it show changes at once.
     *
     * @param material The material.
     */
    public void drawValues(@NonNull Material material) {
        final Vector4f base = material.getBaseColor();
        color4[0] = base.x;
        color4[1] = base.y;
        color4[2] = base.z;
        color4[3] = base.w;
        if (IkGui.colorEdit4("Base color", color4)) {
            base.set(color4[0], color4[1], color4[2], color4[3]);
        }
        if (material.getTexture() != null) {
            IkGui.textDisabled("The base color texture stands in for the color");
        }
        final Material.Transparency[] modes = Material.Transparency.values();
        if (IkGui.beginCombo(
                "Transparency", material.getTransparency().name().toLowerCase(Locale.ROOT))) {
            for (Material.Transparency mode : modes) {
                if (IkGui.selectable(
                        mode.name().toLowerCase(Locale.ROOT), mode == material.getTransparency())) {
                    material.setTransparency(mode);
                }
            }
            IkGui.endCombo();
        }
        slider("Roughness", material.getRoughness(), material::setRoughness);
        slider("Metallic", material.getMetallic(), material::setMetallic);
        slider("Specular", material.getSpecular(), material::setSpecular);
        if (material.getOcclusionRoughnessMetal() != null) {
            IkGui.textDisabled("The packed texture stands in for roughness and metallic");
        }
        if (IkGui.collapsingHeader("More factors###more")) {
            slider("Specular tint", material.getSpecularTint(), material::setSpecularTint);
            slider("Sheen", material.getSheen(), material::setSheen);
            slider("Sheen tint", material.getSheenTint(), material::setSheenTint);
            slider("Clearcoat", material.getClearcoat(), material::setClearcoat);
            slider("Clearcoat gloss", material.getClearcoatGloss(), material::setClearcoatGloss);
            slider("Anisotropic", material.getAnisotropic(), material::setAnisotropic);
            slider("Subsurface", material.getSubsurface(), material::setSubsurface);
        }

        drawEmission(material);
        if (IkGui.checkbox(
                "Normal map green points down (DirectX)", material.isNormalGreenDown())) {
            material.setNormalGreenDown(!material.isNormalGreenDown());
        }
        if (IkGui.checkbox("Packed map's red is occlusion", material.isPackedOcclusion())) {
            material.setPackedOcclusion(!material.isPackedOcclusion());
        }
    }

    /**
     * Draw the emitted light: a color and a strength, kept in the material as their product.
     *
     * @param material The material.
     */
    private void drawEmission(Material material) {
        final Vector3f emissive = material.getEmissive();
        final float strength = Math.max(emissive.x, Math.max(emissive.y, emissive.z));
        if (strength > 0) {
            color3[0] = emissive.x / strength;
            color3[1] = emissive.y / strength;
            color3[2] = emissive.z / strength;
        } else {
            color3[0] = 1;
            color3[1] = 1;
            color3[2] = 1;
        }
        boolean changed = IkGui.colorEdit3("Emission color", color3);
        value[0] = strength;
        // Logarithmic, since screens are hundreds of cd/m^2 and lamps hundreds of thousands
        changed |=
                IkGui.sliderFloat(
                        "Emission (cd/m^2)",
                        value,
                        0,
                        MAX_EMISSION,
                        "%.0f",
                        SliderFlags.LOGARITHMIC);
        if (changed) {
            emissive.set(color3[0], color3[1], color3[2]).mul(value[0]);
        }
    }

    /**
     * Draw one texture slot, and load what it is given: a texture file's map for the slot, or an
     * image.
     *
     * @param slots Draws the slot.
     * @param placement The scene, which keeps textures loaded here.
     * @param material The material.
     * @param slot The slot.
     */
    private void drawSlot(AssetSlot slots, ScenePlacement placement, Material material, Slot slot) {
        final TextureHandle texture = slot.getter.apply(material);
        Path file = filesOf(material)[slot.ordinal()];
        if (file == null && texture != null) {
            file = texturePaths.get(texture);
        }
        final AssetSlot.Change change = slots.draw(slot.label, TEXTURE_TYPES, file, texture);
        if (change != null) {
            assign(placement, material, slot, change.file());
        }
    }

    /**
     * Put a file in a texture slot, or empty it, loading its texture.
     *
     * @param placement The scene, which keeps textures loaded here.
     * @param material The material.
     * @param slot The slot.
     * @param file The texture file or image, or null to empty the slot.
     */
    private void assign(
            ScenePlacement placement, Material material, Slot slot, @Nullable Path file) {
        if (file == null) {
            filesOf(material)[slot.ordinal()] = null;
            slot.setter.accept(material, null);
            return;
        }
        // A texture file holds a normal map beside its colors, which the normal slot takes
        final Path wanted =
                slot == Slot.NORMAL && file.toString().endsWith(AssetFormat.EXTENSION)
                        ? Path.of(file + BlockContent.NORMAL_MAP)
                        : file;
        filesOf(material)[slot.ordinal()] = wanted;
        loader.load(
                wanted,
                slot.colorSpace,
                loaded -> {
                    if (!wanted.equals(filesOf(material)[slot.ordinal()])) {
                        // Changed again while this one loaded
                        return;
                    }
                    placement.adoptTexture(loaded, file.getFileName().toString());
                    texturePaths.put(loaded, wanted);
                    slot.setter.accept(material, loaded);
                    if (slot == Slot.BASE_COLOR) {
                        material.getBaseColor().set(1, 1, 1, material.getBaseColor().w);
                    }
                });
    }

    /**
     * The files a material's texture slots name.
     *
     * @param material The material.
     * @return Its files by slot, null where a slot names none; changes write through.
     */
    private Path[] filesOf(Material material) {
        return slotFiles.computeIfAbsent(material, m -> new Path[Slot.values().length]);
    }

    /**
     * Draw the buttons to open a material file and save the selected one, and how it went.
     *
     * @param placement The scene.
     */
    private void drawFiles(@NonNull ScenePlacement placement) {
        final String[] extensions = {AssetFormat.EXTENSION.substring(1), TEXT_EXTENSION};
        if (IkGui.button("Open file...")) {
            ui.openFile(
                    "Open material",
                    null,
                    file -> {
                        if (file != null) {
                            open(placement, file);
                        }
                    },
                    extensions);
        }
        if (selected >= 0) {
            IkGui.sameLine();
            if (IkGui.button("Save file...")) {
                final Material material = placement.getMaterials().get(selected);
                final Path last = materialFiles.get(material);
                ui.saveFile(
                        "Save material",
                        last == null ? null : last.getParent(),
                        last == null
                                ? placement.nameOf(selected) + AssetFormat.EXTENSION
                                : last.getFileName().toString(),
                        file -> {
                            if (file != null) {
                                save(placement, material, file);
                            }
                        },
                        extensions);
            }
        }
        if (!fileStatus.isEmpty()) {
            IkGui.textWrapped(fileStatus);
        }
    }

    /**
     * Open a material file as a new material in the scene, loading its textures from paths relative
     * to it.
     *
     * @param placement The scene.
     * @param file The file, binary or text.
     * @return The material, or null if the file couldn't be read.
     */
    @Nullable Material open(@NonNull ScenePlacement placement, @NonNull Path file) {
        final MaterialSection.Stored stored;
        try {
            stored = AssetConvert.readMaterial(file);
        } catch (RuntimeException e) {
            log.warn("Couldn't open the material {}", file, e);
            fileStatus = "Couldn't open " + file.getFileName() + ": " + e.getMessage();
            return null;
        }
        final Material material = new Material().setValues(stored.material());
        selected = placement.addMaterial(materialName(file), material);
        materialFiles.put(material, file.toAbsolutePath());
        final String[] names = {
            stored.textures().baseColor(),
            stored.textures().normal(),
            stored.textures().packed(),
            stored.textures().emissive()
        };
        final Slot[] slots = Slot.values();
        for (int i = 0; i < slots.length; ++i) {
            if (names[i] == null) {
                continue;
            }
            final Slot slot = slots[i];
            final Path image = file.toAbsolutePath().resolveSibling(names[i]).normalize();
            // Kept even if it can't load, so the slot shows it missing and saving keeps it
            filesOf(material)[i] = image;
            loader.load(
                    image,
                    slot.colorSpace,
                    loaded -> {
                        placement.adoptTexture(loaded, image.getFileName().toString());
                        texturePaths.put(loaded, image);
                        slot.setter.accept(material, loaded);
                    });
        }
        fileStatus = "Opened " + file.getFileName();
        return material;
    }

    /**
     * Save a material to a file, binary or text by its ending, naming its textures by their paths
     * relative to it.
     *
     * @param placement The scene, for the names of textures that didn't come from a file here.
     * @param material The material.
     * @param file The file.
     */
    void save(@NonNull ScenePlacement placement, @NonNull Material material, @NonNull Path file) {
        final Path folder = file.toAbsolutePath().getParent();
        final Slot[] slots = Slot.values();
        final String[] names = new String[slots.length];
        boolean guessed = false;
        final Path[] named = filesOf(material);
        for (int i = 0; i < slots.length; ++i) {
            final TextureHandle texture = slots[i].getter.apply(material);
            if (texture == null && named[i] == null) {
                continue;
            }
            final Path source = named[i] != null ? named[i] : texturePaths.get(texture);
            if (source != null && source.getRoot().equals(folder.getRoot())) {
                names[i] = folder.relativize(source).toString().replace('\\', '/');
            } else if (source != null) {
                // On another drive, so no path leads there from the file; a mark is kept
                names[i] = source.getFileName().toString();
                guessed = true;
            } else {
                // From an imported scene, which only knows its name
                names[i] = placement.textureName(texture);
                guessed = true;
            }
        }
        try {
            AssetConvert.writeMaterial(
                    file,
                    material,
                    new MaterialSection.Textures(names[0], names[1], names[2], names[3]),
                    null);
        } catch (RuntimeException e) {
            log.warn("Couldn't save the material to {}", file, e);
            fileStatus = "Couldn't save " + file.getFileName() + ": " + e.getMessage();
            return;
        }
        materialFiles.put(material, file.toAbsolutePath());
        fileStatus =
                "Saved "
                        + file.getFileName()
                        + (guessed
                                ? "; some textures are named by file name only, as no path"
                                        + " leads to them from there"
                                : "");
    }

    /**
     * A material's name from its file: the file name without its ending.
     *
     * @param file The file.
     * @return The name.
     */
    private static String materialName(@NonNull Path file) {
        final String name = file.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(AssetConvert.MATERIAL_TEXT)) {
            return name.substring(0, name.length() - AssetConvert.MATERIAL_TEXT.length());
        }
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Draw a slider from 0 to 1 for one factor.
     *
     * @param label The label.
     * @param current The value now.
     * @param set Sets a new value.
     */
    private void slider(String label, float current, Consumer<Float> set) {
        value[0] = current;
        if (IkGui.sliderFloat(label, value, 0, 1)) {
            set.accept(value[0]);
        }
    }
}
