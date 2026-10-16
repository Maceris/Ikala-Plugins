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

// Light given off as level of detail bakes store it, in an RGBA8 texel: the color over a scale in
// red, green and blue, with the scale's log2, from EMISSION_LOG_MIN over EMISSION_LOG_RANGE
// stops, in alpha. The scale is rounded up, so the brightest channel keeps nearly all 8 bits.
const float EMISSION_LOG_MIN = -16.0;
const float EMISSION_LOG_RANGE = 32.0;

vec4 encodeEmission(vec3 emission)
{
    float brightest = max(max(emission.r, emission.g), emission.b);
    if (brightest <= exp2(EMISSION_LOG_MIN)) {
        return vec4(0.0);
    }
    float stops = (log2(brightest) - EMISSION_LOG_MIN) / EMISSION_LOG_RANGE;
    float alpha = clamp(ceil(stops * 255.0) / 255.0, 1.0 / 255.0, 1.0);
    float scale = exp2(alpha * EMISSION_LOG_RANGE + EMISSION_LOG_MIN);
    return vec4(min(emission / scale, vec3(1.0)), alpha);
}

vec3 decodeEmission(vec4 texel)
{
    return texel.a <= 0.0 ? vec3(0.0) : texel.rgb * exp2(texel.a * EMISSION_LOG_RANGE
        + EMISSION_LOG_MIN);
}

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
