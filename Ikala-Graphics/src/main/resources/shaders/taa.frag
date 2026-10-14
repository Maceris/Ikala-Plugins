#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Temporal anti-aliasing, see Taa: each frame samples its pixels a little apart (TaaMath), and this
// blends the new frame into the history of the ones before, in linear light before tone mapping.
//
// - Each pixel finds where it was last frame from its depth, using the depth nearest the camera
//   around it so edges don't smear.
// - Last frame's depth at the four texels around that point says which of them still show the same
//   surface; history from texels that don't is dropped. A pixel whose surface is outside the whole
//   range of last frame's depths around it was hidden then, and starts over. Thin, cut out surfaces
//   such as leaves and plants land on different depths as the jitter moves, so a mismatch inside
//   that range keeps all four texels and leaves the rest to clipping.
// - History is read with a Catmull-Rom filter where every texel matches, so it doesn't blur over
//   many frames, and clipped toward the spread of the new frame's colors around the pixel, in
//   YCoCg, so changes in lighting don't leave trails. Pixels with a long steady history get a
//   wider clip, so a steady picture isn't clipped back toward the new frame's noise.
// - Each pixel keeps how many frames its history holds in alpha, and blends the new one in by its
//   share of them, but never less than MIN_BLEND. Translucent surfaces have no history of their
//   own, so where they cover the pixel it blends faster and clips tighter.
// - Colors are weighted by 1 / (1 + luma) as they blend, so a single bright sample can't flare.

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform Constants {
    // From this frame's clip space, without jitter, to last frame's
    mat4 reprojection;
    // Bindless slots: the new frame's color, the history, this frame's and last frame's depth, and
    // the transparency pass's summed extinction
    int colorIndex;
    int historyIndex;
    int depthIndex;
    int previousDepthIndex;
    int extinctionIndex;
    // FLAG_HISTORY: the history can be read; FLAG_VIEW: show how much history each pixel has
    int flags;
    // The size of the area drawn, in pixels
    vec2 screenSize;
    // What the colors will be multiplied by before tone mapping
    float exposure;
};

layout(set = 0, binding = 0) uniform sampler2D bindlessTextures[];

const int FLAG_HISTORY = 1;
const int FLAG_VIEW = 2;

// The most frames history counts, and the least share a new frame gets
const float MAX_SAMPLES = 16.0;
const float MIN_BLEND = 0.1;
// How much a pixel fully covered by translucent surfaces blends in each frame
const float REACTIVE_BLEND = 0.5;
// How far the clip reaches, in standard deviations: for a new or moving pixel, for one with a long
// steady history, and how much tighter it gets under translucent surfaces
const float CLIP_GAMMA = 1.0;
const float STEADY_CLIP_GAMMA = 1.25;
const float REACTIVE_CLIP_SCALE = 0.75;
// How far a texel's depth may be from where the point should be, as a share of its distance plus
// how much depth changes across a pixel there, before it counts as another surface
const float DEPTH_TOLERANCE = 0.02;
const float SLOPE_TOLERANCE = 1.5;
// Less matching texel weight than this, and the pixel starts over
const float MIN_HISTORY_WEIGHT = 0.5;
// Match Projection.Z_NEAR and Z_FAR
const float Z_NEAR = 0.01;
const float Z_FAR = 1000.0;

// The distance in front of the camera of a point at a depth buffer value
float linearDepth(float depth)
{
    return Z_NEAR * Z_FAR / (Z_FAR - depth * (Z_FAR - Z_NEAR));
}

vec3 toYCoCg(vec3 c)
{
    return vec3(0.25 * c.r + 0.5 * c.g + 0.25 * c.b, 0.5 * c.r - 0.5 * c.b,
        -0.25 * c.r + 0.5 * c.g - 0.25 * c.b);
}

vec3 fromYCoCg(vec3 c)
{
    return vec3(c.x + c.y - c.z, c.x + c.z, c.x - c.y - c.z);
}

float luma(vec3 c)
{
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Sample at a pixel position, in pixels from the drawn area's corner, through linear filtering
vec4 sampleAt(int index, vec2 pixel)
{
    vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(index)], 0));
    return textureLod(bindlessTextures[nonuniformEXT(index)], pixel / size, 0.0);
}

// The history at a pixel position through a Catmull-Rom filter, from five bilinear samples
// (Jimenez 2016), the outer corners left out
vec3 catmullRom(int index, vec2 pixel)
{
    vec2 position1 = floor(pixel - 0.5) + 0.5;
    vec2 f = pixel - position1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 position0 = position1 - 1.0;
    vec2 position3 = position1 + 2.0;
    vec2 position12 = position1 + w2 / w12;
    vec3 sum = sampleAt(index, vec2(position12.x, position0.y)).rgb * (w12.x * w0.y)
        + sampleAt(index, vec2(position0.x, position12.y)).rgb * (w0.x * w12.y)
        + sampleAt(index, position12).rgb * (w12.x * w12.y)
        + sampleAt(index, vec2(position3.x, position12.y)).rgb * (w3.x * w12.y)
        + sampleAt(index, vec2(position12.x, position3.y)).rgb * (w12.x * w3.y);
    float weight = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
    return max(sum / weight, vec3(0.0));
}

// Clip a color toward the middle of a box, so it ends on the box's surface if it was outside
vec3 clipToBox(vec3 color, vec3 middle, vec3 extent)
{
    vec3 offset = color - middle;
    vec3 units = abs(offset / max(extent, vec3(1e-5)));
    float largest = max(units.x, max(units.y, units.z));
    return largest > 1.0 ? middle + offset / largest : color;
}

void main()
{
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 last = ivec2(screenSize) - 1;
    vec3 current = texelFetch(bindlessTextures[nonuniformEXT(colorIndex)], pixel, 0).rgb;

    // The colors around the pixel, and the depth nearest the camera among them
    vec3 moment1 = vec3(0.0);
    vec3 moment2 = vec3(0.0);
    float centerDepth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], pixel, 0).x;
    float nearestDepth = centerDepth;
    ivec2 nearest = pixel;
    float centerDistance = linearDepth(centerDepth);
    float slope = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            ivec2 at = clamp(pixel + ivec2(x, y), ivec2(0), last);
            vec3 color = toYCoCg(texelFetch(bindlessTextures[nonuniformEXT(colorIndex)], at, 0).rgb);
            moment1 += color;
            moment2 += color * color;
            float depth = texelFetch(bindlessTextures[nonuniformEXT(depthIndex)], at, 0).x;
            if (depth < nearestDepth) {
                nearestDepth = depth;
                nearest = at;
            }
            slope = max(slope, abs(linearDepth(depth) - centerDistance));
        }
    }
    vec3 mean = moment1 / 9.0;
    vec3 deviation = sqrt(max(moment2 / 9.0 - mean * mean, vec3(0.0)));

    // Translucent surfaces over the pixel, which history can't follow; volume bricks, which it can,
    // are left out of this channel
    float extinction = texelFetch(bindlessTextures[nonuniformEXT(extinctionIndex)], pixel, 0).y;
    float reactive = 1.0 - exp(-max(extinction, 0.0));

    // Where the pixel was last frame: moved as the nearest surface around it moved. The scene is
    // drawn with OpenGL style y up, but this quad isn't flipped, so y is flipped both ways.
    vec2 nearestUv = (vec2(nearest) + 0.5) / screenSize;
    vec4 nearestClip = vec4(nearestUv.x * 2.0 - 1.0, 1.0 - nearestUv.y * 2.0, nearestDepth, 1.0);
    vec4 nearestPrevious = reprojection * nearestClip;
    vec2 nearestPreviousUv = vec2(nearestPrevious.x, -nearestPrevious.y) / nearestPrevious.w * 0.5
        + 0.5;
    vec2 previousUv = outTextCoord + (nearestPreviousUv - nearestUv);
    // And how far away this pixel's own surface was then
    vec4 ownClip = vec4(outTextCoord.x * 2.0 - 1.0, 1.0 - outTextCoord.y * 2.0, centerDepth, 1.0);
    vec4 ownPrevious = reprojection * ownClip;
    float expectedDistance = linearDepth(ownPrevious.z / ownPrevious.w);

    float samples = 0.0;
    vec3 history = current;
    bool steady = false;
    bool onScreen = all(greaterThanEqual(previousUv, vec2(0.0)))
        && all(lessThanEqual(previousUv, vec2(1.0)));
    if ((flags & FLAG_HISTORY) != 0 && onScreen) {
        // The four texels around the point, each kept if it shows the same surface
        vec2 previousPixel = previousUv * screenSize;
        vec2 corner = floor(previousPixel - 0.5);
        vec2 f = previousPixel - 0.5 - corner;
        float tolerance = DEPTH_TOLERANCE * expectedDistance + SLOPE_TOLERANCE * slope;
        float totalWeight = 0.0;
        vec4 weighted = vec4(0.0);
        int matched = 0;
        for (int i = 0; i < 4; ++i) {
            ivec2 offset = ivec2(i & 1, i >> 1);
            ivec2 texel = clamp(ivec2(corner) + offset, ivec2(0), last);
            float bilinear = (offset.x == 1 ? f.x : 1.0 - f.x) * (offset.y == 1 ? f.y : 1.0 - f.y);
            float distance = linearDepth(
                texelFetch(bindlessTextures[nonuniformEXT(previousDepthIndex)], texel, 0).x);
            if (abs(distance - expectedDistance) <= tolerance) {
                ++matched;
                totalWeight += bilinear;
                weighted += bilinear
                    * texelFetch(bindlessTextures[nonuniformEXT(historyIndex)], texel, 0);
            }
        }
        if (totalWeight >= MIN_HISTORY_WEIGHT) {
            vec4 kept = weighted / totalWeight;
            samples = kept.a;
            history = matched == 4 ? catmullRom(historyIndex, previousPixel) : kept.rgb;
            steady = matched == 4 && samples >= MAX_SAMPLES * 0.5;
        } else {
            // Was the surface hidden last frame, or only between the depths the jitter saw?
            float nearestBefore = Z_FAR;
            float farthestBefore = 0.0;
            for (int y = -1; y <= 2; ++y) {
                for (int x = -1; x <= 2; ++x) {
                    ivec2 texel = clamp(ivec2(corner) + ivec2(x, y), ivec2(0), last);
                    float distance = linearDepth(
                        texelFetch(bindlessTextures[nonuniformEXT(previousDepthIndex)], texel, 0).x);
                    nearestBefore = min(nearestBefore, distance);
                    farthestBefore = max(farthestBefore, distance);
                }
            }
            if (expectedDistance >= nearestBefore - tolerance
                && expectedDistance <= farthestBefore + tolerance) {
                vec4 kept = sampleAt(historyIndex, previousPixel);
                samples = kept.a;
                history = kept.rgb;
            }
        }
    }
    // Anything not a number starts over
    if (any(isnan(history)) || isnan(samples)) {
        history = current;
        samples = 0.0;
    }

    // Clip the history toward this frame's colors around the pixel
    float gamma = (steady ? STEADY_CLIP_GAMMA : CLIP_GAMMA) * mix(1.0, REACTIVE_CLIP_SCALE, reactive);
    history = fromYCoCg(clipToBox(toYCoCg(history), mean, deviation * gamma));

    // Blend by the pixel's share of the frames, weighted against fireflies
    float blend = max(1.0 / (samples + 1.0), mix(MIN_BLEND, REACTIVE_BLEND, reactive));
    float currentWeight = blend / (1.0 + luma(current) * exposure);
    float historyWeight = (1.0 - blend) / (1.0 + luma(history) * exposure);
    vec3 color = (current * currentWeight + history * historyWeight) / (currentWeight + historyWeight);
    float total = min(samples + 1.0, MAX_SAMPLES);

    if ((flags & FLAG_VIEW) != 0) {
        // Gray for how much history the pixel has, red where it started over this frame
        color = samples == 0.0 ? vec3(1.0, 0.0, 0.0) : vec3(total / MAX_SAMPLES);
        color /= max(exposure, 1e-5);
    }
    fragColor = vec4(color, total);
}
