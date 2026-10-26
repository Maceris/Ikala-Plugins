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
    WaveClimateList climateList;
    WaveProfileList profileList;
    // The drawn area, in pixels
    vec2 screenSize;
    // The scene's color and depth behind the water, bindless
    int behindColorIndex;
    int behindDepthIndex;
    // A calm surface's roughness, for the sun's highlight and the sky's reflection
    float roughness;
    // How wide a pixel is seen from the camera, in radians
    float pixelAngle;
};

layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) in vec4 outViewPosition;
layout(location = 2) flat in uint outTile;
// How much of the waves the depth allows here
layout(location = 3) in float outCalm;

layout(location = 0) out vec4 outColor;

// How deep the water is taken to be where nothing lies behind it, in meters
const float DEEP = 1000.0;
// The share of the sky's light a calm surface reflects, averaged over the sky; the rest gets in
const float SKY_REFLECTANCE = 0.066;
// The least cosine to the normal anything gets in a GGX term, so grazing light doesn't blow up
const float MIN_COSINE = 1e-3;
// The least cosine a pixel's footprint is stretched by, seen at a grazing angle
const float MIN_FOOTPRINT_COSINE = 0.2;
// How far the bottom shifts across the screen per unit of the surface's tilt, as a share of the
// screen's height, per meter of water down to 2 m, over the distance
const float REFRACTION_SHIFT = 0.25;
const float REFRACTION_MAX_DEPTH = 2.0;

void main() {
    WaterTileData tile = tileList.waterTiles[outTile];
    vec3 absorption = tile.absorption.rgb;
    vec3 scattering = tile.scattering.rgb;
    vec3 extinction = absorption + scattering;
    vec3 backscattering = tile.backscattering.rgb;

    // Render space: world axes, the camera at the origin
    const vec3 up = vec3(0.0, 1.0, 0.0);
    vec3 normal = up;
    vec3 toEye = normalize(-outRenderPosition);
    float alpha = max(roughness * roughness, 1e-4);

    // The waves: their slope from the layer whose shortest waves this pixel can still show, and
    // the slopes of those it can't as roughness
    int climate = int(tile.offset.w);
    if (climate >= 0 && outCalm > 0.0) {
        float footprint = length(outRenderPosition) * pixelAngle
            / max(dot(up, toEye), MIN_FOOTPRINT_COSINE);
        float layer = waveLayer(footprint);
        int lower = int(floor(layer));
        int upper = min(lower + 1, WAVE_LAYERS - 1);
        float blend = layer - float(lower);
        vec2 slope = mix(
            waveSlope(climateList, profileList, climate, lower, outRenderPosition.xz),
            waveSlope(climateList, profileList, climate, upper, outRenderPosition.xz), blend)
            * outCalm;
        vec4 missing = climateList.climates[climate].missingVariance;
        float variance = mix(missing[lower], missing[upper], blend) * outCalm * outCalm;
        normal = normalize(vec3(-slope.x, 1.0, -slope.y));
        alpha = sqrt(alpha * alpha + variance);
    }
    float surfaceRoughness = sqrt(alpha);
    float cosView = max(dot(normal, toEye), MIN_COSINE);
    float reflected = waterFresnel(cosView);

    // The sun
    vec3 toSun = normalize(mat3(invViewMatrix) * -directionalLight.direction);
    float cosSun = dot(normal, toSun);
    vec3 sun = vec3(0.0);
    if (cosSun > 0.0) {
        sun = directionalLight.color * directionalLight.intensity
            * sunVisibility(outViewPosition.xyz, outRenderPosition, up);
    }

    // The sky: reflected along the mirror direction, and its average lighting the water from above
    float sky = mix(skyLightFloor, 1.0, skyVisibility(outRenderPosition, up));
    vec3 ambientColor = ambientLight.intensity * ambientLight.color;
    vec3 skyReflected = ambientColor;
    vec3 skyAverage = ambientColor;
    if (environmentEnabled != 0) {
        vec3 scale = environmentScale.w > 0.5
            ? environmentScale.rgb
            : ambientColor / max(dot(environmentAverage(), ENVIRONMENT_LUMINANCE), 1e-4);
        vec3 mirrored = reflect(-toEye, normal);
        // Waves tilted far enough mirror the water, not the sky: keep to the sky
        mirrored.y = max(mirrored.y, 0.0);
        skyReflected = environmentSpecular(normalize(mirrored), surfaceRoughness) * scale;
        skyAverage = environmentAverage() * scale;
    }
    skyReflected *= sky;
    skyAverage *= sky;

    // The sun's highlight, GGX
    vec3 highlight = vec3(0.0);
    if (cosSun > 0.0) {
        vec3 halfway = normalize(toSun + toEye);
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

    // The bottom: what lies behind the water, shifted as the tilted surface bends the view, unless
    // that would show something in front of the water
    float depth = -outViewPosition.z;
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec2 tilt = (transpose(mat3(invViewMatrix)) * vec3(normal.x, 0.0, normal.z)).xy;
    float straightBehind =
        texelFetch(bindlessTextures[nonuniformEXT(behindDepthIndex)], pixel, 0).x;
    float shiftDepth = straightBehind < 1.0 ? REFRACTION_MAX_DEPTH : 0.0;
    vec2 shift = vec2(tilt.x, -tilt.y) * screenSize.y * REFRACTION_SHIFT * shiftDepth
        / max(depth, 1.0);
    ivec2 shifted = clamp(ivec2(gl_FragCoord.xy + shift), ivec2(0), ivec2(screenSize) - 1);
    float shiftedBehind =
        texelFetch(bindlessTextures[nonuniformEXT(behindDepthIndex)], shifted, 0).x;
    if (shiftedBehind >= gl_FragCoord.z) {
        pixel = shifted;
    }
    float behindDepth = texelFetch(bindlessTextures[nonuniformEXT(behindDepthIndex)], pixel, 0).x;
    vec3 bottom = vec3(0.0);
    float depthBelow = DEEP;
    if (behindDepth < 1.0) {
        vec2 uv = (vec2(pixel) + 0.5) / screenSize;
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

    if ((fog.enabled & FOG_ENABLED) != 0) {
        color = fogApply(color, gl_FragCoord.xy, depth, fog.logScale);
    }
    outColor = vec4(color, 1.0);
}
