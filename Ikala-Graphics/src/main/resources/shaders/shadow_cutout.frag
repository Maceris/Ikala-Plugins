#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "material.glsl"
#include "alpha_test.glsl"

// Cuts holes in a shadow where the material's texture cuts them in the surface, by the same test
// scene.frag makes, so leaves cast the shadows of leaves rather than of their quads.

layout(location = 0) in vec2 inTextCoord;
layout(location = 1) flat in uint inMaterialIdx;
// A coverage volume shell's coverage, or -1 for a cut out material
layout(location = 2) in float inShellAlpha;

layout(std430, set = 0, binding = 1) readonly buffer Materials {
    Material materials[];
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

void main()
{
    if (inShellAlpha >= 0.0) {
        // A shell stops as much light as its volume, by each texel's own threshold
        if (inShellAlpha <= shellThreshold(gl_FragCoord.xy, 0u, uint(gl_PrimitiveID))) {
            discard;
        }
        return;
    }
    Material material = materials[inMaterialIdx];
    if (material.textureIndex <= 0) {
        if (material.baseColor.a < ALPHA_CUT) {
            discard;
        }
        return;
    }
    float alpha = texture(bindlessTextures[nonuniformEXT(material.textureIndex)], inTextCoord).a;
    vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(material.textureIndex)], 0));
    if (!alphaTestPasses(alpha, alphaTestMip(dFdx(inTextCoord), dFdy(inTextCoord), size),
            inTextCoord, size)) {
        discard;
    }
}
