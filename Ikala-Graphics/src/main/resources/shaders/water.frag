#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A water surface, see WaterRender, drawn over the lit scene and the sky with a copy of them, and of
// their depth, behind it. What leaves the surface toward the eye: the sky and the sun reflected by
// the Fresnel share, and the rest from under it: the bottom, dimmed by the water along the view and
// along the light's way down to it, and the light coming down that the water column sends back up,
// in closed form for a uniform column (see WaterMath.columnReflectance), spread evenly over the
// upward directions. Light leaving the water loses n^2 of its radiance as its rays spread out
// through the surface. All seen through the fog in front.

#define ENVIRONMENT_SET 3
#include "lighting.glsl"
#define FOG_SET 4
#include "fog.glsl"
#include "water.glsl"

layout(push_constant) uniform Constants {
    WaterTileList tileList;
    // The drawn area, in pixels
    vec2 screenSize;
    // The scene's color and depth behind the water, bindless
    int behindColorIndex;
    int behindDepthIndex;
    // The surface's roughness, for the sun's highlight and the sky's reflection
    float roughness;
};

layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) in vec4 outViewPosition;
layout(location = 2) flat in uint outTile;

layout(location = 0) out vec4 outColor;

// How deep the water is taken to be where nothing lies behind it, in meters
const float DEEP = 1000.0;
// The share of the sky's light a calm surface reflects, averaged over the sky; the rest gets in
const float SKY_REFLECTANCE = 0.066;
// The least cosine to the normal anything gets in a GGX term, so grazing light doesn't blow up
const float MIN_COSINE = 1e-3;

void main() {
    WaterTileData tile = tileList.waterTiles[outTile];
    vec3 absorption = tile.absorption.rgb;
    vec3 scattering = tile.scattering.rgb;
    vec3 extinction = absorption + scattering;
    vec3 backscattering = tile.backscattering.rgb;

    // Render space: world axes, the camera at the origin
    vec3 normal = vec3(0.0, 1.0, 0.0);
    vec3 toEye = normalize(-outRenderPosition);
    float cosView = max(dot(normal, toEye), MIN_COSINE);
    float reflected = waterFresnel(cosView);

    // The sun
    vec3 toSun = normalize(mat3(invViewMatrix) * -directionalLight.direction);
    float cosSun = dot(normal, toSun);
    vec3 sun = vec3(0.0);
    if (cosSun > 0.0) {
        sun = directionalLight.color * directionalLight.intensity
            * sunVisibility(outViewPosition.xyz, outRenderPosition, normal);
    }

    // The sky: reflected along the mirror direction, and its average lighting the water from above
    float sky = mix(skyLightFloor, 1.0, skyVisibility(outRenderPosition, normal));
    vec3 ambientColor = ambientLight.intensity * ambientLight.color;
    vec3 skyReflected = ambientColor;
    vec3 skyAverage = ambientColor;
    if (environmentEnabled != 0) {
        vec3 scale = environmentScale.w > 0.5
            ? environmentScale.rgb
            : ambientColor / max(dot(environmentAverage(), ENVIRONMENT_LUMINANCE), 1e-4);
        skyReflected = environmentSpecular(reflect(-toEye, normal), roughness) * scale;
        skyAverage = environmentAverage() * scale;
    }
    skyReflected *= sky;
    skyAverage *= sky;

    // The sun's highlight, GGX
    vec3 highlight = vec3(0.0);
    if (cosSun > 0.0) {
        vec3 halfway = normalize(toSun + toEye);
        float alpha = max(roughness * roughness, 1e-4);
        float cosHalf = max(dot(normal, halfway), 0.0);
        float d = alpha * alpha / (3.14159265 * sqr(cosHalf * cosHalf * (alpha * alpha - 1.0) + 1.0));
        // Smith's separable masking and shadowing, with the 4 n.l n.v already divided out, as
        // the Disney BRDF has it
        float visibility = smithGGX(cosView, alpha) * smithGGX(max(cosSun, MIN_COSINE), alpha);
        highlight = sun * d * visibility * waterFresnel(dot(halfway, toEye)) * cosSun;
    }

    // Under the surface: the view's and the sun's rays bent into the water
    float muView = waterRefractedCosine(cosView);
    float muSun = waterRefractedCosine(max(cosSun, 0.0));

    // The bottom: what lies behind the water at this pixel, and how far below the surface
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float behindDepth = texelFetch(bindlessTextures[nonuniformEXT(behindDepthIndex)], pixel, 0).x;
    vec3 bottom = vec3(0.0);
    float depthBelow = DEEP;
    if (behindDepth < 1.0) {
        vec2 uv = gl_FragCoord.xy / screenSize;
        vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, behindDepth, 1.0);
        view /= view.w;
        vec3 render = mat3(invViewMatrix) * view.xyz;
        depthBelow = max(outRenderPosition.y - render.y, 0.0);
        bottom = texelFetch(bindlessTextures[nonuniformEXT(behindColorIndex)], pixel, 0).rgb;
        // The light stage fogged it as if the air reached all the way: take that fog back off
        if ((fog.enabled & FOG_ENABLED) != 0) {
            vec4 fogged = fogInFront(gl_FragCoord.xy, -view.z, fog.logScale);
            bottom = max(bottom - fogged.rgb, vec3(0.0)) / max(fogged.a, 1e-3);
        }
    }
    float distance = depthBelow / muView;
    // Lit through the water above it, and seen through the water in front of it
    bottom *= exp(-extinction * (depthBelow / muSun + distance));

    // The light coming down into the water, from the sun and the sky, on a level surface
    vec3 downwelling = sun * (1.0 - waterFresnel(cosSun)) * max(cosSun, 0.0)
        + 3.14159265 * skyAverage * (1.0 - SKY_REFLECTANCE);
    // What the column sends back up, evenly over the upward directions
    vec3 column = downwelling / 3.14159265
        * waterColumnReflectance(absorption, backscattering, depthBelow / muSun, distance);

    vec3 below = (bottom + column) / (WATER_INDEX * WATER_INDEX);
    vec3 color = reflected * skyReflected + highlight + (1.0 - reflected) * below;

    float depth = -outViewPosition.z;
    if ((fog.enabled & FOG_ENABLED) != 0) {
        color = fogApply(color, gl_FragCoord.xy, depth, fog.logScale);
    }
    outColor = vec4(color, 1.0);
}
