/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.frontend;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector4f;

import java.util.Objects;

/** A material for rendering that defines color and textures for a mesh. */
@Getter
@Setter
public class Material {
    /** The default color to use when unspecified. */
    public static final Vector4f DEFAULT_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    /** The ambient color of the material. */
    @NonNull private Vector4f ambientColor;

    /** The diffuse color of the material. */
    @NonNull private Vector4f diffuseColor;

    /** The handle for the normal map texture, which might be null. */
    private Texture normalMap;

    /** The reflectance value of the material. */
    private float reflectance;

    /** The specular color of the material. */
    @NonNull private Vector4f specularColor;

    /** The handle for the texture, which might be null. */
    private Texture texture;

    /** Create a default, non-reflective material with no texture. */
    public Material() {
        diffuseColor = Material.DEFAULT_COLOR;
        ambientColor = Material.DEFAULT_COLOR;
        specularColor = Material.DEFAULT_COLOR;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Material other)) {
            return false;
        }

        final float delta = 0.001f;
        if (!ambientColor.equals(other.ambientColor, delta)
                || !diffuseColor.equals(other.diffuseColor, delta)
                || !specularColor.equals(other.specularColor, delta)
                || !Objects.equals(texture, other.texture)) {
            return false;
        }
        return Objects.equals(normalMap, other.normalMap)
                && (Math.abs(reflectance - other.reflectance) <= delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ambientColor, diffuseColor, specularColor, texture, normalMap, reflectance);
    }
}
