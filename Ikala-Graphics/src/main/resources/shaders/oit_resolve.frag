#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Composites the transparent layers over the scene color: their weighted average color, covering
// as much of the background as their total extinction hides. Blended with (ONE, SRC_ALPHA), so the
// output alpha is how much of the background shows through. OitMath has the same math in Java.
//
// For debugging it can instead show one slice of the voxel-based transparency volume over the whole
// screen, as grey from black where nothing gets through to white where everything does.

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform Constants {
    int accumIndex;
    int extinctionIndex;
    // The slice of the transparency volume to show, or negative to composite as usual
    int debugSlice;
};

layout(set = 0, binding = 0) uniform sampler2D bindlessTextures[];
#define OIT_VOLUME_SET 1
#include "oit_volume.glsl"

void main()
{
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (debugSlice >= 0) {
        float through = texelFetch(transmittanceVolume, ivec3(pixel / int(TILE_SIZE), debugSlice), 0).r;
        fragColor = vec4(vec3(through), 0.0);
        return;
    }
    float extinction = texelFetch(bindlessTextures[nonuniformEXT(extinctionIndex)], pixel, 0).r;
    if (extinction <= 0.0) {
        discard;
    }
    vec4 accum = texelFetch(bindlessTextures[nonuniformEXT(accumIndex)], pixel, 0);
    float transmittance = exp(-extinction);
    vec3 average = accum.rgb / max(accum.a, 1e-5);
    fragColor = vec4(average * (1.0 - transmittance), transmittance);
}
