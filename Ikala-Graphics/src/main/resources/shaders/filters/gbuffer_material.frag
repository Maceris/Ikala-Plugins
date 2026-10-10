#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Debug view of the g-buffer material indices, with a distinct color per material. Empty pixels
// are black.

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 color;

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
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];
layout(set = 1, binding = 0) uniform usampler2D bindlessUintTextures[];

// Spread neighboring indices out to unrelated colors
vec3 indexColor(uint index)
{
    uint hash = index * 2654435761u;
    hash ^= hash >> 16;
    hash *= 2246822519u;
    hash ^= hash >> 13;
    vec3 rgb = vec3((hash >> 16) & 0xFFu, (hash >> 8) & 0xFFu, hash & 0xFFu) / 255.0;
    // Keep away from black, so it isn't mistaken for an empty pixel
    return mix(vec3(0.2), vec3(1), rgb);
}

void main()
{
    if (hasGBuffer == 0) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    // The material clears to the default material, so use depth to find empty pixels
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], pixel, 0).x;
    if (depth == 1) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    uint material = texelFetch(bindlessUintTextures[nonuniformEXT(materialIndex)], pixel, 0).r;
    color = vec4(indexColor(material), 1);
}
