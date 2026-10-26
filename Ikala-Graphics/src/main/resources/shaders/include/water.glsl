#ifndef WATER_GLSL
#define WATER_GLSL

// Water's optics, see WaterMath for the same math in Java, with tests; keep them in step. Includers
// enable GL_EXT_buffer_reference.

// Water's refractive index, and the light it reflects at normal incidence, WaterMath
const float WATER_INDEX = 1.333;
const float WATER_NORMAL_REFLECTANCE = 0.0203;

// One tile's place and optics, WaterRender: its corner relative to the camera and its wave climate,
// -1 for still water; its absorption, scattering and backscattering at red, green and blue
struct WaterTileData {
    vec4 offset;
    vec4 absorption;
    vec4 scattering;
    vec4 backscattering;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer WaterTileList {
    WaterTileData waterTiles[];
};

// Wind waves, see WaveMath: the directions the surface sums, the samples and length of each
// profile, its layers, and the shortest wave each layer holds, in meters
const int WAVE_DIRECTIONS = 16;
const int WAVE_PROFILE_SAMPLES = 4096;
const float WAVE_PROFILE_LENGTH = 160.0;
const int WAVE_LAYERS = 4;
const float WAVE_LAYER_CUTOFFS[WAVE_LAYERS] = float[](0.0, 0.25, 1.0, 4.0);
// The layer vertices are displaced by, whose waves a grid of a meter samples well enough
const int WAVE_VERTEX_LAYER = 3;
// How much each layer's cutoff grows over the one before
const float WAVE_LAYER_RATIO = 4.0;

// One wave climate, WaterRender: each direction's amplitude and where the camera stands along it in
// its profile, in meters; the slope variance each layer leaves out
struct WaveClimate {
    float amplitudes[WAVE_DIRECTIONS];
    float offsets[WAVE_DIRECTIONS];
    vec4 missingVariance;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer WaveClimateList {
    WaveClimate climates[];
};

// Each climate's profiles, direction after direction, and in each layer after layer, each
// WAVE_PROFILE_SAMPLES of height and slope
layout(buffer_reference, std430, buffer_reference_align = 8) readonly buffer WaveProfileList {
    vec2 profiles[];
};

// A profile's height and slope at a distance along it, between its two nearest samples, wrapping
vec2 waveProfile(WaveProfileList list, int climate, int direction, int layer, float distance) {
    float at = distance / WAVE_PROFILE_LENGTH * float(WAVE_PROFILE_SAMPLES);
    float lower = floor(at);
    int i0 = int(lower) & (WAVE_PROFILE_SAMPLES - 1);
    int i1 = (i0 + 1) & (WAVE_PROFILE_SAMPLES - 1);
    int base = ((climate * WAVE_DIRECTIONS + direction) * WAVE_LAYERS + layer)
        * WAVE_PROFILE_SAMPLES;
    return mix(list.profiles[base + i0], list.profiles[base + i1], at - lower);
}

// A direction's unit vector along the water, x and z
vec2 waveDirection(int b) {
    float angle = 6.28318530718 * float(b) / float(WAVE_DIRECTIONS);
    return vec2(cos(angle), sin(angle));
}

// The surface's height above its rest at a point relative to the camera, from one layer
float waveHeight(WaveClimateList climates, WaveProfileList profiles, int climate, int layer,
        vec2 renderXZ) {
    WaveClimate c = climates.climates[climate];
    float height = 0.0;
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        if (c.amplitudes[b] <= 0.0) {
            continue;
        }
        float distance = dot(renderXZ, waveDirection(b)) + c.offsets[b];
        height += c.amplitudes[b] * waveProfile(profiles, climate, b, layer, distance).x;
    }
    return height;
}

// The surface's slope at a point, x and z, from one layer
vec2 waveSlope(WaveClimateList climates, WaveProfileList profiles, int climate, int layer,
        vec2 renderXZ) {
    WaveClimate c = climates.climates[climate];
    vec2 slope = vec2(0.0);
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        if (c.amplitudes[b] <= 0.0) {
            continue;
        }
        vec2 direction = waveDirection(b);
        float distance = dot(renderXZ, direction) + c.offsets[b];
        slope += c.amplitudes[b] * waveProfile(profiles, climate, b, layer, distance).y * direction;
    }
    return slope;
}

// Which layer suits waves seen at a footprint, as a fraction between two layers: the shortest
// waves kept are four footprints long, so each gets two pixels a half wave
float waveLayer(float footprint) {
    float needed = 4.0 * footprint;
    if (needed <= WAVE_LAYER_CUTOFFS[1]) {
        return needed / WAVE_LAYER_CUTOFFS[1];
    }
    return min(1.0 + log(needed / WAVE_LAYER_CUTOFFS[1]) / log(WAVE_LAYER_RATIO),
        float(WAVE_LAYERS - 1));
}

// The light a surface reflects at an angle, Schlick's approximation, see WaterMath.fresnel
float waterFresnel(float cosine) {
    float m = 1.0 - clamp(cosine, 0.0, 1.0);
    float m2 = m * m;
    return WATER_NORMAL_REFLECTANCE + (1.0 - WATER_NORMAL_REFLECTANCE) * m2 * m2 * m;
}

// A ray's cosine to the normal once it crosses into the water, see WaterMath.refractedCosine
float waterRefractedCosine(float cosine) {
    float c = clamp(cosine, 0.0, 1.0);
    float sine = sqrt(1.0 - c * c) / WATER_INDEX;
    return sqrt(1.0 - sine * sine);
}

// The factor in deep water's reflectance, WaterMath.REFLECTANCE_FACTOR
const float WATER_REFLECTANCE_FACTOR = 0.33;

// How much of the light coming down a column sends back up, not counting its bottom, see
// WaterMath.columnReflectance
vec3 waterColumnReflectance(vec3 absorption, vec3 backscattering, float down, float up) {
    vec3 attenuation = absorption + backscattering;
    vec3 deep = WATER_REFLECTANCE_FACTOR * backscattering / max(attenuation, vec3(1e-6));
    return deep * (1.0 - exp(-attenuation * max(down + up, 0.0)));
}

#endif
