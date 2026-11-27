#ifndef FILTER_SURFACE_GLSL
#define FILTER_SURFACE_GLSL

// The surface a filter debug view shows at a pixel: the g-buffer's material, with the roughness and
// metalness a texture or an averaged level of detail surface put in place of its own, as the
// lighting pass reads them, see lights.frag. Includers enable GL_EXT_buffer_reference,
// GL_EXT_buffer_reference_uvec2 and GL_EXT_nonuniform_qualifier.

#include "material.glsl"

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(set = 0, binding = 1) uniform FilterUniforms {
    mat4 projectionMatrix;
    mat4 inverseProjectionMatrix;
    mat4 viewMatrix;
    mat4 inverseViewMatrix;
    vec2 screenSize;
    vec2 uvScale;
    int hasGBuffer;
    int baseColorIndex;
    int normalIndex;
    int tangentIndex;
    int materialIndex;
    int depthIndex;
    uvec2 materialsAddress;
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];
layout(set = 1, binding = 0) uniform usampler2D bindlessUintTextures[];

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer MaterialTable {
    Material materials[];
};

// The material at a pixel, with its roughness and metallic as lit; false for an empty pixel or a
// frame without a g-buffer
bool surfaceAt(ivec2 pixel, out Material material)
{
    if (hasGBuffer == 0) {
        return false;
    }
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], pixel, 0).x;
    if (depth == 1) {
        return false;
    }
    uint index = texelFetch(bindlessUintTextures[nonuniformEXT(materialIndex)], pixel, 0).r;
    material = MaterialTable(materialsAddress).materials[index];
    float override = texelFetch(bindlessTextures[nonuniformEXT(normalIndex)], pixel, 0).w;
    if (override >= 0.0) {
        material.roughness = override;
        material.metallic = texelFetch(bindlessTextures[nonuniformEXT(tangentIndex)], pixel, 0).w;
    }
    return true;
}

#endif
