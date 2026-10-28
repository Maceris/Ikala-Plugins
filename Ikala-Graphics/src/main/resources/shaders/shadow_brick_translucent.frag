#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Volume bricks in a cascade's translucent maps, see translucent_shadow.glsl and shadow_brick.frag:
// from where the map's ray through this fragment enters the brick's box, the brick is walked a
// voxel at a time to its first solid voxel, which the shadow map itself stops the light at, and
// each see-through voxel on the way, like far glass and water, adds its absorbance and the moments
// of its depth, as a pane does in translucent_shadow.frag: over the length crossed, it covers
// 1 - exp(-extinction * length) and lets through its color of that.

#define VOLUME_SET 2
#include "volume.glsl"

layout(location = 0) in vec3 outRender;
layout(location = 1) flat in vec3 outCorner;
layout(location = 2) flat in uint outSlot;
layout(location = 3) flat in vec3 outDirection;
layout(location = 4) flat in float outDepthStep;
layout(location = 5) flat in float outScale;

// The moments of the voxels' depths, depth to the first through fourth power, times absorbance
layout(location = 0) out vec4 outMoments;
// Their absorbance per color, then as one number, the average
layout(location = 1) out vec4 outAbsorbance;

// The least a voxel lets through, so the absorbance stays finite, as for panes
const float MIN_THROUGH = 1e-3;

void main()
{
    // No component exactly 0, which would make 0 times infinity where the ray lies in a face
    vec3 direction = mix(outDirection, vec3(1e-20), equal(outDirection, vec3(0.0)));
    // The walk's rays start at this fragment's point; distances along the ray are in voxels
    vec3 corner = (outCorner - outRender) / outScale;
    vec2 span = volumeRayBox(direction, corner, corner + float(VOLUME_BRICK));
    float start = max(span.x, 0.0);
    float end = span.y;
    if (!(start < end)) {
        discard;
    }
    VolumeWalk walk = volumeWalkStart(direction, corner, start);
    int axis = 0;
    // The cells holding anything
    uvec2 filledCells = volumeCellMasks[outSlot].zw;
    vec4 moments = vec4(0.0);
    vec4 absorbance = vec4(0.0);
    float t = start;
    for (int i = 0; i < VOLUME_MAX_VOXELS; ++i) {
        if (!volumeCellSet(filledCells, walk.voxel / VOLUME_CELL)) {
            t = volumeWalkSkipCell(walk, direction, corner, axis);
            if (t >= end || volumeWalkOutside(walk)) {
                break;
            }
            continue;
        }
        float leave = min(volumeWalkNext(walk), end);
        vec4 stored = volumeStored(outSlot, walk.voxel);
        if (volumeIsSolid(stored.a)) {
            break;
        }
        float extinction = volumeDecode(stored.a);
        if (extinction > 0.0) {
            float alpha = 1.0 - exp(-extinction * (leave - t) * outScale);
            vec3 through = max(1.0 - alpha + alpha * clamp(stored.rgb, 0.0, 1.0),
                vec3(MIN_THROUGH));
            vec3 voxelAbsorbance = -log(through);
            float total = dot(voxelAbsorbance, vec3(1.0 / 3.0));
            float z = gl_FragCoord.z + outDepthStep * (0.5 * (t + leave)) * outScale;
            float z2 = z * z;
            moments += total * vec4(z, z2, z2 * z, z2 * z2);
            absorbance += vec4(voxelAbsorbance, total);
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
    if (absorbance.w <= 0.0) {
        discard;
    }
    outMoments = moments;
    outAbsorbance = absorbance;
}
