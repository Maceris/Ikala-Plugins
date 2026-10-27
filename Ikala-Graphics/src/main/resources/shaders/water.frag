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
#include "water_constants.glsl"


layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) in vec4 outViewPosition;
layout(location = 2) flat in uint outTile;
// How much of the waves the depth and the edges allow here
layout(location = 3) in float outCalm;
// A side's outward normal along x and z, zero on the surface
layout(location = 4) in vec2 outSideNormal;
// Each direction's amplitudes here, from the vertices
layout(location = 5) in vec4 outAmplitudes[WAVE_AMPLITUDE_VECTORS];
// How fast the water flows here, and how deep it is
layout(location = 13) in vec2 outFlow;
layout(location = 14) in float outDepth;
// Where the surface is at rest, which the waves are worked out from
layout(location = 15) in vec2 outRestXZ;
// The share of the water the foam breaking crests left covers here, see water_wavelets.comp
layout(location = 16) in float outFoam;

layout(location = 0) out vec4 outColor;

// How deep the water is taken to be where nothing lies behind it, in meters
const float DEEP = 1000.0;
// The share of the sky's light a calm surface reflects, averaged over the sky; the rest gets in
const float SKY_REFLECTANCE = 0.066;
// The least cosine to the normal anything gets in a GGX term, so grazing light doesn't blow up
const float MIN_COSINE = 1e-3;
// The least cosine a pixel's footprint is stretched by, seen at a grazing angle
const float MIN_FOOTPRINT_COSINE = 0.2;
// How much of the light reaching it fresh, dense foam sends back, spread evenly: bright, short of
// snow; whitecaps average about 0.22 with old, thinning foam (Koepke 1984), dense foam reaches about
// 0.55 (Frouin et al. 2001)
const float FOAM_ALBEDO = 0.55;
// How big the patches of foam left behind are, in meters, and how sharp their edges, as a share of
// the pattern's range
const float FOAM_PATCH_SIZE = 0.6;
const float FOAM_PATCH_EDGE = 0.08;
// The least Jacobian foam is thickened by, where the surface squeezes it together
const float FOAM_MIN_SQUEEZE = 0.25;
// How far the bottom shifts across the screen per unit of the surface's tilt, as a share of the
// screen's height, per meter of water down to 2 m, over the distance
const float REFRACTION_SHIFT = 0.25;
const float REFRACTION_MAX_DEPTH = 2.0;

// A number from 0 to 1, advancing a seed (Jarzynski and Olano's PCG hash)
float waterRandom(inout uint seed) {
    seed = seed * 747796405u + 2891336453u;
    uint word = ((seed >> ((seed >> 28u) + 4u)) ^ seed) * 277803737u;
    return float(((word >> 22u) ^ word) >> 8) / 16777216.0;
}

// A smooth value noise from 0 to 1 over a plane, each lattice point's value from a PCG hash
float foamHash(ivec2 cell) {
    uint seed = uint(cell.x) * 1973u + uint(cell.y) * 9277u + 26699u;
    return waterRandom(seed);
}

float foamNoise(vec2 point) {
    ivec2 cell = ivec2(floor(point));
    vec2 f = fract(point);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(foamHash(cell), foamHash(cell + ivec2(1, 0)), f.x),
        mix(foamHash(cell + ivec2(0, 1)), foamHash(cell + ivec2(1, 1)), f.x), f.y);
}

// How much of a pixel the foam breaking crests left covers: patches over the share of the water the
// grid says, larger where the surface squeezes them, smaller where it stretches; a far pixel, too
// wide for the patches, just the share
float foamPatches(vec2 rest, float share, float squeeze, float footprint) {
    if (share <= 0.0) {
        return 0.0;
    }
    float thick = clamp(share / max(squeeze, FOAM_MIN_SQUEEZE), 0.0, 1.0);
    vec2 point = rest / FOAM_PATCH_SIZE;
    float pattern = 0.6 * foamNoise(point) + 0.4 * foamNoise(2.3 * point + 17.0);
    float patches = smoothstep(1.0 - thick - FOAM_PATCH_EDGE, 1.0 - thick + FOAM_PATCH_EDGE,
        pattern);
    return mix(patches, thick, clamp(footprint / FOAM_PATCH_SIZE, 0.0, 1.0));
}

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
    // How much of the pixel foam covers
    float foam = 0.0;
    float alpha = max(roughness * roughness, 1e-4);
    // A side, where the water ends: seen through its face, the way it faces the eye
    bool side = dot(outSideNormal, outSideNormal) > 0.0;
    if (side) {
        normal = vec3(outSideNormal.x, 0.0, outSideNormal.y);
        if (dot(normal, toEye) < 0.0) {
            normal = -normal;
        }
    }

    // The waves: their slope from the layer whose shortest waves this pixel can still show, and
    // the slopes of those it can't as roughness
    int climate = int(tile.offset.w);
    if ((climate >= 0 || gridOn != 0) && outCalm > 0.0 && !side) {
        WaveField field;
        field.climates = climateList;
        field.profiles = profileList;
        field.grid = gridList;
        field.climate = climate;
        field.ripples = rippleClimate;
        field.gridOn = gridOn != 0;
        // One depth's profiles a pixel, picked at random each frame by how much each counts, which
        // the anti-aliasing blends. The flow's two phases are blended here: their patterns are
        // meters apart, so picking one a pixel only leaves grain.
        uint seed = (uint(gl_FragCoord.x) * 1973u + uint(gl_FragCoord.y) * 9277u + frame * 26699u)
            | 1u;
        float level = waveDepthLevel(outDepth);
        field.level = min(int(floor(level)) + (waterRandom(seed) < fract(level) ? 1 : 0),
            WAVE_DEPTHS - 1);
        float phaseBlend = dot(outFlow, outFlow) > 0.0 ? waveFlowWeight(waveTime) : 0.0;
        float footprint = length(outRenderPosition) * pixelAngle
            / max(dot(up, toEye), MIN_FOOTPRINT_COSINE);
        float layer = waveLayer(footprint);
        int lower = int(floor(layer));
        int upper = min(lower + 1, WAVE_LAYERS - 1);
        float blend = layer - float(lower);
        vec2 amplitudes[WAVE_DIRECTIONS];
        for (int i = 0; i < WAVE_AMPLITUDE_VECTORS; ++i) {
            amplitudes[2 * i] = outAmplitudes[i].xy;
            amplitudes[2 * i + 1] = outAmplitudes[i].zw;
        }
        field.shift = waveFlowShift(outFlow, waveTime, 0);
        vec3 lowerSums;
        vec3 upperSums;
        vec3 slope = mix(waveSlope(field, amplitudes, lower, outRestXZ, lowerSums),
            waveSlope(field, amplitudes, upper, outRestXZ, upperSums), blend);
        vec3 sums = mix(lowerSums, upperSums, blend);
        if (phaseBlend > 0.0) {
            field.shift = waveFlowShift(outFlow, waveTime, 1);
            slope = mix(slope, mix(waveSlope(field, amplitudes, lower, outRestXZ, lowerSums),
                waveSlope(field, amplitudes, upper, outRestXZ, upperSums), blend), phaseBlend);
            sums = mix(sums, mix(lowerSums, upperSums, blend), phaseBlend);
        }
        slope *= outCalm;
        float variance = slope.z * outCalm;
        // The choppy surface's slope: the height's gradient at rest through the inverse of the
        // map's Jacobian, which steepens the squeezed crests and flattens the stretched troughs
        mat2 jacobian = waveJacobian(sums * outCalm);
        float squeeze = determinant(jacobian);
        vec2 gradient = slope.xy;
        if (squeeze > WAVE_MIN_JACOBIAN) {
            gradient = inverse(jacobian) * gradient;
        } else {
            gradient = mat2(jacobian[1][1], -jacobian[0][1], -jacobian[1][0], jacobian[0][0])
                * gradient / WAVE_MIN_JACOBIAN;
        }
        if (climate >= 0) {
            foam = waveFoam(squeeze, variance, climateList.climates[climate].peak.y);
        }
        float left = foamPatches(outRestXZ - field.shift, outFoam, squeeze, footprint);
        foam = 1.0 - (1.0 - foam) * (1.0 - left);
        normal = normalize(vec3(-gradient.x, 1.0, -gradient.y));
        alpha = sqrt(alpha * alpha + variance);
    }
    float surfaceRoughness = sqrt(alpha);
    float cosView = max(dot(normal, toEye), MIN_COSINE);
    float reflected = waterFresnel(cosView);

    // The sun
    vec3 toSun = normalize(mat3(invViewMatrix) * -directionalLight.direction);
    float cosSun = dot(normal, toSun);
    // The sun on the level water above, which lights what is under it
    float cosSunDown = dot(up, toSun);
    vec3 sun = vec3(0.0);
    if (max(cosSun, cosSunDown) > 0.0) {
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
    float muSun = waterRefractedCosine(max(cosSunDown, 0.0));

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
    // Through a side, how far the view goes in the water
    float across = DEEP;
    if (behindDepth < 1.0) {
        vec2 uv = (vec2(pixel) + 0.5) / screenSize;
        vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, behindDepth, 1.0);
        view /= view.w;
        vec3 render = mat3(invViewMatrix) * view.xyz;
        depthBelow = max(outRenderPosition.y - render.y, 0.0);
        across = length(render - outRenderPosition);
        bottom = texelFetch(bindlessTextures[nonuniformEXT(behindColorIndex)], pixel, 0).rgb;
        // The light stage fogged it as if the air reached all the way: take that fog back off
        if ((fog.enabled & FOG_ENABLED) != 0) {
            vec4 fogged = fogInFront(gl_FragCoord.xy, -view.z, fog.logScale);
            bottom = max(bottom - fogged.rgb, vec3(0.0)) / max(fogged.a, 1e-3);
        }
    }
    float distance = depthBelow / muView;
    // The light's way down to what is seen: through a side, what is behind is about as deep as
    // the side's point, lit from the surface just above
    float down = depthBelow / muSun;
    if (side) {
        distance = across;
        down = 0.0;
    }
    // Lit through the water above it, and seen through the water in front of it
    bottom *= exp(-extinction * (down + distance));

    // The light coming down into the water, from the sun and the sky, on a level surface
    vec3 downwelling = sun * (1.0 - waterFresnel(cosSunDown)) * max(cosSunDown, 0.0)
        + 3.14159265 * skyAverage * (1.0 - SKY_REFLECTANCE);
    // What the column sends back, evenly over the directions it leaves in
    vec3 column = downwelling / 3.14159265
        * waterColumnReflectance(absorption, backscattering, down, distance);

    vec3 below = (bottom + column) / (WATER_INDEX * WATER_INDEX);
    vec3 color = reflected * skyReflected + highlight + (1.0 - reflected) * below;
    // Foam over it, lit by the sun on the wave and the sky above
    if (foam > 0.0) {
        vec3 irradiance = sun * max(cosSun, 0.0) + 3.14159265 * skyAverage;
        color = mix(color, FOAM_ALBEDO / 3.14159265 * irradiance, foam);
    }

    if ((fog.enabled & FOG_ENABLED) != 0) {
        color = fogApply(color, gl_FragCoord.xy, depth, fog.logScale);
    }
    outColor = vec4(color, 1.0);
}
