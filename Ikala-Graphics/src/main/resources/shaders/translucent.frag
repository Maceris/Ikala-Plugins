#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Lights a translucent surface the same way the light stage lights the g-buffer, and adds it to the
// order independent transparency targets, in any order. OitMath and VoxelOitMath have the same
// math in Java.
//
// accum: the lit color times its opacity times its weight, and the opacity times its weight, so
// the resolve can take the weighted average of every layer. The weight is how much light gets
// through to just in front of the surface, read from the voxel-based transparency volume, or
// McGuire and Bavoil's depth weight to compare against.
// extinction: -log(1 - opacity), whose sum gives exactly how much of the background shows through,
// whatever the order

#include "lighting.glsl"
#define OIT_VOLUME_SET 3
#include "oit_volume.glsl"

layout(location = 0) in vec3 outNormal;
layout(location = 1) in vec3 outTangent;
layout(location = 2) in vec3 outBitangent;
layout(location = 3) in vec2 outTextCoord;
layout(location = 4) in vec4 outViewPosition;
// Relative to the camera, which is render space
layout(location = 5) in vec4 outWorldPosition;
layout(location = 6) flat in uint outMaterialIdx;

layout(push_constant) uniform Constants {
    // The size of the area drawn, in pixels, to find each pixel's light cluster
    vec2 screenSize;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
    // Nonzero to weight by the transparency volume, zero for the depth weights
    int voxelWeights;
    // How many tiles across and down this frame's volume holds
    vec2 tiles;
};

layout(location = 0) out vec4 accum;
layout(location = 1) out float extinction;

// Opacity is kept below 1, so the extinction stays finite
const float MAX_OPACITY = 0.999;

// Weighted blended OIT's depth weight, McGuire and Bavoil 2013, equation 10: nearer layers count
// for more in the average. Matches OitMath.weight.
float depthWeight(float alpha, float depth) {
    return alpha * clamp(10.0 / (1e-5 + pow(depth / 5.0, 2.0) + pow(depth / 200.0, 6.0)), 1e-2, 3e3);
}

void main() {
    Material material = materials[outMaterialIdx];
    vec4 baseColor = material.baseColor;
    if (material.textureIndex > 0) {
        baseColor = texture(bindlessTextures[nonuniformEXT(material.textureIndex)], outTextCoord);
    }
    float alpha = min(baseColor.a, MAX_OPACITY);
    if (alpha <= 0.0) {
        discard;
    }

    vec3 normal = normalize(outNormal);
    if (material.normalMapIndex > 0) {
        mat3 TBN = mat3(outTangent, outBitangent, outNormal);
        vec3 newNormal = texture(bindlessTextures[nonuniformEXT(material.normalMapIndex)], outTextCoord).rgb;
        normal = normalize(TBN * (newNormal * 2.0 - 1.0));
    }
    // Both sides of a pane are drawn, and the back is lit from behind
    if (!gl_FrontFacing) {
        normal = -normal;
    }
    vec3 tangent = normalize(outTangent);
    vec3 bitangent = cross(normal, tangent);

    uint clusterLights;
    vec3 color = lightSurface(baseColor, material, outViewPosition.xyz, outWorldPosition.xyz, normal,
        tangent, bitangent, gl_FragCoord.xy / screenSize, clusterLights);

    float depth = -outViewPosition.z;
    float w = voxelWeights != 0
        ? transmittanceInFront(gl_FragCoord.xy, depth, logScale, tiles)
        : depthWeight(alpha, depth);
    accum = vec4(color * alpha * w, alpha * w);
    extinction = -log(1.0 - alpha);
}
