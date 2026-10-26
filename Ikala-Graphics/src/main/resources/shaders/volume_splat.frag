#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Adds a volume brick's extinction into the voxel-based transparency volume, drawn at one pixel per
// tile like oit_splat.frag: walks the tile's middle ray through the brick a voxel at a time, up to
// the opaque scene there, and adds each voxel's extinction times the length through it, in fixed
// point, split between the two slice centers either side of it, up to the first solid voxel,
// whose face hides everything. The color pass walks the same way.

#include "light_inputs.glsl"
#include "oit_volume.glsl"
#define VOLUME_SET 3
#include "volume.glsl"

layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) flat in uint outDraw;

// Each tile's slices, x fastest, then y, then slice
layout(buffer_reference, std430, buffer_reference_align = 4) buffer Extinction {
    uint values[];
};

layout(push_constant) uniform VolumeSplatConstants {
    VolumeDraws draws;
    Extinction extinctionBuffer;
    // How many tiles across and down this frame's volume holds
    uvec2 tiles;
    // The size of the area drawn, in pixels
    vec2 screenSize;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
    // The frame number, for the noise
    uint frame;
    // Where the hand over from sections' own blocks starts, and how long it is, in meters
    float fadeStart;
    float fadeLength;
    // Each pixel's nearest solid face, see volume_depth.frag
    VolumeHits hitBuffer;
};

// How far past the nearest solid face at the tile's middle pixel the walk still goes, as a share of
// its distance: the tile's ray is a little off that pixel's
// How far past the opaque scene's depth the walk still goes, as a share of its distance: about as
// far as the depth buffer can be off far away
const float DEPTH_SLACK = 0.01;
const float HIT_SLACK = 0.01;

// A point in render space in view space: the view is a rotation and a move, so the inverse's
// rotation turned back undoes it
vec3 renderToView(vec3 renderPoint) {
    return transpose(mat3(invViewMatrix)) * (renderPoint - invViewMatrix[3].xyz);
}

void main() {
    VolumeDraw draw = draws.volumeDraws[outDraw];
    uint slot = volumeDrawSlot(draw);
    // Distances along the ray in the brick's voxels, scale meters each
    float scale = volumeDrawScale(draw);
    vec3 corner = draw.corner / scale;
    vec3 boxMax = corner + float(VOLUME_BRICK);
    vec3 direction = normalize(outRenderPosition);
    vec2 span = volumeRayBox(direction, corner, boxMax);
    // Nothing before the hand over starts is the brick's to draw
    float start = max(span.x, max(fadeStart, 0.0) / scale);
    float end = span.y;
    uvec2 tile = uvec2(gl_FragCoord.xy);
    vec2 middle = min((vec2(tile) + 0.5) * float(TILE_SIZE), screenSize - 0.5);
    ivec2 pixel = ivec2(middle);
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)], pixel, 0).x;
    if (depth < 1.0) {
        vec2 uv = middle / screenSize;
        vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, depth, 1.0);
        // Past it by a little: the depth also holds the bricks' own solid faces, written by
        // volume_depth.frag, less precisely than their distances in the hit buffer
        end = min(end, length(view.xyz / view.w) * (1.0 + DEPTH_SLACK) / scale);
    }
    // A brick's solid face nearer than the opaque scene stops the ray too
    float hit = uintBitsToFloat(hitBuffer.hits[uint(pixel.x) + uint(screenSize.x) * uint(pixel.y)]);
    end = min(end, hit * (1.0 + HIT_SLACK) / scale);
    if (end <= start) {
        return;
    }
    uint column = tile.x + tiles.x * tile.y;
    uint sliceStride = tiles.x * tiles.y;
    VolumeWalk walk = volumeWalkStart(direction, corner, start);
    // The cells holding anything
    uvec2 filledCells = volumeCellMasks[slot].zw;
    float t = start;
    for (int i = 0; i < VOLUME_MAX_VOXELS; ++i) {
        if (!volumeCellSet(filledCells, walk.voxel / VOLUME_CELL)) {
            int crossed;
            t = volumeWalkSkipCell(walk, direction, corner, crossed);
            if (t >= end || volumeWalkOutside(walk)) {
                break;
            }
            continue;
        }
        float leave = min(volumeWalkNext(walk), end);
        vec4 stored = volumeStored(slot, walk.voxel);
        vec4 voxel = vec4(stored.rgb, volumeDecode(stored.a));
        if (voxel.a > 0.0 && volumeTakes(gl_FragCoord.xy, frame, t * scale, fadeStart, fadeLength)) {
            // A solid voxel hides everything behind it, at its face
            bool solid = volumeIsSolid(stored.a);
            float at = solid ? t : 0.5 * (t + leave);
            float center = sliceCoordinate(-renderToView(direction * at * scale).z, logScale) - 0.5;
            float lowerSlice = floor(center);
            float fraction = center - lowerSlice;
            float amount = solid ? VOLUME_SOLID_DEPTH : voxel.a * (leave - t) * scale;
            uint total = uint(amount * FIXED_POINT_SCALE + 0.5);
            uint upperPart = uint(float(total) * fraction + 0.5);
            uint lower = uint(clamp(int(lowerSlice), 0, int(SLICES) - 1));
            uint upper = uint(clamp(int(lowerSlice) + 1, 0, int(SLICES) - 1));
            atomicAdd(extinctionBuffer.values[column + sliceStride * lower], total - upperPart);
            atomicAdd(extinctionBuffer.values[column + sliceStride * upper], upperPart);
            if (solid) {
                break;
            }
        }
        if (leave >= end) {
            break;
        }
        t = leave;
        volumeWalkStep(walk);
        if (volumeWalkOutside(walk)) {
            break;
        }
    }
}
