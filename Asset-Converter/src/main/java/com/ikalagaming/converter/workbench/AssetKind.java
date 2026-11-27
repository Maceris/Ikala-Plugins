package com.ikalagaming.converter.workbench;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** The kinds of raw asset the workbench opens, by file extension. */
public enum AssetKind {
    /** An image: a DDS texture, or one stb can decode, like a PNG or HDR file. */
    IMAGE(
            Set.of(
                    "dds", "png", "jpg", "jpeg", "tga", "bmp", "psd", "gif", "hdr", "pic", "pnm",
                    "ppm")),
    /** A model or whole scene assimp reads. */
    MODEL(
            Set.of(
                    "fbx", "obj", "gltf", "glb", "dae", "3ds", "blend", "ply", "stl", "x", "lwo",
                    "ms3d")),
    /** A {@code .ika} container, shown by the container viewer. */
    CONTAINER(Set.of("ika")),
    /** A particle effect, a YAML file named *.effect.yml, shown on the particle stage. */
    PARTICLE_EFFECT(Set.of()),
    /** A material as text, named *.material.yml, shown on a sphere in the 3D viewer. */
    MATERIAL(Set.of()),
    /** A graph for the graph panel, named *.graph.yml. */
    GRAPH(Set.of()),
    /** A game content definition, like a block's look, as KVT text. */
    LOOK(Set.of("kvt")),
    /** Anything else. */
    UNKNOWN(Set.of());

    /** What a particle effect's file name ends with. */
    public static final String PARTICLE_EFFECT_SUFFIX = ".effect.yml";

    /** What a material's text file name ends with. */
    public static final String MATERIAL_SUFFIX = ".material.yml";

    /** What a graph's file name ends with. */
    public static final String GRAPH_SUFFIX = ".graph.yml";

    /** The extensions of this kind, lower case and without the dot. */
    private final Set<String> extensions;

    AssetKind(@NonNull Set<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * Work out what kind of asset a file is from its extension, ignoring case.
     *
     * @param file The file.
     * @return The kind, or {@link #UNKNOWN}.
     */
    public static AssetKind of(@NonNull Path file) {
        final Path name = file.getFileName();
        if (name == null) {
            return UNKNOWN;
        }
        final String text = name.toString();
        if (text.toLowerCase(Locale.ROOT).endsWith(PARTICLE_EFFECT_SUFFIX)) {
            return PARTICLE_EFFECT;
        }
        if (text.toLowerCase(Locale.ROOT).endsWith(MATERIAL_SUFFIX)) {
            return MATERIAL;
        }
        if (text.toLowerCase(Locale.ROOT).endsWith(GRAPH_SUFFIX)) {
            return GRAPH;
        }
        final int dot = text.lastIndexOf('.');
        if (dot < 0) {
            return UNKNOWN;
        }
        final String extension = text.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (AssetKind kind : values()) {
            if (kind.extensions.contains(extension)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
