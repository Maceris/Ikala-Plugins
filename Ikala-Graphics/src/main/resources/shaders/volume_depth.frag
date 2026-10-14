#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Finds the first solid face a volume brick shows at a pixel, walking it a voxel at a time as
// volume_color.frag does, from the front of its box, and writes its depth, depth tested against the opaque scene, so TAA
// follows the blocks the bricks stand in for and translucent surfaces behind them are hidden. Also
// keeps each pixel's nearest such face's distance along its ray, which the splat and color passes
// stop at, so a brick behind another's solid face adds nothing.

#include "light_inputs.glsl"
#define VOLUME_SET 3
#include "volume.glsl"
#include "volume_face.glsl"

layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) flat in uint outDraw;
layout(location = 2) flat in vec4 outDepthTerms;

// Boxes are drawn from the front, and the face found is never in front of the box, so the depth
// test can drop boxes behind the scene, or behind faces already found, before the shader runs
layout(depth_greater) out float gl_FragDepth;

layout(push_constant) uniform VolumeDepthConstants {
    VolumeDraws draws;
    VolumeHits hitBuffer;
    // How many pixels across the hits are, for each pixel's index
    uint hitWidth;
    // The frame number, for the hand over's dither
    uint frame;
    // Where the hand over from sections' own blocks starts, and how long it is, in meters
    float fadeStart;
    float fadeLength;
    // The drawn area's height, in pixels, for the faces' texture mips
    float screenHeight;
};

// A point in render space in view space: the view is a rotation and a move, so the inverse's
// rotation turned back undoes it
vec3 renderToView(vec3 renderPoint) {
    return transpose(mat3(invViewMatrix)) * (renderPoint - invViewMatrix[3].xyz);
}

void main() {
    VolumeDraw draw = draws.volumeDraws[outDraw];
    vec3 boxMax = draw.corner + float(VOLUME_BRICK);
    vec3 direction = normalize(outRenderPosition);
    vec2 span = volumeRayBox(direction, draw.corner, boxMax);
    // Nothing before the hand over starts is the brick's to draw
    float start = max(span.x, max(fadeStart, 0.0));
    // Nothing past a face another brick already found here
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    uint hitIndex = uint(pixel.x) + hitWidth * uint(pixel.y);
    float end = min(span.y, uintBitsToFloat(hitBuffer.hits[hitIndex]));
    if (end <= start) {
        discard;
    }
    VolumeWalk walk = volumeWalkStart(direction, draw.corner, start);
    // The axis of the face the ray last crossed into a voxel through
    int axis = volumeEntryAxis(direction, draw.corner, boxMax);
    // The cells holding something solid
    uvec2 solidCells = volumeCellMasks[draw.slot].xy;
    float t = start;
    bool found = false;
    for (int i = 0; i < VOLUME_MAX_VOXELS; ++i) {
        if (!volumeCellSet(solidCells, walk.voxel / VOLUME_CELL)) {
            t = volumeWalkSkipCell(walk, direction, draw.corner, axis);
            if (t >= end || volumeWalkOutside(walk)) {
                break;
            }
            continue;
        }
        float stored = volumeStored(draw.slot, walk.voxel).a;
        if (volumeIsSolid(stored) && volumeTakes(gl_FragCoord.xy, frame, t, fadeStart, fadeLength)) {
            // Unless the ray meets a hole cut in its face, as the color pass decides
            vec4 texel;
            bool textured = volumeFaceTexel(draw.slot, walk.voxel, direction * t - draw.corner,
                axis, -float(walk.stepTo[axis]), direction, t, screenHeight, texel);
            if (!textured || !volumeFaceHole(texel)) {
                found = true;
                break;
            }
        }
        float leave = min(volumeWalkNext(walk), end);
        if (leave >= end) {
            break;
        }
        t = leave;
        axis = volumeWalkStep(walk);
        if (volumeWalkOutside(walk)) {
            break;
        }
    }
    if (!found) {
        discard;
    }
    float z = renderToView(direction * t).z;
    gl_FragDepth = (outDepthTerms.x * z + outDepthTerms.y) / (outDepthTerms.z * z + outDepthTerms.w);
    atomicMin(hitBuffer.hits[hitIndex], floatBitsToUint(t));
}
