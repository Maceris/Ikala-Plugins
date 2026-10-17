#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

#include "roughening.glsl"

// Coverage volumes, see CoverageVolume and CoverageBricks: each fragment of a brick's box walks
// the brick a voxel at a time (Amanatides and Woo 1987), each voxel's microflakes stopping light by
// their density times the area they project along the ray, sqrt(w^T S w) for the voxel's SGGX
// matrix S (Heitz et al., The SGGX Microflake Distribution, 2015). Coverage is stochastic: the
// walk stops where the light stopped so far passes a random threshold for the pixel and frame, and
// writes a surface there into the g-buffer, as hashed alpha testing does, so temporal
// antialiasing averages the coverage. The surface's normal is the mean of a few samples of the
// microflakes' visible normals (the paper's algorithm 1): lit by lights.frag as a diffuse surface,
// one sample would be one sample of the diffuse microflake phase function (its equation 26), but
// so noisy that temporal antialiasing's history clamping keeps the noise; the mean shades smoothly,
// a little brighter than the true average. The samples' spread widens the roughness, see
// roughening.glsl, so the mean doesn't shine sharper than the microflakes it stands for.
//
// Boxes are drawn by their back faces, so a camera inside one still sees it, and the walk starts
// where the ray enters the box, or at the camera.

layout(location = 0) in vec3 outRender;
layout(location = 1) flat in vec3 outCorner;
layout(location = 2) flat in vec4 outTurn;
layout(location = 3) flat in vec2 outCell;
layout(location = 4) flat in uint outSlot;

layout(location = 0) out vec4 buffBaseColor;
layout(location = 1) out vec4 buffNormal;
layout(location = 2) out vec4 buffTangent;
layout(location = 3) out uint buffMaterial;
layout(location = 4) out vec4 buffEmission;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

// A brick: a header (x: voxel size bits), then its voxels, x fastest, see CoverageBricks
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Bricks {
    uvec4 words[];
};

layout(push_constant) uniform SceneConstants {
    layout(offset = 24) uint frame;
    uint debugSources;
    Bricks bricks;
};

// Match CoverageBricks
const int SIDE = 8;
const uint WORDS_PER_BRICK = 1u + uint(SIDE * SIDE * SIDE);
// The most voxels a ray crosses in a brick
const int MAX_STEPS = 3 * SIDE;
// The specular a volume's surfaces get, as materials' default
const float SPECULAR = 0.5;
const float TWO_PI = 6.28318530718;
// Samples of the visible normals the surface's normal is the mean of
const int NORMAL_SAMPLES = 8;

vec3 rotate(vec4 q, vec3 v)
{
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

vec3 unrotate(vec4 q, vec3 v)
{
    return rotate(vec4(-q.xyz, q.w), v);
}

// A well spread hash (PCG), for the pixel's random numbers
uint pcg(uint v)
{
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float random(inout uint seed)
{
    seed = pcg(seed);
    return float(seed >> 8) / 16777216.0;
}

// A voxel's SGGX matrix from its compact form: sigmas and correlations
mat3 sggx(uvec4 voxel)
{
    vec4 a = unpackUnorm4x8(voxel.z);
    vec4 b = unpackUnorm4x8(voxel.w);
    vec3 sigma = a.xyz;
    float rxy = a.w * 2.0 - 1.0;
    float rxz = b.x * 2.0 - 1.0;
    float ryz = b.y * 2.0 - 1.0;
    float xy = rxy * sigma.x * sigma.y;
    float xz = rxz * sigma.x * sigma.z;
    float yz = ryz * sigma.y * sigma.z;
    return mat3(sigma.x * sigma.x, xy, xz, xy, sigma.y * sigma.y, yz, xz, yz, sigma.z * sigma.z);
}

// A sample of the visible normals of the microflakes S stands for, seen from wi (toward the
// viewer), after Heitz et al.'s algorithm 1
vec3 sampleVisibleNormal(vec3 wi, mat3 S, float u1, float u2)
{
    vec3 wk = abs(wi.x) < 0.9 ? normalize(cross(wi, vec3(1.0, 0.0, 0.0)))
                              : normalize(cross(wi, vec3(0.0, 1.0, 0.0)));
    vec3 wj = cross(wi, wk);
    float skk = dot(wk, S * wk);
    float sjj = dot(wj, S * wj);
    float sii = dot(wi, S * wi);
    float skj = dot(wk, S * wj);
    float ski = dot(wk, S * wi);
    float sji = dot(wj, S * wi);
    float det = skk * sjj * sii - skj * skj * sii - ski * ski * sjj - sji * sji * skk
        + 2.0 * skj * ski * sji;
    float sqrtDet = sqrt(max(det, 1e-12));
    float inverseSqrtSii = 1.0 / sqrt(max(sii, 1e-12));
    float tmp = sqrt(max(sjj * sii - sji * sji, 1e-12));
    vec3 mk = vec3(sqrtDet / tmp, 0.0, 0.0);
    vec3 mj = inverseSqrtSii * vec3(-(ski * sji - skj * sii) / tmp, tmp, 0.0);
    vec3 mi = inverseSqrtSii * vec3(ski, sji, sii);
    float r = sqrt(u1);
    float u = r * cos(TWO_PI * u2);
    float v = r * sin(TWO_PI * u2);
    float w = sqrt(max(0.0, 1.0 - u * u - v * v));
    vec3 m = normalize(u * mk + v * mj + w * mi);
    return normalize(m.x * wk + m.y * wj + m.z * wi);
}

void main()
{
    float cell = outCell.x;
    float scale = outCell.y;
    vec3 direction = normalize(outRender);
    // The ray in the brick's voxels: the camera is at the render origin
    vec3 localDirection = unrotate(outTurn, direction) / cell;
    vec3 localOrigin = unrotate(outTurn, -outCorner) / cell;
    // No component exactly 0, which would make 0 times infinity where the ray lies in a face
    vec3 safe = mix(localDirection, vec3(1e-20), equal(localDirection, vec3(0.0)));
    vec3 inverse = 1.0 / safe;
    vec3 low = (vec3(0.0) - localOrigin) * inverse;
    vec3 high = (vec3(float(SIDE)) - localOrigin) * inverse;
    vec3 nearest = min(low, high);
    vec3 farthest = max(low, high);
    float enter = max(max(nearest.x, nearest.y), max(nearest.z, 0.0));
    float leave = min(min(farthest.x, farthest.y), farthest.z);
    if (!(enter < leave)) {
        // Missed, or not a number
        discard;
    }

    // The pixel's random numbers: the threshold, then the normal's sample
    uint seed = uint(gl_FragCoord.x) ^ (uint(gl_FragCoord.y) << 16) ^ pcg(frame ^ (outSlot << 8));
    float threshold = -log(max(1.0 - random(seed), 1e-6));

    vec3 point = localOrigin + localDirection * enter;
    ivec3 voxel = clamp(ivec3(floor(point)), ivec3(0), ivec3(SIDE - 1));
    ivec3 step = ivec3(sign(localDirection));
    vec3 delta = abs(inverse);
    vec3 next = (vec3(voxel) + max(vec3(step), vec3(0.0)) - localOrigin) * inverse;
    // The microflakes' facing is in the model's axes
    vec3 modelDirection = unrotate(outTurn, direction);
    uint base = outSlot * WORDS_PER_BRICK + 1u;
    float depth = 0.0;
    float t = enter;
    for (int i = 0; i < MAX_STEPS; ++i) {
        float exit = min(min(next.x, next.y), min(next.z, leave));
        uvec4 data = bricks.words[base + uint(voxel.x + SIDE * (voxel.y + SIDE * voxel.z))];
        float density = unpackHalf2x16(data.x).x;
        if (density > 0.0) {
            mat3 S = sggx(data);
            float extinction = density * sqrt(max(dot(modelDirection, S * modelDirection), 0.0));
            // Lengths in the model's units
            float through = extinction * (exit - t) / scale;
            if (depth + through >= threshold) {
                float hit = t + (threshold - depth) / max(extinction, 1e-6) * scale;
                vec3 render = direction * hit;
                vec4 clip = projectionMatrix * viewMatrix * vec4(render, 1.0);
                float fragmentDepth = clip.z / clip.w;
                if (!(fragmentDepth >= 0.0 && fragmentDepth <= 1.0)) {
                    discard;
                }
                gl_FragDepth = fragmentDepth;
                vec3 sampled = vec3(0.0);
                for (int n = 0; n < NORMAL_SAMPLES; ++n) {
                    // Stratified over the disk's angle
                    float angle = (float(n) + random(seed)) / float(NORMAL_SAMPLES);
                    sampled += sampleVisibleNormal(-modelDirection, S, random(seed), angle);
                }
                float spread = length(sampled) / float(NORMAL_SAMPLES);
                sampled = normalize(sampled);
                if (any(isnan(sampled))) {
                    // A matrix too flat to sample: face the viewer
                    sampled = -modelDirection;
                }
                vec3 normal = rotate(outTurn, sampled);
                vec3 viewNormal = normalize(mat3(viewMatrix) * normal);
                if (any(isnan(viewNormal)) || any(isinf(viewNormal))) {
                    viewNormal = normalize(mat3(viewMatrix) * -direction);
                }
                vec3 across = abs(viewNormal.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
                vec3 tangent = normalize(cross(viewNormal, across));
                vec4 extra = unpackUnorm4x8(data.x);
                buffBaseColor = vec4(unpackUnorm4x8(data.y).rgb, SPECULAR);
                buffNormal = vec4(viewNormal, roughenBySpread(extra.z, spread));
                buffTangent = vec4(tangent, extra.w);
                buffMaterial = 0u;
                buffEmission = vec4(0.0, 0.0, 0.0, 1.0);
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
