#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Debug view of the g-buffer tangents, which are in view space. Each axis maps from [-1, 1] to
// [0, 1], so facing the camera is blue, right is red and up is green. Empty pixels are black.

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
    vec4 tangent = texelFetch(bindlessTextures[nonuniformEXT(tangentIndex)], ivec2(gl_FragCoord.xy), 0);
    // The scene writes 1 to w, which is cleared to 0 where nothing was drawn
    if (tangent.w == 0) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    color = vec4(tangent.xyz * 0.5 + 0.5, 1);
}
