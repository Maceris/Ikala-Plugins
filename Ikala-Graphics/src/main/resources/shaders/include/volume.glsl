#ifndef VOLUME_GLSL
#define VOLUME_GLSL

// Volume bricks, which stand in for sections' cut out and translucent blocks at a distance: one
// brick of VOLUME_BRICK cubed voxels a section, each voxel its surfaces' color and extinction, in
// slots of one 3D atlas. VolumeMath has the same math in Java; keep them in step.
//
// A voxel inside a cut out block, like leaves, holds the most extinction there is, which marks it
// solid: rays walking the brick a voxel at a time stop at its face and light it as a block's face.
// Other voxels, of plants, water and glass, let light through by their extinction.
//
// Define VOLUME_SET before including this to sample the atlas.


#include "volume_fade.glsl"

// Voxels along each side of a brick, a meter each
const int VOLUME_BRICK = 16;
// The most extinction a see-through voxel holds, per meter; stored as the square root of its share
// in the lower half of the eight bits, with solid voxels and their coverage in the upper half
const float VOLUME_MAX_EXTINCTION = 16.0;
const float VOLUME_MAX_SEE_THROUGH = 127.0;
const float VOLUME_FIRST_SOLID = 128.0;
const float VOLUME_MAX_STORED = 255.0;
// Brick slots along the atlas's x and y
const uint VOLUME_SLOTS_X = 32u;
const uint VOLUME_SLOTS_Y = 16u;
// The most voxels a ray crosses in one brick
const int VOLUME_MAX_VOXELS = 3 * VOLUME_BRICK;
// Voxels along each side of a cell, the bricks' coarse grid for skipping empty space, and cells
// along each side of a brick
const int VOLUME_CELL = 4;
const int VOLUME_CELLS = VOLUME_BRICK / VOLUME_CELL;
// The extinction a solid voxel stands for, per meter, where its own blocks' shading looks through it
const float VOLUME_SOLID_EXTINCTION = VOLUME_MAX_EXTINCTION;
// The optical depth a solid voxel's face counts for: next to nothing gets through
const float VOLUME_SOLID_DEPTH = 8.0;
// Once this little light gets through a brick, the rest of it is skipped
const float VOLUME_OPAQUE_ENOUGH = 0.01;

// One brick to draw: its minimum corner relative to the camera, and its slot
struct VolumeDraw {
    vec3 corner;
    uint slot;
};

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer VolumeDraws {
    VolumeDraw volumeDraws[];
};

// Each pixel's distance along its ray to the first solid face any brick shows there, written by
// volume_depth.frag as the bits of a positive float, which order as the floats do
layout(buffer_reference, std430, buffer_reference_align = 4) buffer VolumeHits {
    uint hits[];
};

// Where a slot starts in the atlas, in texels
ivec3 volumeSlotOrigin(uint slot) {
    return ivec3(
        slot % VOLUME_SLOTS_X,
        (slot / VOLUME_SLOTS_X) % VOLUME_SLOTS_Y,
        slot / (VOLUME_SLOTS_X * VOLUME_SLOTS_Y)) * VOLUME_BRICK;
}

// Whether a stored value is a solid voxel's
bool volumeIsSolid(float stored) {
    return stored * VOLUME_MAX_STORED >= VOLUME_FIRST_SOLID - 0.5;
}

// A see-through voxel's stored extinction read back, per meter, or the solid voxel's stand-in
float volumeDecode(float stored) {
    if (volumeIsSolid(stored)) {
        return VOLUME_SOLID_EXTINCTION;
    }
    float root = stored * VOLUME_MAX_STORED / VOLUME_MAX_SEE_THROUGH;
    return root * root * VOLUME_MAX_EXTINCTION;
}

// A solid voxel's coverage read back: how much of its faces the texture covers
float volumeCoverage(float stored) {
    return clamp((stored * VOLUME_MAX_STORED - VOLUME_FIRST_SOLID)
        / (VOLUME_MAX_STORED - VOLUME_FIRST_SOLID), 0.0, 1.0);
}

// Where a ray from the origin enters and leaves a box, as distances along it; enter > leave when
// it misses
vec2 volumeRayBox(vec3 direction, vec3 boxMin, vec3 boxMax) {
    vec3 inverse = 1.0 / direction;
    vec3 t0 = boxMin * inverse;
    vec3 t1 = boxMax * inverse;
    vec3 near = min(t0, t1);
    vec3 far = max(t0, t1);
    return vec2(max(max(near.x, near.y), near.z), min(min(far.x, far.y), far.z));
}

// Which axis a ray from the origin enters a box across: the one whose slab it enters last
int volumeEntryAxis(vec3 direction, vec3 boxMin, vec3 boxMax) {
    vec3 near = min(boxMin / direction, boxMax / direction);
    return near.x >= near.y && near.x >= near.z ? 0 : (near.y >= near.z ? 1 : 2);
}

// A walk along a ray from the origin through a brick's voxels, one at a time (Amanatides and Woo
// 1987): the voxel it is in, which way it steps along each axis, how far along the ray it crosses
// into the next voxel along each axis, and how far apart those crossings are
struct VolumeWalk {
    ivec3 voxel;
    ivec3 stepTo;
    vec3 nextCrossing;
    vec3 crossingSpacing;
};

// Start a walk at a distance along the ray, which must be inside the brick
VolumeWalk volumeWalkStart(vec3 direction, vec3 corner, float distance) {
    VolumeWalk walk;
    vec3 local = direction * distance - corner;
    walk.voxel = clamp(ivec3(floor(local)), ivec3(0), ivec3(VOLUME_BRICK - 1));
    walk.stepTo = ivec3(greaterThanEqual(direction, vec3(0.0))) * 2 - 1;
    vec3 inverse = 1.0 / direction;
    vec3 boundary = vec3(walk.voxel) + vec3(greaterThanEqual(direction, vec3(0.0)));
    walk.nextCrossing = distance + (boundary - local) * inverse;
    walk.crossingSpacing = abs(inverse);
    return walk;
}

// Where the walk next crosses into another voxel, as a distance along the ray
float volumeWalkNext(VolumeWalk walk) {
    return min(min(walk.nextCrossing.x, walk.nextCrossing.y), walk.nextCrossing.z);
}

// Step the walk into the next voxel, giving the axis it crossed
int volumeWalkStep(inout VolumeWalk walk) {
    vec3 c = walk.nextCrossing;
    int axis = c.x <= c.y && c.x <= c.z ? 0 : (c.y <= c.z ? 1 : 2);
    walk.voxel[axis] += walk.stepTo[axis];
    walk.nextCrossing[axis] += walk.crossingSpacing[axis];
    return axis;
}

// A cell's bit in a cell mask
uint volumeCellIndex(ivec3 cell) {
    return uint(cell.x + VOLUME_CELLS * (cell.y + VOLUME_CELLS * cell.z));
}

// Whether a cell's bit is set in a 64-bit cell mask
bool volumeCellSet(uvec2 mask, ivec3 cell) {
    uint index = volumeCellIndex(cell);
    return ((mask[index >> 5] >> (index & 31u)) & 1u) != 0u;
}

// Skip the walk past the cell it is in, which holds nothing it looks for: on to where the ray
// leaves the cell, giving that distance, and through the axis it crossed
float volumeWalkSkipCell(inout VolumeWalk walk, vec3 direction, vec3 corner, out int axis) {
    vec3 cellMin = corner + vec3((walk.voxel / VOLUME_CELL) * VOLUME_CELL);
    vec3 far = max(cellMin / direction, (cellMin + float(VOLUME_CELL)) / direction);
    axis = far.x <= far.y && far.x <= far.z ? 0 : (far.y <= far.z ? 1 : 2);
    float leave = far[axis];
    // The voxel just past the cell's face, along the axis crossed
    ivec3 cell = walk.voxel / VOLUME_CELL;
    VolumeWalk next = volumeWalkStart(direction, corner, leave);
    next.voxel[axis] = walk.stepTo[axis] > 0
        ? (cell[axis] + 1) * VOLUME_CELL
        : cell[axis] * VOLUME_CELL - 1;
    // It starts on that voxel's face along the axis, so crosses out a whole voxel further on
    next.nextCrossing[axis] = leave + next.crossingSpacing[axis];
    walk = next;
    return leave;
}

// Whether the walk has left the brick
bool volumeWalkOutside(VolumeWalk walk) {
    return any(lessThan(walk.voxel, ivec3(0))) || any(greaterThanEqual(walk.voxel, ivec3(VOLUME_BRICK)));
}


#ifdef VOLUME_SET
layout(set = VOLUME_SET, binding = 1) uniform sampler3D volumeAtlas;

// Each slot's cell masks: x and y which cells hold something solid, z and w which hold anything
layout(std430, set = VOLUME_SET, binding = 2) readonly buffer VolumeCellMasks {
    uvec4 volumeCellMasks[];
};

// Each voxel's material, two 16-bit values a word: 0 for none, or the material's index plus 1
layout(std430, set = VOLUME_SET, binding = 3) readonly buffer VolumeMaterials {
    uint volumeMaterialPairs[];
};

// A solid voxel's material index plus 1, or 0 for none
uint volumeMaterial(uint slot, ivec3 voxel) {
    uint at = slot * uint(VOLUME_BRICK * VOLUME_BRICK * VOLUME_BRICK)
        + uint(voxel.x + VOLUME_BRICK * (voxel.y + VOLUME_BRICK * voxel.z));
    return (volumeMaterialPairs[at >> 1] >> ((at & 1u) * 16u)) & 0xFFFFu;
}

// One voxel of a brick as stored: its color, and its extinction or solid coverage, see volumeDecode
vec4 volumeStored(uint slot, ivec3 voxel) {
    return texelFetch(volumeAtlas, volumeSlotOrigin(slot) + voxel, 0);
}

// A brick's extinction at a point, in voxels from its corner, or none outside it
float volumeDensity(uint slot, vec3 local) {
    if (any(lessThan(local, vec3(0.0))) || any(greaterThanEqual(local, vec3(float(VOLUME_BRICK))))) {
        return 0.0;
    }
    return volumeDecode(volumeStored(slot, ivec3(floor(local))).a);
}
#endif

#endif
