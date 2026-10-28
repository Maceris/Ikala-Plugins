#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "material.glsl"
#include "color.glsl"
#include "translucent_shadow.glsl"

// Adds a see-through surface into a sun cascade's translucent map, see translucent_shadow.glsl,
// blended by adding: its absorbance per color and as one number, and the moments of its depth
// weighted by that. It lets through what it doesn't cover, and its color of what it does, as the
// camera sees a white light through it in translucent.frag, see TranslucentShadowMath.transmittance.

layout(location = 0) in vec2 inTextCoord;
layout(location = 1) flat in uint inMaterialIdx;
// A coverage volume shell's coverage and seed, which the cut out vertex shaders this shares pass
// on; never a shell's here, see shadow_cutout.frag
layout(location = 2) in float inShellAlpha;
layout(location = 3) flat in uint inShellSeed;

layout(std430, set = 0, binding = 1) readonly buffer Materials {
    Material materials[];
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// The moments of its depth, depth to the first through fourth power, times its absorbance
layout(location = 0) out vec4 outMoments;
// Its absorbance per color, then as one number, the average
layout(location = 1) out vec4 outAbsorbance;

// Opacity is kept below 1, so the absorbance stays finite, as in translucent.frag
const float MAX_OPACITY = 0.999;

void main()
{
    // Coverage volume shells are cut out shadows, never see-through ones
    if (inShellAlpha >= 0.0 && inShellSeed != ~0u) {
        discard;
    }
    Material material = materials[inMaterialIdx];
    vec3 color;
    float alpha;
    if (material.textureIndex > 0) {
        // Sampled in linear light
        vec4 texel = texture(bindlessTextures[nonuniformEXT(material.textureIndex)], inTextCoord);
        color = texel.rgb;
        alpha = texel.a;
    } else {
        // Materials give base colors sRGB encoded
        color = sRGBToLinear(material.baseColor.rgb);
        alpha = material.baseColor.a;
    }
    alpha = min(alpha, MAX_OPACITY);
    vec3 through = max(1.0 - alpha + alpha * clamp(color, 0.0, 1.0), vec3(1.0 - MAX_OPACITY));
    vec3 absorbance = -log(through);
    float total = dot(absorbance, vec3(1.0 / 3.0));
    if (total <= 0.0) {
        discard;
    }
    float z = gl_FragCoord.z;
    float z2 = z * z;
    outMoments = total * vec4(z, z2, z2 * z, z2 * z2);
    outAbsorbance = vec4(absorbance, total);
}
