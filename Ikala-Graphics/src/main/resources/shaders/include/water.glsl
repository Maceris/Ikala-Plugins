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

// The wave amplitude grid around the camera, see WaveletMath: cells along a side and their size in
// meters, its bands, and the values a cell holds, each band's amplitude in each direction
const int WAVE_GRID_SIZE = 128;
const float WAVE_CELL_SIZE = 1.0;
const int WAVE_BANDS = 2;
const int WAVE_WIND_BAND = 0;
const int WAVE_RIPPLE_BAND = 1;
const int WAVE_CELL_VALUES = WAVE_BANDS * WAVE_DIRECTIONS;
// How fast each band carries its amplitude, in meters a second; how long the wind takes to build
// its waves back and ripples to die away, in seconds; how much a shore reflects; and how fast
// amplitude spreads into the neighboring directions, per second
const float WAVE_WIND_SPEED = 3.5;
const float WAVE_RIPPLE_SPEED = 0.35;
const float WAVE_WIND_RECOVERY = 20.0;
const float WAVE_RIPPLE_LIFETIME = 6.0;
const float WAVE_REFLECTION = 0.7;
const float WAVE_ANGULAR_DIFFUSION = 0.5;
// Amplitudes below this, in meters, are left out of the sums: ripples dying away never reach 0
const float WAVE_FAINTEST = 1e-4;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer WaveGridList {
    float values[];
};

// Where a world cell's values are in the grid, which wraps around
int waveGridSlot(ivec2 cell) {
    ivec2 wrapped = cell & (WAVE_GRID_SIZE - 1);
    return wrapped.y * WAVE_GRID_SIZE + wrapped.x;
}

// Where a point is in the grid: the four nearest cells' middles and how much each counts, or
// outside it
struct WaveGridSample {
    int slots[4];
    vec4 weights;
    bool inside;
};

// The grid's sample at a point relative to the camera, from the grid's corner relative to the
// camera and in world cells
WaveGridSample waveGridAt(vec2 renderXZ, vec2 cornerRender, ivec2 cornerCell) {
    WaveGridSample at;
    vec2 local = (renderXZ - cornerRender) / WAVE_CELL_SIZE - 0.5;
    at.inside = all(greaterThanEqual(local, vec2(0.0)))
        && all(lessThanEqual(local, vec2(float(WAVE_GRID_SIZE - 1))));
    ivec2 base = cornerCell + ivec2(floor(local));
    vec2 f = fract(local);
    at.slots[0] = waveGridSlot(base);
    at.slots[1] = waveGridSlot(base + ivec2(1, 0));
    at.slots[2] = waveGridSlot(base + ivec2(0, 1));
    at.slots[3] = waveGridSlot(base + ivec2(1, 1));
    at.weights = vec4((1.0 - f.x) * (1.0 - f.y), f.x * (1.0 - f.y), (1.0 - f.x) * f.y, f.x * f.y);
    return at;
}

// A band's amplitude in a direction at a grid sample; open water's past the grid
float waveGridValue(WaveGridList grid, WaveGridSample at, int band, int direction) {
    if (!at.inside) {
        return band == WAVE_WIND_BAND ? 1.0 : 0.0;
    }
    int channel = band * WAVE_DIRECTIONS + direction;
    return dot(at.weights, vec4(grid.values[at.slots[0] * WAVE_CELL_VALUES + channel],
        grid.values[at.slots[1] * WAVE_CELL_VALUES + channel],
        grid.values[at.slots[2] * WAVE_CELL_VALUES + channel],
        grid.values[at.slots[3] * WAVE_CELL_VALUES + channel]));
}

// What makes the waves at a point: the tile's wind climate, -1 for none, the ripples' climate and
// the grid, if there is one
struct WaveField {
    WaveClimateList climates;
    WaveProfileList profiles;
    WaveGridList grid;
    int climate;
    int ripples;
    bool gridOn;
    WaveGridSample at;
};

// One direction's amplitudes at the field's point, the wind's waves' and the ripples'
vec2 waveAmplitudes(WaveField field, int b) {
    float wind = field.climate >= 0 ? field.climates.climates[field.climate].amplitudes[b] : 0.0;
    if (!field.gridOn) {
        return vec2(wind, 0.0);
    }
    return vec2(wind * waveGridValue(field.grid, field.at, WAVE_WIND_BAND, b),
        waveGridValue(field.grid, field.at, WAVE_RIPPLE_BAND, b));
}

// The surface's height above its rest at a point relative to the camera, from one layer
float waveHeight(WaveField field, int layer, vec2 renderXZ) {
    float height = 0.0;
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        vec2 amplitudes = waveAmplitudes(field, b);
        float along = dot(renderXZ, waveDirection(b));
        if (amplitudes.x > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.climate].offsets[b];
            height += amplitudes.x * waveProfile(field.profiles, field.climate, b, layer, distance).x;
        }
        if (amplitudes.y > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.ripples].offsets[b];
            height += amplitudes.y * waveProfile(field.profiles, field.ripples, b, layer, distance).x;
        }
    }
    return height;
}

// The directions' amplitudes passed from vertex to fragment, two directions to a vec4: the grid's
// cells are as wide as the surface's, so they are read per vertex and interpolated
const int WAVE_AMPLITUDE_VECTORS = WAVE_DIRECTIONS / 2;

// Every direction's amplitudes at the field's point, read once for the layers that use them
void waveAmplitudesAll(WaveField field, out vec2 amplitudes[WAVE_DIRECTIONS]) {
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        amplitudes[b] = waveAmplitudes(field, b);
    }
}

// The surface's slope at a point, x and z, from one layer and the directions' amplitudes there,
// and the slope variance the layer leaves out there
vec3 waveSlope(WaveField field, vec2 all[WAVE_DIRECTIONS], int layer, vec2 renderXZ) {
    vec2 slope = vec2(0.0);
    float windEnergy = 0.0;
    float rippleEnergy = 0.0;
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        vec2 amplitudes = all[b];
        vec2 direction = waveDirection(b);
        float along = dot(renderXZ, direction);
        if (amplitudes.x > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.climate].offsets[b];
            slope += amplitudes.x
                * waveProfile(field.profiles, field.climate, b, layer, distance).y * direction;
            windEnergy += amplitudes.x * amplitudes.x;
        }
        if (amplitudes.y > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.ripples].offsets[b];
            slope += amplitudes.y
                * waveProfile(field.profiles, field.ripples, b, layer, distance).y * direction;
            rippleEnergy += amplitudes.y * amplitudes.y;
        }
    }
    float missing = 0.0;
    if (field.climate >= 0) {
        missing += windEnergy * field.climates.climates[field.climate].missingVariance[layer];
    }
    if (field.gridOn) {
        missing += rippleEnergy * field.climates.climates[field.ripples].missingVariance[layer];
    }
    return vec3(slope, missing);
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
