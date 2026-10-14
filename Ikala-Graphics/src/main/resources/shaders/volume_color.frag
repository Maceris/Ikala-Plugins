#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Walks a volume brick a voxel at a time, from where the pixel's ray enters it to where it leaves or
// meets the opaque scene, and adds what it sends toward the camera to the transparency targets, as
// translucent.frag adds a surface: each voxel's color times its opacity times its weight, and the
// weight times the opacity, so the resolve averages it with every other layer; and its extinction,
// whose sum gives how much of the background shows through. The weight is how much light gets
// through from the camera to the brick, from the transparency volume (which the splat filled with
// this brick too) or the depth weight, times how much gets through inside the brick so far.
//
// A solid voxel, inside a cut out block like leaves, ends the walk: the face the ray crossed into
// it is lit as a block's face, textured as its mesh is, by the sun on it through the shadow
// cascades and the sky map past them, and by the ambient light as far as the sky shows. Where its
// texture is cut out, the ray goes on through the block, see volume_face.glsl. Other voxels, of plants, water and glass,
// let light through by how far the ray goes through them, lit as surfaces facing every way. The
// brick shades itself too: what it holds a little way toward the sun, and a little way up, dims
// the sun and the sky light, so the inside and underside of a canopy go dark.

#include "lighting.glsl"
#define OIT_VOLUME_SET 3
#include "oit_volume.glsl"
#define VOLUME_SET 4
#include "volume.glsl"
#include "volume_face.glsl"

layout(location = 0) in vec3 outRenderPosition;
layout(location = 1) flat in uint outDraw;

layout(push_constant) uniform VolumeConstants {
    VolumeDraws draws;
    // The size of the area drawn, in pixels
    vec2 screenSize;
    // SLICES / ln(viewDistance / FIRST_DEPTH)
    float logScale;
    // Nonzero to weight by the transparency volume, zero for the depth weights
    int voxelWeights;
    // How many tiles across and down this frame's volume holds
    vec2 tiles;
    // The frame number, for the noise
    uint frame;
    // Where the hand over from sections' own blocks starts, and how long it is, in meters
    float fadeStart;
    float fadeLength;
    // Each pixel's nearest solid face, see volume_depth.frag
    VolumeHits hitBuffer;
};

// How far past the nearest solid face the walk still goes, as a share of its distance, so the
// brick it is in still finds it after rounding
// How far past the opaque scene's depth the walk still goes, as a share of its distance: about as
// far as the depth buffer can be off far away
const float DEPTH_SLACK = 0.01;
const float HIT_SLACK = 1e-4;

layout(location = 0) out vec4 accum;
// Red: the extinction, which the resolve composites with; green, which TAA reads for surfaces it
// can't follow, stays 0, since bricks stand in for blocks that don't move
layout(location = 1) out vec2 extinction;

// How much of the sun a voxel's surfaces facing every way scatter toward the camera, over their
// albedo: about the half facing the sun are lit, by a cosine on average of a half, over pi
const float SUN_SCATTER = 0.5 / PI;

// Where the brick's self shading samples, in meters toward the light, and how far each sample
// stands for
const float SHADE_NEAR = 1.0;
const float SHADE_FAR = 2.5;
const float SHADE_LENGTH = 1.25;
const vec3 UP = vec3(0.0, 1.0, 0.0);
// How far out from a solid voxel's face its self shading looks from, in voxels: the middle of the
// voxel in front
const float FACE_OFFSET = 0.5;
// Weighted blended OIT's depth weight, as translucent.frag
float depthWeight(float alpha, float depth) {
    return alpha * clamp(10.0 / (1e-5 + pow(depth / 5.0, 2.0) + pow(depth / 200.0, 6.0)), 1e-2, 3e3);
}

// A point in render space in view space: the view is a rotation and a move, so the inverse's
// rotation turned back undoes it
vec3 renderToView(vec3 renderPoint) {
    return transpose(mat3(invViewMatrix)) * (renderPoint - invViewMatrix[3].xyz);
}

// How much gets through what a brick holds a little way from a point toward a direction
float selfShade(uint slot, vec3 local, vec3 toward) {
    float held = volumeDensity(slot, local + toward * SHADE_NEAR)
        + volumeDensity(slot, local + toward * SHADE_FAR);
    return exp(-held * SHADE_LENGTH);
}

// How much of the sun (x) and the sky (y) reach a point, from the shadow cascades, the sky map past
// them, and the brick itself
vec2 visibility(uint slot, vec3 renderPoint, vec3 normal, vec3 local, vec3 renderSun,
    float shadowDistance) {
    vec3 viewPoint = renderToView(renderPoint);
    float sunVisible = sunVisibility(viewPoint, renderPoint, normal);
    float sky = skyVisibility(renderPoint, normal);
    if (-viewPoint.z >= shadowDistance) {
        sunVisible = min(sunVisible, sky);
    }
    return vec2(sunVisible * selfShade(slot, local, renderSun), sky * selfShade(slot, local, UP));
}

void main() {
    VolumeDraw draw = draws.volumeDraws[outDraw];
    vec3 boxMax = draw.corner + float(VOLUME_BRICK);
    vec3 direction = normalize(outRenderPosition);
    vec2 span = volumeRayBox(direction, draw.corner, boxMax);
    // Nothing before the hand over starts is the brick's to draw
    float start = max(span.x, max(fadeStart, 0.0));
    float end = span.y;
    // The opaque scene stops the ray
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)], pixel, 0).x;
    if (depth < 1.0) {
        vec2 uv = gl_FragCoord.xy / screenSize;
        vec4 view = invProjectionMatrix * vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, depth, 1.0);
        // Past it by a little: the depth also holds the bricks' own solid faces, written by
        // volume_depth.frag, less precisely than their distances in the hit buffer
        end = min(end, length(view.xyz / view.w) * (1.0 + DEPTH_SLACK));
    }
    // A brick's solid face nearer than the opaque scene stops the ray too
    float hit = uintBitsToFloat(hitBuffer.hits[uint(pixel.x) + uint(screenSize.x) * uint(pixel.y)]);
    end = min(end, hit * (1.0 + HIT_SLACK));
    if (end <= start) {
        discard;
    }

    vec3 renderSun = normalize((invViewMatrix * vec4(-directionalLight.direction, 0.0)).xyz);
    vec3 sun = directionalLight.color * directionalLight.intensity;
    vec3 ambient = ambientLight.color * ambientLight.intensity;
    float shadowDistance = -cascadeShadowSplits[NUM_CASCADES - 1].splitDistance;

    // How much gets through from the camera to the brick
    float entryDepth = -renderToView(direction * start).z;
    float inFront = voxelWeights != 0
        ? transmittanceInFront(gl_FragCoord.xy, entryDepth, logScale, tiles)
        : 1.0;
    float through = 1.0;
    vec4 sum = vec4(0.0);
    float optical = 0.0;
    VolumeWalk walk = volumeWalkStart(direction, draw.corner, start);
    // The axis of the face the ray last crossed into a voxel through
    int axis = volumeEntryAxis(direction, draw.corner, boxMax);
    // The cells holding anything
    uvec2 filledCells = volumeCellMasks[draw.slot].zw;
    float t = start;
    for (int i = 0; i < VOLUME_MAX_VOXELS; ++i) {
        if (!volumeCellSet(filledCells, walk.voxel / VOLUME_CELL)) {
            t = volumeWalkSkipCell(walk, direction, draw.corner, axis);
            if (t >= end || volumeWalkOutside(walk)) {
                break;
            }
            continue;
        }
        float leave = min(volumeWalkNext(walk), end);
        vec4 stored = volumeStored(draw.slot, walk.voxel);
        vec4 voxel = vec4(stored.rgb, volumeDecode(stored.a));
        if (voxel.a > 0.0 && volumeTakes(gl_FragCoord.xy, frame, t, fadeStart, fadeLength)) {
            if (volumeIsSolid(stored.a)) {
                // A block's face, textured as its mesh is: lit as one, and nothing behind it
                // shows, unless the ray meets a hole cut in it and goes on through the block
                vec3 normal = vec3(0.0);
                normal[axis] = -float(walk.stepTo[axis]);
                vec3 hit = direction * t;
                vec4 texel;
                bool textured = volumeFaceTexel(draw.slot, walk.voxel, hit - draw.corner, axis,
                    normal[axis], direction, t, screenSize.y, texel);
                if (!textured || !volumeFaceHole(texel)) {
                    vec3 local = hit - draw.corner + normal * FACE_OFFSET;
                    vec2 seen = visibility(draw.slot, hit, normal, local, renderSun,
                        shadowDistance);
                    float facing = max(dot(normal, renderSun), 0.0);
                    vec3 light = sun * (facing / PI) * seen.x
                        + ambient * mix(skyLightFloor, 1.0, seen.y);
                    // Without a texture, the face's holes show the canopy's inside, which only
                    // the dim light under cover reaches, by how much they cover
                    vec3 under = ambient * skyLightFloor;
                    vec3 albedo = textured ? sRGBToLinear(texel.rgb) : voxel.rgb;
                    float cover = textured ? 1.0 : volumeCoverage(stored.a);
                    vec3 color = albedo * mix(under, light, cover);
                    float weight = voxelWeights != 0
                        ? inFront * through
                        : depthWeight(1.0, -renderToView(hit).z);
                    sum += vec4(color * weight, weight);
                    optical += VOLUME_SOLID_DEPTH;
                    break;
                }
            } else {
                float segment = leave - t;
                float alpha = 1.0 - exp(-voxel.a * segment);
                vec3 renderPoint = direction * (t + 0.5 * segment);
                vec2 seen = visibility(draw.slot, renderPoint, UP, renderPoint - draw.corner,
                    renderSun, shadowDistance);
                vec3 color = voxel.rgb * (sun * SUN_SCATTER * seen.x
                    + ambient * mix(skyLightFloor, 1.0, seen.y));
                float weight = voxelWeights != 0
                    ? inFront * through
                    : depthWeight(alpha, -renderToView(renderPoint).z);
                sum += vec4(color * alpha * weight, alpha * weight);
                optical += voxel.a * segment;
                through *= 1.0 - alpha;
                if (through < VOLUME_OPAQUE_ENOUGH) {
                    break;
                }
            }
        }
        if (leave >= end) {
            break;
        }
        t = leave;
        axis = volumeWalkStep(walk);
        if (volumeWalkOutside(walk)) {
            break;
        }
    }
    if (sum.a <= 0.0) {
        discard;
    }
    accum = sum;
    extinction = vec2(optical, 0.0);
}
