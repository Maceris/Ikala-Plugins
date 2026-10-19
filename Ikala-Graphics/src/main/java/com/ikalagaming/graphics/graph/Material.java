package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.TextureHandle;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;
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
    private TextureHandle normalMap;

    /** The handle for the texture, which might be null. */
    private TextureHandle texture;

    /**
     * Packed occlusion (red), roughness (green) and metalness (blue), linear, as glTF and Bistro
     * pack them, or null for none. Where set, its roughness and metalness stand in for the
     * material's, and if {@link #packedOcclusion} is set, its red dims the ambient light.
     */
    private TextureHandle occlusionRoughnessMetal;

    /**
     * Whether {@link #occlusionRoughnessMetal}'s red channel is occlusion. Some packed maps leave
     * it unused, like Bistro's, which hold 0 there and would black out the ambient light.
     */
    private boolean packedOcclusion;

    /** The light the surface gives off, as an sRGB texture times {@link #emissive}, or null. */
    private TextureHandle emissiveMap;

    /**
     * The light the surface gives off, in linear light, in the scene's light units: the color times
     * its strength. Multiplies the emissive map if there is one. Black for none.
     */
    private final Vector3f emissive;

    /**
     * Whether the normal map's green channel points down the texture, as DirectX and many FBX
     * exports have it, instead of up, as OpenGL and glTF have it.
     */
    private boolean normalGreenDown;

    /**
     * Whether the normal map holds baked data: the tangent space normal's x and y in red and green,
     * roughness in blue and metalness in alpha, which stand in for the material's own, as a level
     * of detail bake writes them.
     */
    private boolean bakedData;

    /**
     * Whether, as a model's level of detail bake page, its color texture names a material and a
     * shade per texel rather than holding colors: red the shade over 2, green and blue the
     * material's index, read without filtering, so an instance's material remaps reach far baked
     * tiles. Set with {@link #bakedData}, whose data texture then holds how much the baked normals
     * agree in place of roughness.
     */
    private boolean indexedBake;

    /**
     * How the material lets light through, which decides which faces it hides when baked and how it
     * is drawn.
     */
    public enum Transparency {
        /** Solid: hides whatever is behind it. */
        OPAQUE,
        /** Holes cut out by the texture's alpha, like leaves: hides nothing. */
        CUTOUT,
        /** See-through, like glass: hides nothing except the same material behind it. */
        TRANSLUCENT
    }

    /** How the material lets light through, opaque unless set. */
    private Transparency transparency;

    /**
     * Whether level of detail projects its texture along each triangle's main axis, one repeat per
     * unit, and shades triangles flat, instead of keeping the mesh's normals and texture
     * coordinates. Suits textures laid out on a unit grid, as block faces are, whose seams at every
     * face would otherwise keep every vertex from being simplified. Off, the default, keeps them,
     * as authored meshes need.
     */
    private boolean triplanar;

    /**
     * The material baked faces pointing up in the world are drawn with instead, such as a grass
     * block's top, or null to use this one. It must be in the material cache too.
     */
    private Material top;

    /**
     * The material baked faces pointing down in the world are drawn with instead, such as a grass
     * block's dirt bottom, or null to use this one. It must be in the material cache too.
     */
    private Material bottom;

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
        normalMap = null;
        texture = null;
        occlusionRoughnessMetal = null;
        emissiveMap = null;
        emissive = new Vector3f();
        normalGreenDown = false;
        transparency = Transparency.OPAQUE;
        top = null;
        bottom = null;
    }

    /**
     * The material for faces pointing up, this one unless there is a top variant.
     *
     * @return The material.
     */
    public Material forTop() {
        return top == null ? this : top;
    }

    /**
     * The material for faces pointing down, this one unless there is a bottom variant.
     *
     * @return The material.
     */
    public Material forBottom() {
        return bottom == null ? this : bottom;
    }
}
