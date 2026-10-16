package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Edits the materials of a scene in the workbench: pick one, or make a new one or a copy, change
 * its colors and factors, put textures in its slots from the image last opened, and put it on the
 * selected mesh. Changes show at once, since materials are written to the GPU every frame. Render
 * thread only.
 */
public class MaterialEditor {
    /** The strongest emission the slider offers. */
    private static final float MAX_EMISSION = 50;

    /** The size of a texture slot's thumbnail. */
    private static final float THUMBNAIL = 48;

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

    /** The image the workbench opened last, which slots can take. */
    private final Supplier<Path> openImage;

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
     * @param loader Loads textures for the slots.
     * @param openImage The image the workbench opened last, or null if none.
     */
    public MaterialEditor(@NonNull TextureLoader loader, @NonNull Supplier<Path> openImage) {
        this.loader = loader;
        this.openImage = openImage;
    }

    /**
     * Edit a material.
     *
     * @param index Its index in the scene's materials, or -1 for none.
     */
    public void select(int index) {
        selected = index;
    }

    /** Forget the material being edited, when the scene closes. */
    public void clear() {
        selected = -1;
        named = -1;
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
        if (IkGui.beginCombo("Transparency", material.getTransparency().name().toLowerCase())) {
            for (Material.Transparency mode : modes) {
                if (IkGui.selectable(
                        mode.name().toLowerCase(), mode == material.getTransparency())) {
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

        IkGui.separatorText("Textures");
        final Path image = openImage.get();
        for (Slot slot : Slot.values()) {
            drawSlot(placement, material, slot, image);
        }
        if (image == null) {
            IkGui.textDisabled("Open an image in the file list to put it in a slot");
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
        changed |= IkGui.sliderFloat("Emission strength", value, 0, MAX_EMISSION, "%.2f");
        if (changed) {
            emissive.set(color3[0], color3[1], color3[2]).mul(value[0]);
        }
    }

    /**
     * Draw one texture slot: its thumbnail, and buttons to clear it or put the open image in it.
     *
     * @param placement The scene, which keeps textures loaded here.
     * @param material The material.
     * @param slot The slot.
     * @param image The image the workbench opened last, or null.
     */
    private void drawSlot(ScenePlacement placement, Material material, Slot slot, Path image) {
        IkGui.pushID(slot.name());
        final TextureHandle texture = slot.getter.apply(material);
        if (texture != null) {
            IkGui.image(texture, THUMBNAIL, THUMBNAIL);
        } else {
            IkGui.dummy(THUMBNAIL, THUMBNAIL);
        }
        IkGui.sameLine();
        IkGui.beginGroup();
        IkGui.text(slot.label);
        if (texture != null) {
            IkGui.textDisabled(placement.textureName(texture));
            if (IkGui.button("Clear")) {
                slot.setter.accept(material, null);
            }
        } else {
            IkGui.textDisabled("empty");
        }
        if (image != null) {
            if (texture != null) {
                IkGui.sameLine();
            }
            if (IkGui.button("Use " + image.getFileName())) {
                loader.load(
                        image,
                        slot.colorSpace,
                        loaded -> {
                            placement.adoptTexture(loaded, image.getFileName().toString());
                            slot.setter.accept(material, loaded);
                            if (slot == Slot.BASE_COLOR) {
                                material.getBaseColor().set(1, 1, 1, material.getBaseColor().w);
                            }
                        });
            }
        }
        IkGui.endGroup();
        IkGui.popID();
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
