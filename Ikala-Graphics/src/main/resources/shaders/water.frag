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
// On a side, how far its water has fallen from the lip, in meters
layout(location = 17) in float outFall;

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
// A falling sheet's streaks: how wide, in meters; how long apart in when their water left the
// lip, in seconds, which the fall stretches as it speeds up; and how much they tilt the sheet
const float SHEET_STREAK_WIDTH = 0.15;
const float SHEET_STREAK_TIME = 0.125;
const float SHEET_STREAK_TILT = 0.6;
// Where a falling sheet breaks up and is white through, Horeni's fit for rectangular jets,
// 6 q^0.32 meters, q its flow per meter of lip in m^2/s (small laboratory jets, q under about
// 0.25; Castillo et al.); and how far down it starts to whiten, as a share of that
const float SHEET_BREAKUP_SCALE = 6.0;
const float SHEET_BREAKUP_POWER = 0.32;
const float SHEET_AERATION_START = 0.3;
// The finer strands in a white sheet: how many to a streak, where their pattern starts, and how
// much they thin it
const float SHEET_STRAND_SCALE = 3.0;
const float SHEET_STRAND_OFFSET = 37.0;
const float SHEET_STRAND_DEPTH = 0.55;
// The bubbles in a broken-up sheet: their radius, in meters, about a millimeter as in aerated
// flows, and the share of the sheet that is air where it is white through
const float SHEET_BUBBLE_RADIUS = 1e-3;
const float SHEET_MAX_AIR = 0.3;
// How far the bottom shifts across the screen per unit of the surface's tilt, as a share of the
// screen's height, per meter of water down to 2 m, over the distance
const float REFRACTION_SHIFT = 0.25;
const float REFRACTION_MAX_DEPTH = 2.0;
// How far off the sky is taken to be, seen through a side, in meters
const float SIDE_SKY_DISTANCE = 1e4;

// A number from 0 to 1, advancing a seed (Jarzynski and Olano's PCG hash)
float waterRandom(inout uint seed) {
    seed = seed * 747796405u + 2891336453u;
    uint word = ((seed >> ((seed >> 28u) + 4u)) ^ seed) * 277803737u;
    return float(((word >> 22u) ^ word) >> 8) / 16777216.0;
}

// A smooth value noise from 0 to 1 over a plane, each lattice point's value from a PCG hash,
// repeating every period cells, so a pattern fixed in the world stays put as the camera's place
// wraps, see WATER_PATTERN_WRAP
float foamHash(ivec2 cell, ivec2 period) {
    cell = ((cell % period) + period) % period;
    uint seed = uint(cell.x) * 1973u + uint(cell.y) * 9277u + 26699u;
    return waterRandom(seed);
}

float foamNoise(vec2 point, ivec2 period) {
    ivec2 cell = ivec2(floor(point));
    vec2 f = fract(point);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(foamHash(cell, period), foamHash(cell + ivec2(1, 0), period), f.x),
        mix(foamHash(cell + ivec2(0, 1), period), foamHash(cell + ivec2(1, 1), period), f.x), f.y);
}

// How many of a pattern's cells it takes to repeat over a length
ivec2 foamPeriod(vec2 length, vec2 cell) {
    return ivec2(round(length / cell));
}

// How much of a pixel the foam breaking crests left covers: patches over the share of the water the
// grid says, larger where the surface squeezes them, smaller where it stretches; a far pixel, too
// wide for the patches, just the share
float foamPatches(vec2 rest, float share, float squeeze, float footprint) {
    if (share <= 0.0) {
        return 0.0;
    }
    float thick = clamp(share / max(squeeze, FOAM_MIN_SQUEEZE), 0.0, 1.0);
    vec2 point = (rest + cameraWrap) / FOAM_PATCH_SIZE;
    ivec2 period = foamPeriod(vec2(WATER_PATTERN_WRAP), vec2(FOAM_PATCH_SIZE));
    float pattern = 0.6 * foamNoise(point, period) + 0.4 * foamNoise(2.0 * point + 17.0, 2 * period);
    float patches = smoothstep(1.0 - thick - FOAM_PATCH_EDGE, 1.0 - thick + FOAM_PATCH_EDGE,
        pattern);
    return mix(patches, thick, clamp(footprint / FOAM_PATCH_SIZE, 0.0, 1.0));
}

// Where what lies behind the water at a pixel is, relative to the camera, from its depth
vec3 behindPoint(ivec2 pixel, float behindDepth) {
    vec2 uv = (vec2(pixel) + 0.5) / screenSize;
    vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, behindDepth, 1.0);
    return mat3(invViewMatrix) * (view.xyz / view.w);
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
    // A side's face is water pouring off it: leaving the lip at its flow outward, falling faster
    // as it goes, sqrt(v0^2 + 2 g s) after falling s. Its pattern is fixed to when its water left
    // the lip and where along the lip, so it streams down, stretched as the water speeds up; the
    // waves crossing the lip tilt it as they go over; and it whitens with air as it breaks up
    if (side) {
        vec2 outward = outSideNormal;
        vec2 along = vec2(-outward.y, outward.x);
        float fallen = max(outFall, 0.0);
        float lip = max(dot(outFlow, outward), SHEET_MIN_SPEED);
        float speed = sqrt(lip * lip + 2.0 * WATER_GRAVITY * fallen);
        float launched = sheetTime - (speed - lip) / WATER_GRAVITY;
        float across = dot(outRestXZ + cameraWrap, along);
        vec2 point = vec2(across / SHEET_STREAK_WIDTH, launched / SHEET_STREAK_TIME);
        ivec2 period = foamPeriod(vec2(WATER_PATTERN_WRAP, WATER_SHEET_TIME_PERIOD),
            vec2(SHEET_STREAK_WIDTH, SHEET_STREAK_TIME));
        float streak = foamNoise(point, period);
        float streakSlope = foamNoise(point + vec2(0.5, 0.0), period)
            - foamNoise(point - vec2(0.5, 0.0), period);
        float lipTilt = 0.0;
        if (climate >= 0 || gridOn != 0) {
            WaveField lipField;
            lipField.climates = climateList;
            lipField.profiles = profileList;
            lipField.grid = gridList;
            lipField.climate = climate;
            lipField.ripples = rippleClimate;
            lipField.gridOn = gridOn != 0;
            lipField.level = 0;
            lipField.shift = vec2(0.0);
            vec2 lipAmplitudes[WAVE_DIRECTIONS];
            for (int i = 0; i < WAVE_AMPLITUDE_VECTORS; ++i) {
                lipAmplitudes[2 * i] = outAmplitudes[i].xy;
                lipAmplitudes[2 * i + 1] = outAmplitudes[i].zw;
            }
            vec3 lipSums;
            lipTilt = dot(waveSlope(lipField, lipAmplitudes, WAVE_LAST_LAYER, outRestXZ,
                lipSums).xy, along);
        }
        vec3 face = vec3(outward.x, 0.0, outward.y);
        vec3 tangent = vec3(along.x, 0.0, along.y);
        normal = normalize(face - tangent * (SHEET_STREAK_TILT * streakSlope + lipTilt));
        if (dot(normal, toEye) < 0.0) {
            normal = -normal;
        }
        // A free overfall's brink runs at about critical flow, v = sqrt(g d), so q = v^3 / g
        float perWidth = lip * lip * lip / WATER_GRAVITY;
        float breakup = SHEET_BREAKUP_SCALE * pow(perWidth, SHEET_BREAKUP_POWER);
        float aerated = smoothstep(SHEET_AERATION_START * breakup, breakup, fallen);
        // Thick and thin strands even where it is white through
        float strands = foamNoise(point * vec2(SHEET_STRAND_SCALE, 1.0)
            + vec2(0.0, SHEET_STRAND_OFFSET), period * ivec2(int(SHEET_STRAND_SCALE), 1));
        float bubbly = smoothstep(1.0 - aerated - FOAM_PATCH_EDGE,
            1.0 - aerated + FOAM_PATCH_EDGE, streak) * mix(1.0, strands, SHEET_STRAND_DEPTH);
        // The sheet is thin water full of bubbles, not a coat of foam: as thick as its flow over its
        // speed, q / v, its bubbles of radius r and air share a blocking 1.5 a / r of the light per
        // meter, so it is clear seen square on and whitens toward grazing, where the view's way
        // through it is longer
        float thickness = perWidth / speed;
        float depth = 1.5 * SHEET_MAX_AIR * bubbly * thickness / SHEET_BUBBLE_RADIUS;
        float cosFace = max(abs(dot(face, toEye)), MIN_COSINE);
        foam = 1.0 - exp(-depth / cosFace);
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
    // Through a side, the view bends in at the face, crosses the water to where it leaves the
    // tile, and bends out there; what it then reaches is what shows, which is how a block of water
    // shows as one
    float sideWater = -1.0;
    if (side) {
        vec3 into = normalize(outRenderPosition);
        vec3 bent = refract(into, normal, 1.0 / WATER_INDEX);
        vec2 low = tile.offset.xz;
        vec2 high = low + vec2(tile.absorption.w, tile.scattering.w);
        vec2 exits = mix((low - outRenderPosition.xz) / min(bent.xz, vec2(-1e-6)),
            (high - outRenderPosition.xz) / max(bent.xz, vec2(1e-6)),
            greaterThan(bent.xz, vec2(0.0)));
        sideWater = max(min(exits.x, exits.y), 0.0);
        // Something solid inside the tile's box, as the stone a pool stands on, stops the view first
        float straight = straightBehind < 1.0
            ? length(behindPoint(ivec2(gl_FragCoord.xy), straightBehind) - outRenderPosition)
            : SIDE_SKY_DISTANCE;
        bool stopped = straight < sideWater;
        sideWater = min(sideWater, straight);
        vec3 leaving = exits.x < exits.y ? vec3(sign(bent.x), 0.0, 0.0)
            : vec3(0.0, 0.0, sign(bent.z));
        vec3 exiting = refract(bent, -leaving, WATER_INDEX);
        // Met too steeply, the far face reflects it back in, and it ends on what the water stands
        // on or against, about where the straight view does
        if (!stopped && dot(exiting, exiting) == 0.0) {
            stopped = true;
            sideWater = straight;
        }
        if (!stopped) {
            vec3 exitPoint = outRenderPosition + bent * sideWater;
            // What is behind, about as far on as the straight view's, or far off for the sky
            vec3 seen = exitPoint + exiting * max(straight - sideWater, 0.0);
            vec4 clip = inverse(invProjectionMatrix)
                * vec4(transpose(mat3(invViewMatrix)) * seen, 1.0);
            vec2 ndc = clip.xy / clip.w;
            vec2 uv = vec2(ndc.x * 0.5 + 0.5, 0.5 - ndc.y * 0.5);
            pixel = clamp(ivec2(uv * screenSize), ivec2(0), ivec2(screenSize) - 1);
        }
    }
    float behindDepth = texelFetch(bindlessTextures[nonuniformEXT(behindDepthIndex)], pixel, 0).x;
    vec3 bottom = vec3(0.0);
    float depthBelow = DEEP;
    // Through a side, how far the view goes in the water
    float across = DEEP;
    if (behindDepth < 1.0) {
        vec3 render = behindPoint(pixel, behindDepth);
        vec4 view = vec4(transpose(mat3(invViewMatrix)) * render, 1.0);
        depthBelow = max(outRenderPosition.y - render.y, 0.0);
        across = length(render - outRenderPosition);
        bottom = texelFetch(bindlessTextures[nonuniformEXT(behindColorIndex)], pixel, 0).rgb;
        // The light stage fogged it as if the air reached all the way: take that fog back off
        if ((fog.enabled & FOG_ENABLED) != 0) {
            vec4 fogged = fogInFront(gl_FragCoord.xy, -view.z, fog.logScale);
            bottom = max(bottom - fogged.rgb, vec3(0.0)) / max(fogged.a, 1e-3);
        }
    } else if (side) {
        // Through a side with only the sky behind, the sky, past the air beyond the water
        bottom = texelFetch(bindlessTextures[nonuniformEXT(behindColorIndex)], pixel, 0).rgb;
    }
    float distance = depthBelow / muView;
    // The light's way down to what is seen: through a side, what is behind is about as deep as
    // the side's point, lit from the surface just above
    float down = depthBelow / muSun;
    if (side) {
        // The view leaves the water by the far side of the tile at the latest
        distance = min(across, sideWater);
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
