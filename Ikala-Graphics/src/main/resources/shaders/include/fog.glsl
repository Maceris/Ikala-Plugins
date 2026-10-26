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

#ifdef FOG_SET
// Each froxel's light scattered toward the camera in front of its far end (rgb) and how much light
// gets through to it (a)
layout(set = FOG_SET, binding = 0) uniform sampler3D fogVolume;

// The fog in front of a point at a pixel, depth meters in front of the camera: the light it
// scatters toward the camera (rgb) and how much of the point's own light gets through (a). Blended
// between froxels, and from nothing in the way at the camera to the first slice's far end.
vec4 fogInFront(vec2 fragCoord, float depth, float logScale) {
    vec3 size = vec3(textureSize(fogVolume, 0));
    vec2 tile = fragCoord / float(FOG_TILE);
    float slice = sliceCoordinate(depth, logScale);
    // Each texel holds the fog up to its slice's far end, its center half a slice before it
    vec4 seen = texture(fogVolume, vec3(tile / size.xy, (max(slice, 1.0) - 0.5) / size.z));
    return slice < 1.0 ? mix(vec4(0.0, 0.0, 0.0, 1.0), seen, slice) : seen;
}

// All the fog in the volume at a pixel, out to the view distance, which the sky is seen through
vec4 fogBeyond(vec2 fragCoord) {
    vec3 size = vec3(textureSize(fogVolume, 0));
    return texture(fogVolume, vec3(fragCoord / float(FOG_TILE) / size.xy, 1.0 - 0.5 / size.z));
}

// A point's light seen through the fog in front of it
vec3 fogApply(vec3 color, vec2 fragCoord, float depth, float logScale) {
    vec4 seen = fogInFront(fragCoord, depth, logScale);
    return color * seen.a + seen.rgb;
}
#endif

#endif
