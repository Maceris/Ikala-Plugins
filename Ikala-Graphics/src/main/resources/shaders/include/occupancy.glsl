#ifndef OCCUPANCY_GLSL
#define OCCUPANCY_GLSL

// Reading the occupancy volume, see OccupancyVolume: three levels of 128^3 voxels, 1, 2 and 4
// meters wide, stacked along z, addressed toroidally. Include with OCCUPANCY_SET defined, after
// light_inputs.glsl, which has the levels' places.

layout(set = OCCUPANCY_SET, binding = 0, r32ui) uniform restrict readonly uimage3D occupancyVolume;
// Each level's coverage, through a linear sampler that wraps around as the levels' addressing does
layout(set = OCCUPANCY_SET, binding = 2) uniform sampler3D occupancyCoverage[3];

// Match OccupancyClipmap and OccupancyVolume
const int OCCUPANCY_SIZE = 128;
const int OCCUPANCY_LEVELS = 3;
const uint OCCUPANCY_OPAQUE = 1u;
const uint OCCUPANCY_TRANSLUCENT = 2u;
const uint OCCUPANCY_CUTOUT = 4u;
// Something that fills a voxel, wholly or with holes
const uint OCCUPANCY_SOLID = OCCUPANCY_OPAQUE | OCCUPANCY_CUTOUT;
// What a level gives for a point it doesn't hold
const uint OCCUPANCY_OUTSIDE = 0xFFFFFFFFu;

// The bits of the voxel of one level holding a render space point, or OCCUPANCY_OUTSIDE
uint occupancyIn(vec3 renderPosition, int level)
{
    vec4 origin = occupancyOrigins[level];
    ivec3 voxel = ivec3(floor((renderPosition - origin.xyz) / origin.w));
    if (any(lessThan(voxel, ivec3(0))) || any(greaterThanEqual(voxel, ivec3(OCCUPANCY_SIZE)))) {
        return OCCUPANCY_OUTSIDE;
    }
    ivec3 texel = (occupancyTexels[level].xyz + voxel) & (OCCUPANCY_SIZE - 1);
    return imageLoad(occupancyVolume, ivec3(texel.xy, level * OCCUPANCY_SIZE + texel.z)).x;
}

// The bits of the finest voxel holding a render space point; 0 past every level or while the
// volume can't be read
uint occupancyAt(vec3 renderPosition)
{
    if (occupancyEnabled == 0) {
        return 0u;
    }
    for (int level = 0; level < OCCUPANCY_LEVELS; ++level) {
        uint bits = occupancyIn(renderPosition, level);
        if (bits != OCCUPANCY_OUTSIDE) {
            return bits;
        }
    }
    return 0u;
}

// How many samples each ambient occlusion direction takes, each about twice as high above the
// surface as the last, the highest at the reach
const int AO_STEPS = 4;
// How much the tilted directions count next to the normal's: cos 45 degrees
const float AO_TILT = 0.70710678;
// How far above the surface, in voxels, a sample must be to count, so the voxels it blends between
// all lie above the surface's own voxel. Each sample also climbs half a voxel more than its share
// of the reach, so the nearest one clears this at the finest level.
const float AO_CLEARANCE = 1.5;
const float AO_HEIGHT_PAD = 0.5;

// The finest level holding every point within a distance of a render space point, or
// OCCUPANCY_LEVELS for none
int occupancyLevelAround(vec3 renderPosition, float distance)
{
    for (int level = 0; level < OCCUPANCY_LEVELS; ++level) {
        vec4 origin = occupancyOrigins[level];
        vec3 low = renderPosition - distance - origin.xyz;
        vec3 high = renderPosition + distance - origin.xyz;
        if (all(greaterThanEqual(low, vec3(0))) && all(lessThan(high, vec3(OCCUPANCY_SIZE * origin.w)))) {
            return level;
        }
    }
    return OCCUPANCY_LEVELS;
}

// How full one level is at a render space point, from 0 to 1, blended between the eight voxels
// around it, so it changes smoothly as the point moves. Indexed by constants, as the level may
// differ between neighboring pixels.
float occupancyFullness(vec3 renderPosition, int level)
{
    vec4 origin = occupancyOrigins[level];
    vec3 texel = vec3(occupancyTexels[level].xyz) + (renderPosition - origin.xyz) / origin.w;
    vec3 uvw = texel / float(OCCUPANCY_SIZE);
    if (level == 0) {
        return textureLod(occupancyCoverage[0], uvw, 0).r;
    } else if (level == 1) {
        return textureLod(occupancyCoverage[1], uvw, 0).r;
    }
    return textureLod(occupancyCoverage[2], uvw, 0).r;
}

// How much ambient light reaches a surface past what the occupancy volume holds nearby, from 0 to
// 1: looks out along the normal and four directions tilted 45 degrees around it, weighted by how
// near each is to the normal. Along each direction, every sample lets through what the samples
// before it left, less how full it is, and the direction lets through the share of its samples
// light reached. Samples blend between the voxels around them, so a surface lying anywhere in its
// voxel, even right between two layers, gets a steady answer. The normal is best the surface's
// geometric one.
//
// Also gives the bent normal: the directions averaged by how open each is, which leans away from
// what is in the way, toward where the sky shows.
float occupancyAmbient(vec3 renderPosition, vec3 renderNormal, out vec3 bentNormal)
{
    bentNormal = renderNormal;
    if (occupancyEnabled == 0 || occupancyAoReach <= 0.0) {
        return 1.0;
    }
    // Every sample is read from one level, so coarse voxels too near the surface to tell it from
    // what is above it are skipped
    int level = occupancyLevelAround(renderPosition, occupancyAoReach);
    if (level == OCCUPANCY_LEVELS) {
        return 1.0;
    }
    float voxelSize = occupancyOrigins[level].w;
    // Two directions across the normal, Duff et al. 2017
    float s = renderNormal.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (s + renderNormal.z);
    float b = renderNormal.x * renderNormal.y * a;
    vec3 across = vec3(1.0 + s * renderNormal.x * renderNormal.x * a, s * b, -s * renderNormal.x);
    vec3 across2 = vec3(b, s + renderNormal.y * renderNormal.y * a, -renderNormal.y);
    // The tilted directions move a meter across for each meter they climb, so a sample's height
    // above the surface is the same in every direction
    vec3 directions[5] = vec3[5](renderNormal, renderNormal + across, renderNormal - across,
        renderNormal + across2, renderNormal - across2);
    float open = 0.0;
    float total = 0.0;
    vec3 bent = vec3(0);
    for (int d = 0; d < 5; ++d) {
        float weight = d == 0 ? 1.0 : AO_TILT;
        float through = 1.0;
        float reached = 0.0;
        for (int step = 0; step < AO_STEPS; ++step) {
            float height = occupancyAoReach * exp2(float(step - AO_STEPS + 1))
                + AO_HEIGHT_PAD * voxelSize;
            if (height >= AO_CLEARANCE * voxelSize) {
                through *= 1.0 - occupancyFullness(renderPosition + directions[d] * height, level);
            }
            reached += through;
        }
        open += weight * reached / float(AO_STEPS);
        total += weight;
        bent += normalize(directions[d]) * (weight * reached / float(AO_STEPS));
    }
    // Shut in all around, there is no way it leans
    bentNormal = dot(bent, bent) > 1e-8 ? normalize(bent) : renderNormal;
    return open / total;
}

// How much ambient light reaches a surface, see the version that gives the bent normal too
float occupancyAmbient(vec3 renderPosition, vec3 renderNormal)
{
    vec3 bentNormal;
    return occupancyAmbient(renderPosition, renderNormal, bentNormal);
}

// The most lights a pixel marches a ray for. Matches ShaderBindings.Light.MAX_OCCUPANCY_RAYS.
const int OCCUPANCY_MAX_RAYS = 8;
// The most steps a ray takes; a longer ray takes longer steps
const int RAY_MAX_STEPS = 48;
// How long a step is, in voxels, at most: two to a voxel, so a wall a voxel thick can't fall
// between steps
const float RAY_STEP_VOXELS = 0.5;
// How far, in voxels, a ray starts out from the surface and stops short of the light, so the
// voxels holding either, whose blended coverage reaches a voxel past their middle, don't block it
const float RAY_END_CLEARANCE = 1.5;
// How bright each channel looks, Rec. 709, to rank the lights a pixel marches rays for
const vec3 RAY_LUMINANCE = vec3(0.2126, 0.7152, 0.0722);
// How much more than its blended coverage each ray sample blocks. Two samples a voxel land
// anywhere along it, and the nearer one to a voxel's middle always reads at least 3/4 of it, so
// with this gain an opaque voxel always stops a ray, wherever its samples fall; read as it is, a
// wall a voxel thick let up to 1/16 through in rings
const float RAY_COVERAGE_GAIN = 4.0 / 3.0;
// Past this little light, a ray stops: nothing behind it would show
const float RAY_OPAQUE = 0.01;

// The finest level holding two render space points with a voxel to spare for blending, or
// OCCUPANCY_LEVELS for none. Levels are boxes, so they hold the segment between the points too.
int occupancyLevelHolding(vec3 a, vec3 b)
{
    for (int level = 0; level < OCCUPANCY_LEVELS; ++level) {
        vec4 origin = occupancyOrigins[level];
        vec3 low = min(a, b) - origin.xyz - origin.w;
        vec3 high = max(a, b) - origin.xyz + origin.w;
        if (all(greaterThanEqual(low, vec3(0))) && all(lessThan(high, vec3(OCCUPANCY_SIZE * origin.w)))) {
            return level;
        }
    }
    return OCCUPANCY_LEVELS;
}

// How much light gets from a light to a surface through what the occupancy volume holds between
// them, from 0 to 1: a ray marched through the finest level holding both, every step letting
// through what the steps before it left, less how full it is. Leaves and plants let some through.
// The normal is best the surface's geometric one.
float occupancyRayVisibility(vec3 renderPosition, vec3 renderNormal, vec3 renderLight)
{
    int level = occupancyLevelHolding(renderPosition, renderLight);
    if (occupancyEnabled == 0 || level == OCCUPANCY_LEVELS) {
        return 1.0;
    }
    float voxelSize = occupancyOrigins[level].w;
    float clearance = RAY_END_CLEARANCE * voxelSize;
    vec3 start = renderPosition + renderNormal * clearance;
    vec3 toLight = renderLight - start;
    float span = length(toLight);
    float march = span - clearance;
    if (march <= 0.0) {
        return 1.0;
    }
    vec3 direction = toLight / span;
    int steps = min(int(ceil(march / (RAY_STEP_VOXELS * voxelSize))), RAY_MAX_STEPS);
    float step = march / float(steps);
    float through = 1.0;
    for (int i = 0; i < steps; ++i) {
        float full = occupancyFullness(start + direction * (step * (float(i) + 0.5)), level);
        through *= 1.0 - min(full * RAY_COVERAGE_GAIN, 1.0);
        if (through < RAY_OPAQUE) {
            return 0.0;
        }
    }
    return through;
}

// How far a reflection is followed through the occupancy volume for what hides the sky, in meters
const float REFLECTION_REACH = 16.0;

// How far above level, as the sine of its angle, a reflection fades from the ground's light, which
// the environment's lower half stands for, to the sky's, which what is in the way can hide
const float REFLECTION_HORIZON_FADE = 0.1;

// How much of the sky a surface sees along its reflection, from 0 to 1: a ray through the occupancy
// volume, out to REFLECTION_REACH, so floors and walls under cover don't mirror a sky they can't
// see. Reflections below the horizon mirror the environment's ground, which stands for the real
// ground's light, so the ground the ray would meet doesn't hide them. The normal is best the
// surface's geometric one.
float occupancyReflectionVisibility(vec3 renderPosition, vec3 renderNormal, vec3 reflected)
{
    float skyward = smoothstep(0.0, REFLECTION_HORIZON_FADE, reflected.y);
    // Past the surface's horizon the lobe is mostly hidden already
    if (skyward <= 0.0 || dot(reflected, renderNormal) <= 0.0) {
        return 1.0;
    }
    float seen = occupancyRayVisibility(renderPosition, renderNormal, renderPosition + reflected * REFLECTION_REACH);
    return mix(1.0, seen, skyward);
}

// The occupancy view: red where a surface's own voxel is empty, which would be a hole in the
// voxels; otherwise gray, darker the more of the voxels around the air in front of it are full
vec3 occupancyViewColor(vec3 renderPosition, vec3 renderNormal)
{
    if (occupancyEnabled == 0) {
        return vec3(0.0, 0.0, 1.0);
    }
    if ((occupancyAt(renderPosition - renderNormal * 0.25) & OCCUPANCY_SOLID) == 0u) {
        return vec3(1.0, 0.0, 0.0);
    }
    // The middle of the finest voxel in front of the surface, so no sample lands on a boundary
    vec3 grid = occupancyOrigins[0].xyz;
    vec3 front = floor(renderPosition + renderNormal * 0.5 - grid) + 0.5 + grid;
    float full = 0.0;
    for (int z = -1; z <= 1; ++z) {
        for (int y = -1; y <= 1; ++y) {
            for (int x = -1; x <= 1; ++x) {
                if ((occupancyAt(front + vec3(x, y, z)) & OCCUPANCY_SOLID) != 0u) {
                    full += 1.0;
                }
            }
        }
    }
    return vec3(1.0 - full / 27.0);
}

#endif
