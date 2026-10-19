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
    // The material's average metalness, see average
    float averageMetallic;
    // What the material looks like averaged over its surface, as far stand-ins draw it: its
    // texture's average, worked out on the CPU, else its base color, in linear light, and in alpha
    // its roughness, its packed texture's average if it has one
    vec4 average;
};

// The normal map's green channel points down the texture, as in DirectX
const int MATERIAL_FLAG_NORMAL_GREEN_DOWN = 1;
// The packed occlusion, roughness and metal map's red channel is occlusion; some leave it unused
const int MATERIAL_FLAG_PACKED_OCCLUSION = 2;
// The normal map is baked data: normal x and y in red and green, roughness in blue, metalness in
// alpha, standing in for the material's, as level of detail bakes write it
const int MATERIAL_FLAG_BAKED_DATA = 4;
// With MATERIAL_FLAG_BAKED_DATA, a model's bake page: the color texture names a material and a
// shade per texel (red the shade over MAX_BAKE_SHADE, green and blue the material's index), read
// without filtering, and the data texture's blue is how much the baked normals agree, which widens
// the named material's roughness
const int MATERIAL_FLAG_INDEXED_BAKE = 8;
// The most an indexed bake texel's shade reaches, see Attributes.MAX_SHADE
const float MAX_BAKE_SHADE = 2.0;

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
