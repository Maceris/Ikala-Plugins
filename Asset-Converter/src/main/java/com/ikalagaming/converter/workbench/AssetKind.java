package com.ikalagaming.converter.workbench;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** The kinds of raw asset the workbench opens, by file extension. */
public enum AssetKind {
    /** An image stb can decode, like a PNG or HDR file. */
    IMAGE(Set.of("png", "jpg", "jpeg", "tga", "bmp", "psd", "gif", "hdr", "pic", "pnm", "ppm")),
    /** Anything else. */
    UNKNOWN(Set.of());

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
