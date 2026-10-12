#ifndef LIGHT_INPUTS_GLSL
#define LIGHT_INPUTS_GLSL

// Everything the lighting reads: the light stage's descriptor set (set 0, see LightRender and
// ShaderBindings.Light) and the bindless textures (set 1). The light stage and the transparent
// stage share both.

#include "clusters.glsl"
#include "light_struct.glsl"
#include "material.glsl"

const int NUM_CASCADES = 3;

struct AmbientLight
{
    vec3 color;
    float intensity;
};

struct DirectionalLight
{
    vec3 color;
	float _padding;
    vec3 direction;
    float intensity;
};

struct Fog
{
    vec3 color;
    float density;
    int enabled;
    vec3 _padding;
};

struct CascadeShadow {
    mat4 projViewMatrix;
    // The view space z of the far end of the cascade's slice, so negative
    float splitDistance;
    // How wide one texel of the cascade's shadow map is, in meters
    float texelSize;
    // The bindless index of the cascade's map of what moves, at the same place as its shadow map,
    // or -1 for none, see SunShadowCache
    int dynamicMapIndex;
};

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 invProjectionMatrix;
    mat4 invViewMatrix;

    AmbientLight ambientLight;
    DirectionalLight directionalLight;
    int lightCount;
    // Nonzero to tint each pixel by how many lights its cluster lists
    int clusterHeatMap;
    Fog fog;
    CascadeShadow cascadeShadowSplits[NUM_CASCADES];

    int baseColorSamplerIndex;
    int normalSamplerIndex;
    int tangentSamplerIndex;
    int materialSamplerIndex;
    int depthSamplerIndex;
    int shadowMap0Index;
    int shadowMap1Index;
    int shadowMap2Index;
    // (CLUSTERS_Z - 1) / ln(viewDistance / FIRST_SLICE_DEPTH)
    float clusterLogScale;

    // The sky occlusion map, see SkyOcclusion: from render space to the map, which looks straight
    // down, its bindless index or -1 for none, how far down it reaches in meters, the least sky
    // light a covered point gets, and nonzero to show sky light in gray
    mat4 skyProjViewMatrix;
    int skyMapIndex;
    float skyDepthRange;
    float skyLightFloor;
    int skyOcclusionView;
};

layout(std430, set = 0, binding = 1) readonly buffer Lights {
    Light lights[];
};

layout(std430, set = 0, binding = 2) readonly buffer Materials {
    Material materials[];
};

// How many lights each cluster lists, CLUSTER_COUNT of them, then MAX_LIGHTS_PER_CLUSTER light
// indices for each cluster
layout(std430, set = 0, binding = 3) readonly buffer Clusters {
    uint clusterData[];
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];
// The same array, for reading integer textures like the material indices
layout(set = 1, binding = 0) uniform usampler2D bindlessUintTextures[];

#endif
