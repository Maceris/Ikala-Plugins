#ifndef PARTICLE_FRAGMENT_GLSL
#define PARTICLE_FRAGMENT_GLSL

// What the particle billboards' fragment shaders share: their inputs and how opaque a billboard is
// at a fragment, round and faded where it nears the opaque scene. Include after light_inputs.glsl.

#include "particle_draw.glsl"

layout(location = PARTICLE_VARYING_CORNER) in vec2 outCorner;
layout(location = PARTICLE_VARYING_COLOR) in vec4 outColor;
layout(location = PARTICLE_VARYING_VIEW) in vec4 outViewPosition;
layout(location = PARTICLE_VARYING_SIZE) in vec2 outSizeEmissive;

// Opacity is kept below 1, so the extinction stays finite. Matches OitMath.MAX_OPACITY.
const float PARTICLE_MAX_OPACITY = 0.999;

// How far in front of the camera the opaque scene is at this fragment's pixel, in meters along the
// view, or far away where only the sky is
float sceneDepthHere() {
    vec2 pixelCenter = min(gl_FragCoord.xy * pixelScale, screenSize - 0.5);
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)],
        ivec2(pixelCenter), 0).x;
    if (depth >= 1.0) {
        return 1e30;
    }
    vec2 uv = pixelCenter / screenSize;
    vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, depth, 1.0);
    return -view.z / view.w;
}

// The billboard's opacity at this fragment before it fades near the opaque scene: its color's
// opacity over its round shape. Cheap, so fragments in the billboard's clear corners stop here,
// before reading the scene's depth.
float particleShapeAlpha() {
    return outColor.a * roundShape(outCorner);
}

// The billboard's opacity at this fragment, from its shape's, faded near the opaque scene
float particleAlpha(float shapeAlpha) {
    float fade = softFade(-outViewPosition.z, sceneDepthHere(), outSizeEmissive.x * softScale);
    return min(shapeAlpha * fade, PARTICLE_MAX_OPACITY);
}

#endif
