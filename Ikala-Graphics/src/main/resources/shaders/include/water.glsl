#ifndef WATER_GLSL
#define WATER_GLSL

// Water's optics, see WaterMath for the same math in Java, with tests; keep them in step. Includers
// enable GL_EXT_buffer_reference.

#include "water_optics.glsl"

// One tile's place and optics, WaterRender: its corner relative to the camera and its wave climate,
// -1 for still water; its absorption, scattering and backscattering at red, green and blue
// A water tile: its corner relative to the camera and its wind climate; its absorption, and its
// width along x in w; its scattering, and its width along z in w; its backscattering
struct WaterTileData {
    vec4 offset;
    vec4 absorption;
    vec4 scattering;
    vec4 backscattering;
    // The tiles across its edges, -x, +x, -z, +z, -1 for none; backscattering.w is 1 if its optics
    // blend with theirs, see WaterNeighbors.blends
    ivec4 neighbors;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer WaterTileList {
    WaterTileData waterTiles[];
};

// How far in from a tile's edge its water blends with the water across it, in meters, at most half
// the tile: an even mix at the edge, its own water this far in
const float WATER_BLEND_BAND = 8.0;

// A tile's optics at a point relative to the camera, blended near its edges with the waters across
// them, so a muddy river meets the clear sea in a band, not a line: mixed water's optics are its
// waters' mixed in the same shares. Bilinear between this tile, the ones across the nearer x and z
// edges and the one diagonally across both, the x neighbor's z neighbor, so the blend is continuous
// at corners too. Where a neighbor is missing, land, this tile's water stands in.
void waterTileOptics(WaterTileList list, WaterTileData tile, vec2 renderXZ, out vec3 absorption,
        out vec3 scattering, out vec3 backscattering) {
    absorption = tile.absorption.rgb;
    scattering = tile.scattering.rgb;
    backscattering = tile.backscattering.rgb;
    if (tile.backscattering.w == 0.0) {
        return;
    }
    vec2 size = vec2(tile.absorption.w, tile.scattering.w);
    vec2 band = min(vec2(WATER_BLEND_BAND), 0.5 * size);
    vec2 local = renderXZ - tile.offset.xz;
    bvec2 high = greaterThan(local, 0.5 * size);
    // How far in from the nearer edges, as shares of the band, and how much the water across counts
    vec2 inward = mix(local, size - local, vec2(high)) / band;
    vec2 across = 0.5 * max(vec2(0.0), 1.0 - inward);
    int xEdge = high.x ? 1 : 0;
    int zEdge = high.y ? 3 : 2;
    if (across.x <= 0.0 && across.y <= 0.0) {
        return;
    }
    int xTile = across.x > 0.0 ? tile.neighbors[xEdge] : -1;
    int zTile = across.y > 0.0 ? tile.neighbors[zEdge] : -1;
    mat3 x = mat3(absorption, scattering, backscattering);
    if (xTile >= 0) {
        WaterTileData t = list.waterTiles[xTile];
        x = mat3(t.absorption.rgb, t.scattering.rgb, t.backscattering.rgb);
    }
    mat3 z = mat3(absorption, scattering, backscattering);
    if (zTile >= 0) {
        WaterTileData t = list.waterTiles[zTile];
        z = mat3(t.absorption.rgb, t.scattering.rgb, t.backscattering.rgb);
    }
    // Diagonally across: the x neighbor's z neighbor, or the z neighbor's x neighbor; else the x
    // neighbor's water, or the z neighbor's
    mat3 d = xTile >= 0 ? x : z;
    if (across.x > 0.0 && across.y > 0.0) {
        int xz = xTile >= 0 ? list.waterTiles[xTile].neighbors[zEdge] : -1;
        if (xz < 0 && zTile >= 0) {
            xz = list.waterTiles[zTile].neighbors[xEdge];
        }
        if (xz >= 0) {
            WaterTileData t = list.waterTiles[xz];
            d = mat3(t.absorption.rgb, t.scattering.rgb, t.backscattering.rgb);
        }
    }
    mat3 own = mat3(absorption, scattering, backscattering);
    mat3 mixed = (1.0 - across.x) * (1.0 - across.y) * own + across.x * (1.0 - across.y) * x
        + (1.0 - across.x) * across.y * z + across.x * across.y * d;
    absorption = mixed[0];
    scattering = mixed[1];
    backscattering = mixed[2];
}

// Wind waves, see WaveMath: the directions the surface sums, the samples and length of each
// short profile, its layers, and the shortest wave each layer holds, in meters
const int WAVE_DIRECTIONS = 16;
const int WAVE_PROFILE_SAMPLES = 4096;
const float WAVE_PROFILE_LENGTH = 160.0;
// The long profiles, one a climate's depth and direction, which hold the waves longer than the
// short profiles can, in every layer: their samples and length, a whole number of short profiles,
// and how many waves they sum, which come first among a climate's, see WaveMath.LONG_WAVES
const int WAVE_LONG_SAMPLES = 2048;
const float WAVE_LONG_LENGTH = 1280.0;
const int WAVE_LONG_WAVES = 14;
const int WAVE_LAYERS = 4;
const float WAVE_LAYER_CUTOFFS[WAVE_LAYERS] = float[](0.0, 0.25, 1.0, 4.0);
// The layer vertices are displaced by, whose waves a grid of a meter samples well enough
const int WAVE_VERTEX_LAYER = 3;
// The layer of only the longest waves, a swell's
const int WAVE_LAST_LAYER = WAVE_LAYERS - 1;
// How far the choppy waves move the surface sideways, Tessendorf's lambda: 1 moves each point
// round a circle as tall as its wave, as in Gerstner's waves
const float WAVE_CHOP = 1.0;
// How soft the foam's edge is, for the pixels that see every wave, as a share of the spread of the
// climate's Jacobian: a fixed width swamps a light breeze's, whose threshold sits close to 1
const float WAVE_FOAM_EDGE = 0.25;
// The least Jacobian the slope of the choppy surface is worked out with, short of a fold
const float WAVE_MIN_JACOBIAN = 0.1;
// How much each layer's cutoff grows over the one before
const float WAVE_LAYER_RATIO = 4.0;
// The depths each climate's profiles are worked out for, see WaveMath.DEPTH_LEVELS: deep water,
// 3 m and 1 m. Water deeper than twice the first shallow one is deep.
const int WAVE_DEPTHS = 3;
const float WAVE_SHALLOW_DEPTHS[WAVE_DEPTHS - 1] = float[](3.0, 1.0);
// How long the flow's two phases take to come round, in seconds: the surface's pattern is carried
// by the current for half of it before fading out for the other phase's
const float WAVE_FLOW_CYCLE = 4.0;

// One wave climate, WaterRender: each direction's amplitude and where the camera stands along it in
// its profile, in meters; the slope variance each layer leaves out; which depth's profiles each
// depth reads, deep water's where its waves are the same, so those are never filled; and the
// amplitude of its strongest direction, in x, which the grid's wind band keeps shares of; the
// Jacobian its waves break into foam under, in y, see WaveMath.foamJacobian; and the variance of
// the Jacobian's sum over all its waves, z, and over only the longest layer's, w, for a direction's
// whole share, see WaveMath.jacobianVariance; then each direction's share of its swell, see
// WaveMath.swellAmplitudes
struct WaveClimate {
    float amplitudes[WAVE_DIRECTIONS];
    float offsets[WAVE_DIRECTIONS];
    vec4 missingVariance;
    ivec4 levels;
    vec4 peak;
    float swell[WAVE_DIRECTIONS];
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer WaveClimateList {
    WaveClimate climates[];
};

// The most climates drawn at once, the winds' and the ripples', WaterRender.MAX_CLIMATES + 1: the
// long profiles have room for that many, then the swells' as many, before the short ones start
const int WAVE_MAX_CLIMATES = 5;
const int WAVE_LONG_REGION = WAVE_MAX_CLIMATES * WAVE_DEPTHS * WAVE_DIRECTIONS * WAVE_LONG_SAMPLES;

// The long profiles first, each climate's depth after depth, in each direction after direction,
// WAVE_LONG_SAMPLES each; the swells' the same; then the short ones, the same and in each layer
// after layer, WAVE_PROFILE_SAMPLES each. A sample is four half floats: height, slope, how far the choppy waves
// move the surface along the profile, and the sum the Jacobian is made of, see water_waves.comp
layout(buffer_reference, std430, buffer_reference_align = 8) readonly buffer WaveProfileList {
    uvec2 profiles[];
};

// One profile sample's four sums
vec4 waveProfileSample(uvec2 packed) {
    return vec4(unpackHalf2x16(packed.x), unpackHalf2x16(packed.y));
}

// Where a climate's short profile starts in the list
int waveShortBase(int climate, int level, int direction, int layer) {
    return 2 * WAVE_LONG_REGION + (((climate * WAVE_DEPTHS + level) * WAVE_DIRECTIONS + direction)
        * WAVE_LAYERS + layer) * WAVE_PROFILE_SAMPLES;
}

// Where a climate's long profile starts in the list
int waveLongBase(int climate, int level, int direction) {
    return ((climate * WAVE_DEPTHS + level) * WAVE_DIRECTIONS + direction) * WAVE_LONG_SAMPLES;
}

// Where a climate's swell profile starts in the list
int waveSwellBase(int climate, int level, int direction) {
    return WAVE_LONG_REGION + waveLongBase(climate, level, direction);
}

// One profile's sums at a distance along it, between its two nearest samples, wrapping; its
// samples a power of two
vec4 waveProfileAt(WaveProfileList list, int base, int samples, float length, float distance) {
    float at = distance / length * float(samples);
    float lower = floor(at);
    int i0 = int(lower) & (samples - 1);
    int i1 = (i0 + 1) & (samples - 1);
    return mix(waveProfileSample(list.profiles[base + i0]),
        waveProfileSample(list.profiles[base + i1]), at - lower);
}

// A direction's height, slope, along-profile displacement and Jacobian sum at a distance along it,
// in a layer: its short profile's and its long one's, whose waves every layer keeps
vec4 waveProfile(WaveProfileList list, int climate, int level, int direction, int layer,
        float distance) {
    return waveProfileAt(list, waveShortBase(climate, level, direction, layer),
            WAVE_PROFILE_SAMPLES, WAVE_PROFILE_LENGTH, distance)
        + waveProfileAt(list, waveLongBase(climate, level, direction), WAVE_LONG_SAMPLES,
            WAVE_LONG_LENGTH, distance);
}

// Which depth's profiles suit water of a depth, as a fraction between two of them
float waveDepthLevel(float depth) {
    float first = WAVE_SHALLOW_DEPTHS[0];
    if (depth >= 2.0 * first) {
        return 0.0;
    }
    if (depth >= first) {
        return (2.0 * first - depth) / first;
    }
    float second = WAVE_SHALLOW_DEPTHS[1];
    return depth >= second ? 1.0 + (first - depth) / (first - second) : 2.0;
}

// How far the flow has carried the surface's pattern in one of its two phases, see WAVE_FLOW_CYCLE
vec2 waveFlowShift(vec2 flow, float time, int phase) {
    float along = fract(time / WAVE_FLOW_CYCLE + 0.5 * float(phase));
    return flow * WAVE_FLOW_CYCLE * (along - 0.5);
}

// How much the second phase counts: none when the first is halfway, when its pattern is unstretched
float waveFlowWeight(float time) {
    return abs(2.0 * fract(time / WAVE_FLOW_CYCLE) - 1.0);
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
// Gravity, in meters a second squared
const float WATER_GRAVITY = 9.81;
// How fast water leaves a lip whose surface doesn't flow out over it, in meters a second: a gentle
// spill
const float SHEET_MIN_SPEED = 0.3;
// How far out from the face it falls past a falling sheet is drawn, in meters, so the two never
// fight over depth
const float SHEET_GAP = 0.01;

// How often patterns fixed in the world repeat, in meters, and the falling sheets' time, in
// seconds, see WaveMath.PATTERN_WRAP and SHEET_TIME_PERIOD: each a whole number of the patterns'
// cells
const float WATER_PATTERN_WRAP = 614.4;
const float WATER_SHEET_TIME_PERIOD = 512.0;

// After the bands, the share of the cell the foam breaking crests left covers, then how hard it rains
// on the cell, in drops a square meter a second, see WaveletMath.CELL_VALUES: read as the foam
// band's directions 0 and 1
const int WAVE_FOAM_BAND = WAVE_BANDS;
const int WAVE_RAIN_DIRECTION = 1;
const int WAVE_CELL_VALUES = WAVE_BANDS * WAVE_DIRECTIONS + 2;
// How much of the whitecaps' coverage is foam still being made, see WaveMath.ACTIVE_WHITECAP_SHARE,
// and how long the foam left behind takes to fade, in seconds, see WaveMath.FOAM_LIFETIME
const float WAVE_ACTIVE_WHITECAP_SHARE = 0.115;
const float WAVE_FOAM_LIFETIME = 4.0;
// How soft the edge of where the grid's crests break is, as a share of their Jacobian's spread
const float WAVE_FOAM_CREST_EDGE = 0.25;

// The normal distribution's cumulative function, in the tanh form, see WaveMath.normalCdf: close in
// the tail, where foam's chances lie. Kept to WAVE_NORMAL_REACH standard deviations, where it is 0
// or 1 already: tanh of a large number overflows to NaN on some drivers.
const float WAVE_NORMAL_REACH = 8.0;

float waveNormalCdf(float x) {
    x = clamp(x, -WAVE_NORMAL_REACH, WAVE_NORMAL_REACH);
    return 0.5 + 0.5 * tanh(0.7978845608 * (x + 0.044715 * x * x * x));
}

// The share of the water a climate's foam left behind covers on average, the rest of the
// whitecaps' coverage after those breaking: what open water past the grid holds
float waveFoamLeft(WaveClimate climate) {
    if (climate.peak.z <= 0.0) {
        return 0.0;
    }
    float breaking = waveNormalCdf((climate.peak.y - 1.0) / sqrt(climate.peak.z));
    return breaking * (1.0 - WAVE_ACTIVE_WHITECAP_SHARE) / WAVE_ACTIVE_WHITECAP_SHARE;
}
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
// How narrowly a wave paint's waves keep to its way, the power on the cosine, see WaveletMath
const float WAVE_PAINT_SPREAD = 8.0;

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
    // Which depth's profiles, and how far the flow carried the pattern
    int level;
    vec2 shift;
};

// One direction's amplitudes at the field's point, the wind's waves' and the ripples': in the grid
// the wind band holds shares of the climate's strongest direction, so shores and paint can send
// waves any way, even against the wind; past it, the climate's own
vec2 waveAmplitudes(WaveField field, int b) {
    float wind = field.climate >= 0 ? field.climates.climates[field.climate].amplitudes[b] : 0.0;
    if (!field.gridOn) {
        return vec2(wind, 0.0);
    }
    if (field.at.inside && field.climate >= 0) {
        wind = field.climates.climates[field.climate].peak.x
            * waveGridValue(field.grid, field.at, WAVE_WIND_BAND, b);
    }
    return vec2(wind, waveGridValue(field.grid, field.at, WAVE_RIPPLE_BAND, b));
}

// Which depth's profiles a climate reads for the field's depth
int waveClimateLevel(WaveField field, int climate) {
    return climate >= 0 ? field.climates.climates[climate].levels[field.level] : 0;
}

// Whether any climate drawn has a swell, WaterRender's specialization constant: compiled in but
// unused, the swell's code cost the surface about 5%
layout(constant_id = 0) const bool WAVE_SWELL = false;

// Whether the field's climate has a swell, in its levels' last
bool waveHasSwell(WaveField field) {
    return WAVE_SWELL && field.climate >= 0
        && field.climates.climates[field.climate].levels.w != 0;
}

// A direction's swell's sums at a distance along it, at the field's climate's depth level: the
// swell's own waves, its share in that direction. The wave grid leaves swell alone. Only for a
// climate with a swell, see waveHasSwell.
vec4 waveSwell(WaveField field, int level, int b, float distance) {
    float amplitude = field.climates.climates[field.climate].swell[b];
    if (amplitude <= WAVE_FAINTEST) {
        return vec4(0.0);
    }
    return amplitude * waveProfileAt(field.profiles, waveSwellBase(field.climate, level, b),
        WAVE_LONG_SAMPLES, WAVE_LONG_LENGTH, distance);
}

// How the waves move the surface at a point relative to the camera, from one layer and the
// directions' amplitudes there: x and z by the choppy waves, as much as WAVE_CHOP, and y, its height
// above its rest
vec3 waveDisplacement(WaveField field, vec2 all[WAVE_DIRECTIONS], int layer, vec2 renderXZ) {
    vec3 moved = vec3(0.0);
    int windLevel = waveClimateLevel(field, field.climate);
    int rippleLevel = waveClimateLevel(field, field.ripples);
    bool swell = waveHasSwell(field);
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        vec2 amplitudes = all[b];
        vec2 direction = waveDirection(b);
        float along = dot(renderXZ - field.shift, direction);
        vec2 sums = vec2(0.0);
        if (amplitudes.x > WAVE_FAINTEST || swell) {
            float distance = along + field.climates.climates[field.climate].offsets[b];
            if (amplitudes.x > WAVE_FAINTEST) {
                sums += amplitudes.x
                    * waveProfile(field.profiles, field.climate, windLevel, b, layer, distance).xz;
            }
            if (swell) {
                sums += waveSwell(field, windLevel, b, distance).xz;
            }
        }
        if (amplitudes.y > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.ripples].offsets[b];
            sums += amplitudes.y
                * waveProfile(field.profiles, field.ripples, rippleLevel, b, layer, distance).xz;
        }
        moved += vec3(WAVE_CHOP * sums.y * direction.x, sums.x, WAVE_CHOP * sums.y * direction.y);
    }
    return moved;
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
// and the slope variance the layer leaves out there; and the sums the choppy waves' Jacobian is
// made of: J = I - WAVE_CHOP * [x z; z y] of them, see waveJacobian
vec3 waveSlope(WaveField field, vec2 all[WAVE_DIRECTIONS], int layer, vec2 renderXZ,
        out vec3 jacobian) {
    vec2 slope = vec2(0.0);
    jacobian = vec3(0.0);
    float windEnergy = 0.0;
    float rippleEnergy = 0.0;
    int windLevel = waveClimateLevel(field, field.climate);
    int rippleLevel = waveClimateLevel(field, field.ripples);
    bool swell = waveHasSwell(field);
    for (int b = 0; b < WAVE_DIRECTIONS; ++b) {
        vec2 amplitudes = all[b];
        vec2 direction = waveDirection(b);
        float along = dot(renderXZ - field.shift, direction);
        if (amplitudes.x > WAVE_FAINTEST) {
            // Past the wind's own height in this direction, painted or reflected waves grow only
            // their swell: short waves are already as steep as they get, as in a rougher sea
            float own = field.climates.climates[field.climate].amplitudes[b];
            float fine = min(amplitudes.x, own);
            float swell = amplitudes.x - fine;
            float distance = along + field.climates.climates[field.climate].offsets[b];
            vec4 profile = fine > WAVE_FAINTEST
                ? waveProfile(field.profiles, field.climate, windLevel, b, layer, distance)
                : vec4(0.0);
            vec4 swellProfile = swell <= WAVE_FAINTEST ? vec4(0.0)
                : layer == WAVE_LAST_LAYER ? profile
                : waveProfile(field.profiles, field.climate, windLevel, b, WAVE_LAST_LAYER,
                    distance);
            vec4 sums = fine * profile + swell * swellProfile;
            slope += direction * sums.y;
            jacobian += sums.w * vec3(direction.x * direction.x, direction.y * direction.y,
                direction.x * direction.y);
            windEnergy += fine * fine;
        }
        if (swell) {
            // The swell's own waves, long, so every layer keeps them
            vec4 sums = waveSwell(field, windLevel, b,
                along + field.climates.climates[field.climate].offsets[b]);
            slope += direction * sums.y;
            jacobian += sums.w * vec3(direction.x * direction.x, direction.y * direction.y,
                direction.x * direction.y);
        }
        if (amplitudes.y > WAVE_FAINTEST) {
            float distance = along + field.climates.climates[field.ripples].offsets[b];
            // Ripples tilt the surface but don't break: they stay out of the Jacobian
            slope += amplitudes.y * direction
                * waveProfile(field.profiles, field.ripples, rippleLevel, b, layer, distance).y;
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

// The choppy waves' map from the surface at rest to where it is, x0 to x0 + D: its Jacobian
// matrix, from waveSlope's sums, xx, zz and xz
mat2 waveJacobian(vec3 sums) {
    return mat2(1.0 - WAVE_CHOP * sums.x, -WAVE_CHOP * sums.z,
        -WAVE_CHOP * sums.z, 1.0 - WAVE_CHOP * sums.y);
}

// How much of a pixel foam covers, from the Jacobian of the waves it sees and the slope variance
// of those it can't: where the surface squeezes under the climate's foam Jacobian it breaks, a
// threshold set so foam covers as much as measured whitecaps do (WaveMath.foamJacobian); War
// Thunder's ocean uses a fixed 0.3 to 0.5 (Tcheblokov 2015). The waves left out add to the
// Jacobian about as much spread as to the slope, so a far pixel's foam is the chance its waves
// break, as Dupuy and Bruneton filter whitecaps (2012).
float waveFoam(float jacobian, float missingVariance, float threshold, float climateVariance) {
    float edge = WAVE_FOAM_EDGE * WAVE_CHOP * sqrt(climateVariance);
    float spread = sqrt(WAVE_CHOP * WAVE_CHOP * missingVariance + edge * edge);
    if (spread <= 0.0) {
        return jacobian < threshold ? 1.0 : 0.0;
    }
    return waveNormalCdf((threshold - jacobian) / spread);
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

#endif
