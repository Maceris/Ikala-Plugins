#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_buffer_reference : require

// Turns the HDR scene color, in linear light, into the image shown: exposure, then Khronos PBR
// Neutral tone mapping, then the sRGB encode. ToneMapMath has the same math in Java, with tests.
// With automatic exposure, the exposure auto_exposure.comp adapted to multiplies it too, the
// scene's exposure compensating it.

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 fragColor;

// The automatic exposure's state, see auto_exposure.comp
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer ExposureState {
    float adapted;
    float autoExposure;
    uint valid;
    float target;
};

layout(push_constant) uniform Constants {
    // The bindless slot of the scene color
    int sceneColorIndex;
    // What the scene color is multiplied by first
    float exposure;
    ExposureState exposureState;
    // Nonzero to multiply by the automatic exposure too
    int useAutoExposure;
};

layout(set = 0, binding = 0) uniform sampler2D bindlessTextures[];

// Khronos PBR Neutral: colors keep their hue and stay as they are up to the start of compression,
// and brighter ones roll off smoothly toward white.
// https://github.com/KhronosGroup/ToneMapping/tree/main/PBR_Neutral
const float START_COMPRESSION = 0.8 - 0.04;
const float DESATURATION = 0.15;

vec3 pbrNeutral(vec3 color) {
    float x = min(color.r, min(color.g, color.b));
    float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
    color -= offset;

    float peak = max(color.r, max(color.g, color.b));
    if (peak < START_COMPRESSION) {
        return color;
    }
    const float d = 1.0 - START_COMPRESSION;
    float newPeak = 1.0 - d * d / (peak + d - START_COMPRESSION);
    color *= newPeak / peak;

    float g = 1.0 - 1.0 / (DESATURATION * (peak - newPeak) + 1.0);
    return mix(color, vec3(newPeak), g);
}

// The exact sRGB curve, the inverse of the decode the lighting does
vec3 linearToSRGB(vec3 color) {
    vec3 c = clamp(color, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, greaterThan(c, vec3(0.0031308)));
}

void main()
{
    // Drawn 1:1 with the scene color, which can be larger than the screen
    vec3 linear = texelFetch(bindlessTextures[nonuniformEXT(sceneColorIndex)], ivec2(gl_FragCoord.xy), 0).rgb;
    float scale = exposure;
    if (useAutoExposure != 0 && exposureState.valid != 0) {
        scale *= exposureState.autoExposure;
    }
    fragColor = vec4(linearToSRGB(pbrNeutral(max(linear * scale, vec3(0)))), 1.0);
}
