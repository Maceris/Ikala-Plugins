#ifndef FOG_GLSL
#define FOG_GLSL

// The fog volume: froxels of FOG_TILE pixel tiles across the screen by the transparency volume's
// SLICES slices in depth, see oit_volume.glsl. fog_inject.comp fills each froxel with the fog's
// light scattered toward the camera per meter and its extinction, and fog_integrate.comp sums them
// front to back. FogMath has the math in Java, with tests; keep them in step.
//
// Define FOG_SET before including this to read the integrated fog.

#include "oit_volume.glsl"

// Pixels along each side of a froxel's tile, see FogVolume
const uint FOG_TILE = 16;

// How far in front of the camera a slice boundary is, the inverse of sliceCoordinate
float sliceDepth(float slice, float logScale) {
    return FIRST_DEPTH * exp(slice / logScale);
}

// How much of a shape's density a point gets by how far inside it is: none outside, rising smoothly
// to all of it once the edge's width inside, see FogMath.edgeWeight
float edgeWeight(float signedDistance, float edge) {
    if (edge <= 0.0) {
        return signedDistance <= 0.0 ? 1.0 : 0.0;
    }
    float t = clamp(-signedDistance / edge, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// The Henyey-Greenstein phase function, per steradian, see FogMath.henyeyGreenstein
float henyeyGreenstein(float cosTheta, float g) {
    float gg = g * g;
    float denominator = 1.0 + gg - 2.0 * g * cosTheta;
    return (1.0 - gg) / (4.0 * 3.14159265359 * denominator * sqrt(denominator));
}

// Scattering orders followed in multipleScattering, and how each octave changes from the one
// before, see FogMath
const int SCATTER_OCTAVES = 4;
const float SCATTER_ATTENUATION = 0.5;
const float SCATTER_CONTRIBUTION = 0.5;
const float SCATTER_ECCENTRICITY = 0.5;

// The sun's light scattered toward the camera per unit of scattering and sunlight, counting light
// scattered more than once, after Wrenninge's octaves, see FogMath.multipleScattering
float multipleScattering(float opticalDepth, float thickness, float cosTheta, float g, float albedo) {
    float scattered = albedo * (1.0 - exp(-thickness));
    float attenuation = 1.0;
    float contribution = 1.0;
    float eccentricity = 1.0;
    float total = 0.0;
    for (int octave = 0; octave < SCATTER_OCTAVES; ++octave) {
        total += contribution * henyeyGreenstein(cosTheta, g * eccentricity)
            * exp(-attenuation * opticalDepth);
        attenuation *= SCATTER_ATTENUATION;
        contribution *= SCATTER_CONTRIBUTION * scattered;
        eccentricity *= SCATTER_ECCENTRICITY;
    }
    return total;
}

// How much of the sky's light reaches a point under an optical depth of shelter, counting light
// scattered more than once, see FogMath.ambientScattering
float ambientScattering(float opticalDepth, float thickness, float albedo) {
    float scattered = albedo * (1.0 - exp(-thickness));
    float attenuation = 1.0;
    float contribution = 1.0;
    float weights = 0.0;
    float total = 0.0;
    for (int octave = 0; octave < SCATTER_OCTAVES; ++octave) {
        total += contribution * exp(-attenuation * opticalDepth);
        weights += contribution;
        attenuation *= SCATTER_ATTENUATION;
        contribution *= SCATTER_CONTRIBUTION * scattered;
    }
    return total / weights;
}

#ifdef FOG_SET
// Each froxel's light scattered toward the camera in front of its far end, and how much of each
// color gets through to it
layout(set = FOG_SET, binding = 0) uniform sampler3D fogVolume;
layout(set = FOG_SET, binding = 1) uniform sampler3D fogTransmittance;

// What the fog in front of a point does: the light it scatters toward the camera, and how much of
// each color of the point's own light gets through
struct FogSeen {
    vec3 light;
    vec3 through;
};

// The fog in front of a point at a pixel, depth meters in front of the camera. Blended between
// froxels, and from nothing in the way at the camera to the first slice's far end.
FogSeen fogInFront(vec2 fragCoord, float depth, float logScale) {
    vec3 size = vec3(textureSize(fogVolume, 0));
    vec2 tile = fragCoord / float(FOG_TILE);
    float slice = sliceCoordinate(depth, logScale);
    // Each texel holds the fog up to its slice's far end, its center half a slice before it
    vec3 at = vec3(tile / size.xy, (max(slice, 1.0) - 0.5) / size.z);
    FogSeen seen;
    seen.light = texture(fogVolume, at).rgb;
    seen.through = texture(fogTransmittance, at).rgb;
    if (slice < 1.0) {
        seen.light *= slice;
        seen.through = mix(vec3(1.0), seen.through, slice);
    }
    return seen;
}

// All the fog in the volume at a pixel, out to the view distance, which the sky is seen through
FogSeen fogBeyond(vec2 fragCoord) {
    vec3 size = vec3(textureSize(fogVolume, 0));
    vec3 at = vec3(fragCoord / float(FOG_TILE) / size.xy, 1.0 - 0.5 / size.z);
    FogSeen seen;
    seen.light = texture(fogVolume, at).rgb;
    seen.through = texture(fogTransmittance, at).rgb;
    return seen;
}

// A point's light seen through the fog in front of it
vec3 fogApply(vec3 color, vec2 fragCoord, float depth, float logScale) {
    FogSeen seen = fogInFront(fragCoord, depth, logScale);
    return color * seen.through + seen.light;
}
#endif

#endif
