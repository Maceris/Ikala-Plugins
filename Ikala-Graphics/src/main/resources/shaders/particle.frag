#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A particle's billboard in the transparent color pass, weighted and added up like translucent
// surfaces (see translucent.frag) and composited by the resolve. Its color comes lit, see
// particle.vert, and is seen through the fog in front of it. The cheap tests come first, so
// fragments that can't show stop before the scene's depth and the fog are read: the billboard's
// clear corners, and, weighted by the transparency volume, fragments behind so much that next to
// nothing of them gets through (Drobot's zero transmittance early out, done per fragment).

// The sets as the transparent stage binds them, see TranslucentRender
#define OCCUPANCY_SET 4
#define ENVIRONMENT_SET 5
#include "lighting.glsl"
#define OIT_VOLUME_SET 3
#include "oit_volume.glsl"
#define FOG_SET 6
#include "fog.glsl"
#include "particle_fragment.glsl"

layout(location = 0) out vec4 accum;
layout(location = 1) out vec2 extinction;

// Below this much light getting through from in front, a fragment adds nothing that shows
const float MIN_VISIBLE = 1.0 / 256.0;

// The depth weights of weighted blended transparency, as translucent.frag has them
float depthWeight(float alpha, float depth) {
    return alpha * clamp(10.0 / (1e-5 + pow(depth / 5.0, 2.0) + pow(depth / 200.0, 6.0)), 1e-2, 3e3);
}

void main() {
    float shapeAlpha = particleShapeAlpha();
    if (shapeAlpha <= 0.0) {
        discard;
    }
    float depth = -outViewPosition.z;
    float w = 1.0;
    if (voxelWeights != 0) {
        w = transmittanceInFront(gl_FragCoord.xy, depth, logScale, tiles);
        if (w < MIN_VISIBLE) {
            discard;
        }
    }
    float alpha = particleAlpha(shapeAlpha);
    if (alpha <= 0.0) {
        discard;
    }
    if (voxelWeights == 0) {
        w = depthWeight(alpha, depth);
    }
    vec3 color = outColor.rgb;
    if ((fog.enabled & FOG_ENABLED) != 0) {
        color = fogApply(color, gl_FragCoord.xy, depth, fog.logScale);
    }
    accum = vec4(color * alpha * w, alpha * w);
    // The anti-aliasing reads the second as how far to trust this frame, see particleReactive
    extinction = vec2(-log(1.0 - alpha), -log(1.0 - particleReactive(alpha)));
}
