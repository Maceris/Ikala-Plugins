#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "material.glsl"
#include "alpha_test.glsl"

// Cuts holes in a shadow where the material's texture cuts them in the surface, by the same test
// scene.frag makes, so leaves cast the shadows of leaves rather than of their quads.

layout(location = 0) in vec2 inTextCoord;
layout(location = 1) flat in uint inMaterialIdx;

layout(std430, set = 0, binding = 1) readonly buffer Materials {
    Material materials[];
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

void main()
{
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
