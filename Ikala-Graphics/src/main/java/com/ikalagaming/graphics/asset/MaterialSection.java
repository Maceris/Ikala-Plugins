package com.ikalagaming.graphics.asset;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A material's values and the names of its textures, as a {@code MATL} section: what the asset
 * editor saves for a material, and what a game reads back to draw with it. The values are the
 * Disney principled BRDF ones {@link Material} holds; textures are stored by name only, and whoever
 * loads the material decides what a name means, such as a path relative to the file.
 *
 * <p>Version 1, little-endian like every payload:
 *
 * <ol>
 *   <li>A fixed record: its own size in bytes (int), the transparency (int: 0 opaque, 1 cut out, 2
 *       translucent), flags (int, {@link #FLAG_NORMAL_GREEN_DOWN}, {@link #FLAG_PACKED_OCCLUSION},
 *       {@link #FLAG_TRIPLANAR}), the base color (4 floats, sRGB, alpha), the emission (3 floats,
 *       linear), then anisotropic, clearcoat, clearcoat gloss, metallic, roughness, sheen, sheen
 *       tint, specular, specular tint and subsurface (10 floats).
 *   <li>The texture names, for base color, normal, packed occlusion/roughness/metal and emission in
 *       that order: each a byte length (int), 0 for none, then that many bytes of UTF-8.
 * </ol>
 *
 * <p>Readers skip fields past the ones they know, using the record's size, so later versions can
 * add to it.
 */
public final class MaterialSection {

    /** The section's tag. */
    public static final int TAG = SectionTag.of("MATL");

    /** The version this code writes and reads. */
    public static final int VERSION = 1;

    /** The normal map's green channel points down, as in DirectX. */
    public static final int FLAG_NORMAL_GREEN_DOWN = 1;

    /** The packed map's red channel is occlusion. */
    public static final int FLAG_PACKED_OCCLUSION = 2;

    /** Level of detail projects the texture along each triangle's main axis. */
    public static final int FLAG_TRIPLANAR = 4;

    /** The size of the fixed record this version writes. */
    static final int RECORD_SIZE = 3 * Integer.BYTES + 17 * Float.BYTES;

    /** The longest texture name, in bytes. */
    static final int MAX_NAME = 4096;

    /**
     * The names of a material's textures, each null for none.
     *
     * @param baseColor The base color, sRGB.
     * @param normal The normal map.
     * @param packed The packed occlusion, roughness and metal map.
     * @param emissive The emission map, sRGB.
     */
    public record Textures(String baseColor, String normal, String packed, String emissive) {
        /** No textures. */
        public static final Textures NONE = new Textures(null, null, null, null);

        /**
         * The names in slot order.
         *
         * @return Base color, normal, packed and emissive.
         */
        String[] slots() {
            return new String[] {baseColor, normal, packed, emissive};
        }
    }

    /**
     * A stored material.
     *
     * @param material Its values, with no textures set: load them by {@code textures}' names.
     * @param textures The names of its textures.
     */
    public record Stored(@NonNull Material material, @NonNull Textures textures) {}

    /**
     * Encode a material.
     *
     * @param material The material. Its textures, top and bottom variants are left out.
     * @param textures The names to store for its textures.
     * @return The payload, little-endian, from position 0 to its limit.
     * @throws IllegalArgumentException If a name is empty or too long.
     */
    public static ByteBuffer encode(@NonNull Material material, @NonNull Textures textures) {
        final String[] names = textures.slots();
        final byte[][] bytes = new byte[names.length][];
        int size = RECORD_SIZE;
        for (int i = 0; i < names.length; ++i) {
            bytes[i] = names[i] == null ? new byte[0] : names[i].getBytes(StandardCharsets.UTF_8);
            if (names[i] != null && (bytes[i].length == 0 || bytes[i].length > MAX_NAME)) {
                throw new IllegalArgumentException("Bad texture name: \"" + names[i] + "\"");
            }
            size += Integer.BYTES + bytes[i].length;
        }
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(RECORD_SIZE);
        out.putInt(
                switch (material.getTransparency()) {
                    case OPAQUE -> 0;
                    case CUTOUT -> 1;
                    case TRANSLUCENT -> 2;
                });
        int flags = 0;
        if (material.isNormalGreenDown()) {
            flags |= FLAG_NORMAL_GREEN_DOWN;
        }
        if (material.isPackedOcclusion()) {
            flags |= FLAG_PACKED_OCCLUSION;
        }
        if (material.isTriplanar()) {
            flags |= FLAG_TRIPLANAR;
        }
        out.putInt(flags);
        out.putFloat(material.getBaseColor().x)
                .putFloat(material.getBaseColor().y)
                .putFloat(material.getBaseColor().z)
                .putFloat(material.getBaseColor().w);
        out.putFloat(material.getEmissive().x)
                .putFloat(material.getEmissive().y)
                .putFloat(material.getEmissive().z);
        out.putFloat(material.getAnisotropic())
                .putFloat(material.getClearcoat())
                .putFloat(material.getClearcoatGloss())
                .putFloat(material.getMetallic())
                .putFloat(material.getRoughness())
                .putFloat(material.getSheen())
                .putFloat(material.getSheenTint())
                .putFloat(material.getSpecular())
                .putFloat(material.getSpecularTint())
                .putFloat(material.getSubsurface());
        for (byte[] name : bytes) {
            out.putInt(name.length).put(name);
        }
        return out.flip();
    }

    /**
     * Decode a material.
     *
     * @param section The section.
     * @return The material, with no textures set, and the names of its textures.
     * @throws AssetFormatException If the section isn't a material this code can read, or is cut
     *     short.
     */
    public static Stored decode(@NonNull Section section) {
        if (section.tag() != TAG) {
            throw new AssetFormatException(
                    "Expected a MATL section, not " + SectionTag.toString(section.tag()));
        }
        if (section.version() != VERSION) {
            throw new AssetFormatException("Unsupported MATL version " + section.version());
        }
        final ByteBuffer in = section.data();
        if (in.remaining() < RECORD_SIZE) {
            throw new AssetFormatException("The MATL record is cut short");
        }
        final int start = in.position();
        final int recordSize = in.getInt();
        if (recordSize < RECORD_SIZE || recordSize > in.limit() - start) {
            throw new AssetFormatException("Bad MATL record size " + recordSize);
        }
        final Material material = new Material();
        final int transparency = in.getInt();
        material.setTransparency(
                switch (transparency) {
                    case 0 -> Material.Transparency.OPAQUE;
                    case 1 -> Material.Transparency.CUTOUT;
                    case 2 -> Material.Transparency.TRANSLUCENT;
                    default ->
                            throw new AssetFormatException(
                                    "Unknown MATL transparency " + transparency);
                });
        final int flags = in.getInt();
        material.setNormalGreenDown((flags & FLAG_NORMAL_GREEN_DOWN) != 0);
        material.setPackedOcclusion((flags & FLAG_PACKED_OCCLUSION) != 0);
        material.setTriplanar((flags & FLAG_TRIPLANAR) != 0);
        material.getBaseColor().set(in.getFloat(), in.getFloat(), in.getFloat(), in.getFloat());
        material.getEmissive().set(in.getFloat(), in.getFloat(), in.getFloat());
        material.setAnisotropic(in.getFloat());
        material.setClearcoat(in.getFloat());
        material.setClearcoatGloss(in.getFloat());
        material.setMetallic(in.getFloat());
        material.setRoughness(in.getFloat());
        material.setSheen(in.getFloat());
        material.setSheenTint(in.getFloat());
        material.setSpecular(in.getFloat());
        material.setSpecularTint(in.getFloat());
        material.setSubsurface(in.getFloat());
        in.position(start + recordSize);
        final String[] names = new String[4];
        for (int i = 0; i < names.length; ++i) {
            if (in.remaining() < Integer.BYTES) {
                throw new AssetFormatException("The MATL texture names are cut short");
            }
            final int length = in.getInt();
            if (length < 0 || length > MAX_NAME || length > in.remaining()) {
                throw new AssetFormatException("Bad MATL texture name length " + length);
            }
            if (length > 0) {
                final byte[] bytes = new byte[length];
                in.get(bytes);
                names[i] = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return new Stored(material, new Textures(names[0], names[1], names[2], names[3]));
    }

    /**
     * Write a material file: one {@code MATL} section.
     *
     * @param material The material.
     * @param textures The names to store for its textures.
     * @param metadata The file's metadata, naming what wrote it.
     * @return The file's bytes.
     */
    public static ByteBuffer file(
            @NonNull Material material,
            @NonNull Textures textures,
            @NonNull AssetMetadata metadata) {
        return new AssetWriter()
                .metadata(metadata)
                .section(TAG, VERSION, true, encode(material, textures))
                .toBuffer();
    }

    /**
     * Read the material in a file.
     *
     * @param file The file, as {@link AssetFile} read it.
     * @return The material.
     * @throws AssetFormatException If the file has no material, or it can't be read.
     */
    public static Stored read(@NonNull AssetFile file) {
        return decode(
                file.section(TAG)
                        .orElseThrow(() -> new AssetFormatException("The file has no material")));
    }

    /** Not to be made. */
    private MaterialSection() {}
}
