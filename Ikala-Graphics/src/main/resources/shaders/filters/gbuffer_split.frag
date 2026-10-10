#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Debug view that splits the screen into equal vertical strips, showing the g-buffer base color,
// normals, tangents, material indices and depth from left to right. Each strip draws its texture
// like the matching single texture view does. Empty pixels are black.

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

// The number of strips
const int STRIPS = 5;

// Spread neighboring indices out to unrelated colors, see gbuffer_material.frag
vec3 indexColor(uint index)
{
    uint hash = index * 2654435761u;
    hash ^= hash >> 16;
    hash *= 2246822519u;
    hash ^= hash >> 13;
    vec3 rgb = vec3((hash >> 16) & 0xFFu, (hash >> 8) & 0xFFu, hash & 0xFFu) / 255.0;
    return mix(vec3(0.2), vec3(1), rgb);
}

// The view space distance for a depth value
float viewDistance(float depth)
{
    vec4 view = inverseProjectionMatrix * vec4(0, 0, depth, 1);
    return -view.z / view.w;
}

void main()
{
    int strip = clamp(int(gl_FragCoord.x * STRIPS / screenSize.x), 0, STRIPS - 1);
    // A thin divider on the left edge of each strip after the first
    if (strip > 0 && gl_FragCoord.x - strip * screenSize.x / STRIPS < 2) {
        color = vec4(0.5, 0.5, 0.5, 1);
        return;
    }
    if (hasGBuffer == 0) {
        color = vec4(0, 0, 0, 1);
        return;
    }
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], pixel, 0).x;
    if (depth == 1) {
        color = vec4(0, 0, 0, 1);
        return;
    }

    vec3 rgb;
    if (strip == 0) {
        rgb = texelFetch(bindlessTextures[nonuniformEXT(baseColorIndex)], pixel, 0).rgb;
    } else if (strip == 1) {
        rgb = texelFetch(bindlessTextures[nonuniformEXT(normalIndex)], pixel, 0).xyz * 0.5 + 0.5;
    } else if (strip == 2) {
        rgb = texelFetch(bindlessTextures[nonuniformEXT(tangentIndex)], pixel, 0).xyz * 0.5 + 0.5;
    } else if (strip == 3) {
        rgb = indexColor(texelFetch(bindlessUintTextures[nonuniformEXT(materialIndex)], pixel, 0).r);
    } else {
        float far = viewDistance(1);
        float distance = clamp(viewDistance(depth), 0, far);
        rgb = vec3(1 - log(1 + distance) / log(1 + far));
    }
    color = vec4(rgb, 1);
}
