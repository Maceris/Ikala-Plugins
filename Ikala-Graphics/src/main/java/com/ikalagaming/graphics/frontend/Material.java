package com.ikalagaming.graphics.frontend;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * A material for rendering that defines color and textures for a mesh. Based on the Disney
 * Principled BRDF.
 */
@Getter
@Setter
public class Material {
    /** The default color to use when unspecified. */
    public static final Vector4f DEFAULT_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    /** The surface color of the material, usually this would be a texture. */
    private final Vector4f baseColor;

    /**
     * The degree of anisotropy, controls the aspect ratio of the specular highlight. 0 is
     * isotropic, 1 is maximally anisotropic.
     */
    private float anisotropic;

    /**
     * A special-purpose secondary specular lobe for representing a clearcoat layer. This represents
     * a polyurethane coating, and the value controls the strength. When set to 0, the layer is
     * disabled.
     */
    private float clearcoat;

    /**
     * Controls glossiness of the clearcoat, where 0 is a satin appearance and 1 is a glossy one.
     */
    private float clearcoatGloss;

    /**
     * How metallic the substance is. 0 is dielectric, 1 is metallic, values in between linearly
     * blend between the two. Metallic substances have no diffuse component and have tinted specular
     * (the base color).
     */
    private float metallic;

    /** Surface roughness, for both diffuse and specular. */
    private float roughness;

    /** Grazing component, generally for cloth. */
    private float sheen;

    /** Amount to tint sheen towards the base color. */
    private float sheenTint;

    /**
     * Incident specular amount. Corresponds to index of refraction values in the range [1.0, 1.8].
     */
    private float specular;

    /** Tints incident specular towards the base color. Grazing specular is still un-tinted. */
    private float specularTint;

    /** Simulates local subsurface scattering with an approximation. */
    private float subsurface;

    /** The handle for the normal map texture, which might be null. */
    private Texture normalMap;

    /** The handle for the texture, which might be null. */
    private Texture texture;

    /** Create a default material with no texture. */
    public Material() {
        baseColor = new Vector4f(Material.DEFAULT_COLOR);
        anisotropic = 0;
        clearcoat = 0;
        clearcoatGloss = 0;
        metallic = 0;
        roughness = 0;
        sheen = 0;
        sheenTint = 0;
        specular = 0;
        specularTint = 0;
        subsurface = 0;
    }
}
