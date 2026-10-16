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
};

// A tangent space normal from a normal map texel: x and y from red and green, z rebuilt, so two
// channel (BC5) maps read the same as three channel ones.
vec3 normalFromMap(vec4 texel)
{
    vec2 xy = texel.xy * 2.0 - 1.0;
    return vec3(xy, sqrt(max(0.0, 1.0 - dot(xy, xy))));
}

#endif
