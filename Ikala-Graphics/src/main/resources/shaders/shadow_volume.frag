#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

#include "coverage_bricks.glsl"

// Coverage volumes in a shadow map: scene_volume.frag's walk, along the direction the map looks,
// from where the ray enters the brick's box, writing the depth where its stochastic coverage stops
// it, so light gets through as much as the volume lets it on average. The depth goes a little past
// that, since the scene's walk stops at a random depth of its own: lit surfaces of a volume would
// otherwise shadow themselves about half the time.

layout(location = 0) in vec3 outRender;
layout(location = 1) flat in vec3 outCorner;
layout(location = 2) flat in vec4 outTurn;
layout(location = 3) flat in vec2 outCell;
layout(location = 4) flat in uint outSlot;
layout(location = 5) flat in vec3 outDirection;
layout(location = 6) flat in float outDepthStep;

layout(push_constant) uniform ShadowConstants {
    layout(offset = 88) Bricks bricks;
    uint frame;
};

// Match CoverageBricks
const int SIDE = BRICK_SIDE;
const int MAX_STEPS = 3 * SIDE;
// How far past its stop a volume's shadow starts, in voxels
const float SHADOW_OFFSET = 1.5;

vec3 rotate(vec4 q, vec3 v)
{
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

vec3 unrotate(vec4 q, vec3 v)
{
    return rotate(vec4(-q.xyz, q.w), v);
}

uint pcg(uint v)
{
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

mat3 sggx(uvec4 voxel)
{
    vec4 a = unpackUnorm4x8(voxel.z);
    vec4 b = unpackUnorm4x8(voxel.w);
    vec3 sigma = a.xyz;
    float xy = (a.w * 2.0 - 1.0) * sigma.x * sigma.y;
    float xz = (b.x * 2.0 - 1.0) * sigma.x * sigma.z;
    float yz = (b.y * 2.0 - 1.0) * sigma.y * sigma.z;
    return mat3(sigma.x * sigma.x, xy, xz, xy, sigma.y * sigma.y, yz, xz, yz, sigma.z * sigma.z);
}

void main()
{
    float cell = outCell.x;
    float scale = outCell.y;
    vec3 direction = outDirection;
    // Walk from well before the box, along the map's direction, through this fragment's point
    float back = 4.0 * float(SIDE) * cell;
    vec3 origin = outRender - direction * back;
    vec3 localDirection = unrotate(outTurn, direction) / cell;
    vec3 localOrigin = unrotate(outTurn, origin - outCorner) / cell;
    // No component exactly 0, which would make 0 times infinity where the ray lies in a face
    vec3 safe = mix(localDirection, vec3(1e-20), equal(localDirection, vec3(0.0)));
    vec3 inverse = 1.0 / safe;
    vec3 low = -localOrigin * inverse;
    vec3 high = (vec3(float(SIDE)) - localOrigin) * inverse;
    vec3 nearest = min(low, high);
    vec3 farthest = max(low, high);
    float enter = max(max(nearest.x, nearest.y), max(nearest.z, 0.0));
    float leave = min(min(farthest.x, farthest.y), farthest.z);
    if (!(enter < leave)) {
        // Missed, or not a number
        discard;
    }
    uint seed = pcg(uint(gl_FragCoord.x) ^ (uint(gl_FragCoord.y) << 16) ^ pcg(frame ^ (outSlot << 8)));
    float threshold = -log(max(1.0 - float(seed >> 8) / 16777216.0, 1e-6));

    vec3 point = localOrigin + localDirection * enter;
    ivec3 voxel = clamp(ivec3(floor(point)), ivec3(0), ivec3(SIDE - 1));
    ivec3 step = ivec3(sign(localDirection));
    vec3 delta = abs(inverse);
    vec3 next = (vec3(voxel) + max(vec3(step), vec3(0.0)) - localOrigin) * inverse;
    vec3 modelDirection = unrotate(outTurn, direction);
    float depth = 0.0;
    float t = enter;
    for (int i = 0; i < MAX_STEPS; ++i) {
        float exit = min(min(next.x, next.y), min(next.z, leave));
        uvec4 data = brickVoxel(bricks, outSlot, voxel.x + SIDE * (voxel.y + SIDE * voxel.z));
        float density = unpackHalf2x16(data.x).x;
        if (density > 0.0) {
            mat3 S = sggx(data);
            float extinction = density * sqrt(max(dot(modelDirection, S * modelDirection), 0.0));
            float through = extinction * (exit - t) / scale;
            if (depth + through >= threshold) {
                float hit = t + (threshold - depth) / max(extinction, 1e-6) * scale;
                // This fragment's point is back along the ray from the walk's origin
                float fragmentDepth = gl_FragCoord.z
                    + outDepthStep * (hit + SHADOW_OFFSET * cell - back);
                if (!(fragmentDepth >= 0.0 && fragmentDepth <= 1.0)) {
                    discard;
                }
                gl_FragDepth = fragmentDepth;
                return;
            }
            depth += through;
        }
        if (exit >= leave) {
            break;
        }
        t = exit;
        if (next.x <= next.y && next.x <= next.z) {
            voxel.x += step.x;
            next.x += delta.x;
        } else if (next.y <= next.z) {
            voxel.y += step.y;
            next.y += delta.y;
        } else {
            voxel.z += step.z;
            next.z += delta.z;
        }
        if (any(lessThan(voxel, ivec3(0))) || any(greaterThanEqual(voxel, ivec3(SIDE)))) {
            break;
        }
    }
    discard;
}
