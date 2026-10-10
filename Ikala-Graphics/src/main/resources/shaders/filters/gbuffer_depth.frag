#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Debug view of the g-buffer depth, as distance from the camera on a log scale so both close and
// distant detail are visible. The camera is white, fading to black at the far plane, and empty
// pixels are black.

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

// The view space distance for a depth value
float viewDistance(float depth)
{
    vec4 view = inverseProjectionMatrix * vec4(0, 0, depth, 1);
    return -view.z / view.w;
}

void main()
{
    if (hasGBuffer == 0) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], ivec2(gl_FragCoord.xy), 0).x;
    if (depth == 1) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    // Depth is [0, 1] from the near to the far plane. Scaling from the near plane would spend most
    // of the range on the first few units, so count from 1 unit out instead.
    float far = viewDistance(1);
    float distance = clamp(viewDistance(depth), 0, far);
    float brightness = 1 - log(1 + distance) / log(1 + far);
    color = vec4(vec3(brightness), 1);
}
