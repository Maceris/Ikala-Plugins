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

#endif
