package com.ikalagaming.graphics.asset;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A material as YAML, the text form of a {@link MaterialSection}, for reading, diffing and editing
 * by hand or by other tools. Every key is optional, defaulting to a matte non-metal as below; an
 * unknown key or a value of the wrong kind is an error that names it. Writing gives every key, in
 * this order.
 *
 * <pre>
 * transparency: opaque      # opaque, cutout or translucent
 * baseColor: [1, 1, 1, 1]   # sRGB red, green, blue and opacity; multiplies nothing a texture sets
 * emissive: [0, 0, 0]       # light given off, cd/m^2, linear, times the emissive texture if any
 * roughness: 0.85           # 0 polished to 1 matte
 * metallic: 0               # 0 non-metal to 1 metal
 * specular: 0.5             # a non-metal's reflection head on: 0.5 is 4%, 1 is 8%
 * specularTint: 0           # how much that takes on the base color
 * subsurface: 0             # flattens the diffuse light, as light under the surface does
 * sheen: 0                  # a soft glow at grazing angles, for cloth
 * sheenTint: 0              # how much the sheen takes on the base color
 * clearcoat: 0              # a clear varnish layer on top
 * clearcoatGloss: 1         # how glossy the varnish is
 * anisotropic: 0            # how much the highlight stretches
 * normalGreenDown: false    # the normal map's green points down, as DirectX has it
 * packedOcclusion: false    # the packed map's red is occlusion
 * triplanar: false          # level of detail projects the texture along each axis
 * textures:                 # names, as the loader resolves them; leave one out for none
 *   baseColor: stone.png
 *   normal: stone.normal.png
 *   packed: stone.orm.png
 *   emissive: stone.emissive.png
 * </pre>
 */
public final class MaterialText {

    /** The key for transparency. */
    public static final String KEY_TRANSPARENCY = "transparency";

    /** The key for the base color. */
    public static final String KEY_BASE_COLOR = "baseColor";

    /** The key for the emission. */
    public static final String KEY_EMISSIVE = "emissive";

    /** The key for roughness. */
    public static final String KEY_ROUGHNESS = "roughness";

    /** The key for metallic. */
    public static final String KEY_METALLIC = "metallic";

    /** The key for specular. */
    public static final String KEY_SPECULAR = "specular";

    /** The key for the specular tint. */
    public static final String KEY_SPECULAR_TINT = "specularTint";

    /** The key for subsurface. */
    public static final String KEY_SUBSURFACE = "subsurface";

    /** The key for sheen. */
    public static final String KEY_SHEEN = "sheen";

    /** The key for the sheen tint. */
    public static final String KEY_SHEEN_TINT = "sheenTint";

    /** The key for the clearcoat. */
    public static final String KEY_CLEARCOAT = "clearcoat";

    /** The key for the clearcoat's gloss. */
    public static final String KEY_CLEARCOAT_GLOSS = "clearcoatGloss";

    /** The key for anisotropy. */
    public static final String KEY_ANISOTROPIC = "anisotropic";

    /** The key for a DirectX normal map. */
    public static final String KEY_NORMAL_GREEN_DOWN = "normalGreenDown";

    /** The key for packed occlusion. */
    public static final String KEY_PACKED_OCCLUSION = "packedOcclusion";

    /** The key for triplanar level of detail. */
    public static final String KEY_TRIPLANAR = "triplanar";

    /** The key for the texture names. */
    public static final String KEY_TEXTURES = "textures";

    /** The texture key for the base color. */
    public static final String TEXTURE_BASE_COLOR = "baseColor";

    /** The texture key for the normal map. */
    public static final String TEXTURE_NORMAL = "normal";

    /** The texture key for the packed map. */
    public static final String TEXTURE_PACKED = "packed";

    /** The texture key for the emission map. */
    public static final String TEXTURE_EMISSIVE = "emissive";

    /** Roughness when a file doesn't say: matte. */
    public static final float DEFAULT_ROUGHNESS = 0.85f;

    /** Specular when a file doesn't say: 4% head on, as most non-metals reflect. */
    public static final float DEFAULT_SPECULAR = 0.5f;

    /** Clearcoat gloss when a file doesn't say: as glossy as varnish. */
    public static final float DEFAULT_CLEARCOAT_GLOSS = 1f;

    /** Every key a file may have at its top level. */
    private static final Set<String> KEYS =
            Set.of(
                    KEY_TRANSPARENCY,
                    KEY_BASE_COLOR,
                    KEY_EMISSIVE,
                    KEY_ROUGHNESS,
                    KEY_METALLIC,
                    KEY_SPECULAR,
                    KEY_SPECULAR_TINT,
                    KEY_SUBSURFACE,
                    KEY_SHEEN,
                    KEY_SHEEN_TINT,
                    KEY_CLEARCOAT,
                    KEY_CLEARCOAT_GLOSS,
                    KEY_ANISOTROPIC,
                    KEY_NORMAL_GREEN_DOWN,
                    KEY_PACKED_OCCLUSION,
                    KEY_TRIPLANAR,
                    KEY_TEXTURES);

    /** Every key the textures may have. */
    private static final Set<String> TEXTURE_KEYS =
            Set.of(TEXTURE_BASE_COLOR, TEXTURE_NORMAL, TEXTURE_PACKED, TEXTURE_EMISSIVE);

    /**
     * A matte non-metal, what a file with no values gives: the defaults above.
     *
     * @return A new material.
     */
    public static Material defaults() {
        final Material material = new Material();
        material.setRoughness(DEFAULT_ROUGHNESS);
        material.setSpecular(DEFAULT_SPECULAR);
        material.setClearcoatGloss(DEFAULT_CLEARCOAT_GLOSS);
        return material;
    }

    /**
     * Read a material from YAML.
     *
     * @param text The YAML.
     * @return The material, with no textures set, and the names of its textures.
     * @throws AssetFormatException If it isn't valid YAML, or a key or value is wrong.
     */
    public static MaterialSection.Stored read(@NonNull String text) {
        final Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (YAMLException e) {
            throw new AssetFormatException("Not valid YAML: " + e.getMessage(), e);
        }
        final Material material = defaults();
        if (document == null) {
            return new MaterialSection.Stored(material, MaterialSection.Textures.NONE);
        }
        final Map<?, ?> map = asMap(document, "the material");
        checkKeys(map, KEYS, "");
        if (map.containsKey(KEY_TRANSPARENCY)) {
            final Object value = map.get(KEY_TRANSPARENCY);
            try {
                material.setTransparency(
                        Material.Transparency.valueOf(
                                String.valueOf(value).toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new AssetFormatException(
                        KEY_TRANSPARENCY + " must be opaque, cutout or translucent, not " + value,
                        e);
            }
        }
        final float[] color = numbers(map, KEY_BASE_COLOR, 4);
        if (color != null) {
            material.getBaseColor().set(color[0], color[1], color[2], color[3]);
        }
        final float[] emission = numbers(map, KEY_EMISSIVE, 3);
        if (emission != null) {
            material.getEmissive().set(emission[0], emission[1], emission[2]);
        }
        material.setRoughness(number(map, KEY_ROUGHNESS, material.getRoughness()));
        material.setMetallic(number(map, KEY_METALLIC, material.getMetallic()));
        material.setSpecular(number(map, KEY_SPECULAR, material.getSpecular()));
        material.setSpecularTint(number(map, KEY_SPECULAR_TINT, material.getSpecularTint()));
        material.setSubsurface(number(map, KEY_SUBSURFACE, material.getSubsurface()));
        material.setSheen(number(map, KEY_SHEEN, material.getSheen()));
        material.setSheenTint(number(map, KEY_SHEEN_TINT, material.getSheenTint()));
        material.setClearcoat(number(map, KEY_CLEARCOAT, material.getClearcoat()));
        material.setClearcoatGloss(number(map, KEY_CLEARCOAT_GLOSS, material.getClearcoatGloss()));
        material.setAnisotropic(number(map, KEY_ANISOTROPIC, material.getAnisotropic()));
        material.setNormalGreenDown(flag(map, KEY_NORMAL_GREEN_DOWN));
        material.setPackedOcclusion(flag(map, KEY_PACKED_OCCLUSION));
        material.setTriplanar(flag(map, KEY_TRIPLANAR));
        MaterialSection.Textures textures = MaterialSection.Textures.NONE;
        if (map.containsKey(KEY_TEXTURES) && map.get(KEY_TEXTURES) != null) {
            final Map<?, ?> named = asMap(map.get(KEY_TEXTURES), KEY_TEXTURES);
            checkKeys(named, TEXTURE_KEYS, KEY_TEXTURES + ".");
            textures =
                    new MaterialSection.Textures(
                            name(named, TEXTURE_BASE_COLOR),
                            name(named, TEXTURE_NORMAL),
                            name(named, TEXTURE_PACKED),
                            name(named, TEXTURE_EMISSIVE));
        }
        return new MaterialSection.Stored(material, textures);
    }

    /**
     * Write a material as YAML, every key in order.
     *
     * @param material The material's values.
     * @param textures The names of its textures.
     * @return The YAML.
     */
    public static String write(
            @NonNull Material material, @NonNull MaterialSection.Textures textures) {
        final StringBuilder text = new StringBuilder();
        line(text, KEY_TRANSPARENCY, material.getTransparency().name().toLowerCase(Locale.ROOT));
        line(
                text,
                KEY_BASE_COLOR,
                list(
                        material.getBaseColor().x,
                        material.getBaseColor().y,
                        material.getBaseColor().z,
                        material.getBaseColor().w));
        line(
                text,
                KEY_EMISSIVE,
                list(material.getEmissive().x, material.getEmissive().y, material.getEmissive().z));
        line(text, KEY_ROUGHNESS, number(material.getRoughness()));
        line(text, KEY_METALLIC, number(material.getMetallic()));
        line(text, KEY_SPECULAR, number(material.getSpecular()));
        line(text, KEY_SPECULAR_TINT, number(material.getSpecularTint()));
        line(text, KEY_SUBSURFACE, number(material.getSubsurface()));
        line(text, KEY_SHEEN, number(material.getSheen()));
        line(text, KEY_SHEEN_TINT, number(material.getSheenTint()));
        line(text, KEY_CLEARCOAT, number(material.getClearcoat()));
        line(text, KEY_CLEARCOAT_GLOSS, number(material.getClearcoatGloss()));
        line(text, KEY_ANISOTROPIC, number(material.getAnisotropic()));
        line(text, KEY_NORMAL_GREEN_DOWN, Boolean.toString(material.isNormalGreenDown()));
        line(text, KEY_PACKED_OCCLUSION, Boolean.toString(material.isPackedOcclusion()));
        line(text, KEY_TRIPLANAR, Boolean.toString(material.isTriplanar()));
        final String[] keys = {
            TEXTURE_BASE_COLOR, TEXTURE_NORMAL, TEXTURE_PACKED, TEXTURE_EMISSIVE
        };
        final String[] names = textures.slots();
        boolean any = false;
        for (int i = 0; i < keys.length; ++i) {
            if (names[i] == null) {
                continue;
            }
            if (!any) {
                text.append(KEY_TEXTURES).append(":\n");
                any = true;
            }
            text.append("  ").append(keys[i]).append(": ").append(quoted(names[i])).append('\n');
        }
        return text.toString();
    }

    /**
     * Add a line.
     *
     * @param text Where to.
     * @param key The key.
     * @param value The value, already written as YAML.
     */
    private static void line(
            @NonNull StringBuilder text, @NonNull String key, @NonNull String value) {
        text.append(key).append(": ").append(value).append('\n');
    }

    /**
     * A number as short as it can be written and read back the same.
     *
     * @param value The number.
     * @return The text.
     */
    private static String number(float value) {
        final String text = Float.toString(value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /**
     * Numbers as a flow list.
     *
     * @param values The numbers.
     * @return The text.
     */
    private static String list(float... values) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.length; ++i) {
            text.append(i == 0 ? "" : ", ").append(number(values[i]));
        }
        return text.append(']').toString();
    }

    /**
     * Text quoted for YAML, so names that look like numbers or hold special characters stay text.
     *
     * @param value The text.
     * @return The quoted text.
     */
    private static String quoted(@NonNull String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * A value as a map.
     *
     * @param value The value.
     * @param what What it is, for the error.
     * @return The map.
     * @throws AssetFormatException If it isn't one.
     */
    private static Map<?, ?> asMap(Object value, @NonNull String what) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new AssetFormatException(what + " must be a map of keys");
    }

    /**
     * Check that a map has only known keys.
     *
     * @param map The map.
     * @param known The keys it may have.
     * @param prefix What to put before a key in the error.
     * @throws AssetFormatException If it has another.
     */
    private static void checkKeys(
            @NonNull Map<?, ?> map, @NonNull Set<String> known, @NonNull String prefix) {
        for (Object key : map.keySet()) {
            if (!known.contains(String.valueOf(key))) {
                throw new AssetFormatException("Unknown key " + prefix + key);
            }
        }
    }

    /**
     * A number.
     *
     * @param map The map.
     * @param key Its key.
     * @param fallback The number if it is missing.
     * @return The number.
     * @throws AssetFormatException If it isn't a number.
     */
    private static float number(@NonNull Map<?, ?> map, @NonNull String key, float fallback) {
        final Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.floatValue();
        }
        throw new AssetFormatException(key + " must be a number, not " + value);
    }

    /**
     * A list of numbers.
     *
     * @param map The map.
     * @param key Its key.
     * @param count How many it must have.
     * @return The numbers, or null if missing.
     * @throws AssetFormatException If it isn't that many numbers.
     */
    private static float[] numbers(@NonNull Map<?, ?> map, @NonNull String key, int count) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.size() == count) {
            final float[] out = new float[count];
            for (int i = 0; i < count; ++i) {
                if (!(list.get(i) instanceof Number n)) {
                    throw new AssetFormatException(key + " must be numbers, not " + value);
                }
                out[i] = n.floatValue();
            }
            return out;
        }
        throw new AssetFormatException(key + " must be a list of " + count + " numbers");
    }

    /**
     * A true or false value.
     *
     * @param map The map.
     * @param key Its key.
     * @return The value, false if missing.
     * @throws AssetFormatException If it isn't true or false.
     */
    private static boolean flag(@NonNull Map<?, ?> map, @NonNull String key) {
        final Object value = map.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new AssetFormatException(key + " must be true or false, not " + value);
    }

    /**
     * A texture name.
     *
     * @param map The textures.
     * @param key The slot.
     * @return The name, or null if missing.
     * @throws AssetFormatException If it isn't text, or is empty.
     */
    private static String name(@NonNull Map<?, ?> map, @NonNull String key) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isEmpty()) {
            return text;
        }
        throw new AssetFormatException(KEY_TEXTURES + "." + key + " must be a file name");
    }

    /** Not to be made. */
    private MaterialText() {}
}
