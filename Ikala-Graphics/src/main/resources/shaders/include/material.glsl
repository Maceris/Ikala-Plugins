#ifndef MATERIAL_GLSL
#define MATERIAL_GLSL

// See ShaderBindings.Scene.Material for the layout
struct Material
{
    vec4 baseColor;

    float anisotropic;
    float clearcoat;
    float clearcoatGloss;
    float metallic;

    float roughness;
    float sheen;
    float sheenTint;
    float specular;

    float specularTint;
    float subsurface;
    int normalMapIndex;
    int textureIndex;

    // Light given off, linear, times the emissive map if there is one
    vec3 emissive;
    // How much ambient light reaches the surface, 1 unless a pixel's occlusion says less
    float occlusion;
    // Packed occlusion (r), roughness (g) and metalness (b), linear, or 0 for none
    int occlusionRoughnessMetalIndex;
    int emissiveIndex;
    // See MATERIAL_FLAG_*
    int flags;
    int padding;
};

// The normal map's green channel points down the texture, as in DirectX
const int MATERIAL_FLAG_NORMAL_GREEN_DOWN = 1;
// The packed occlusion, roughness and metal map's red channel is occlusion; some leave it unused
const int MATERIAL_FLAG_PACKED_OCCLUSION = 2;
// The normal map is baked data: normal x and y in red and green, roughness in blue, metalness in
// alpha, standing in for the material's, as level of detail bakes write it
const int MATERIAL_FLAG_BAKED_DATA = 4;

// A tangent space normal from a normal map texel: x and y from red and green, z rebuilt, so two
// channel (BC5) maps read the same as three channel ones. Green flips for maps made DirectX's way.
vec3 normalFromMap(vec4 texel, Material material)
{
    vec2 xy = texel.xy * 2.0 - 1.0;
    if ((material.flags & MATERIAL_FLAG_NORMAL_GREEN_DOWN) != 0) {
        xy.y = -xy.y;
    }
    return vec3(xy, sqrt(max(0.0, 1.0 - dot(xy, xy))));
}

#endif
