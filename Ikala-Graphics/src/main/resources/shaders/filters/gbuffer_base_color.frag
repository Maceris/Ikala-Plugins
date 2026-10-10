#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Debug view of the g-buffer base color. Empty pixels and frames without a g-buffer are black.

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

void main()
{
    if (hasGBuffer == 0) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    vec4 baseColor = texelFetch(bindlessTextures[nonuniformEXT(baseColorIndex)], ivec2(gl_FragCoord.xy), 0);
    // Cleared to transparent black, and the scene discards anything less than half opaque
    color = vec4(baseColor.rgb, 1);
}
