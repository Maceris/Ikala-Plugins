#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "material.glsl"

layout(location = 0) in vec3 outNormal;
layout(location = 1) in vec3 outTangent;
layout(location = 2) in vec3 outBitangent;
layout(location = 3) in vec2 outTextCoord;
layout(location = 4) in vec4 outViewPosition;
layout(location = 5) in vec4 outWorldPosition;
layout(location = 6) flat in uint outMaterialIdx;

// Whether to drop pixels the texture's alpha cuts out. Off for baked opaque geometry, so the
// depth test can run before the shader.
layout(constant_id = 0) const bool ALPHA_TEST = true;

// Whether this is a level of detail cluster from scene_lod.vert, which has flat triangles and no
// texture coordinates. Then each triangle's normal comes from how its position changes across the
// screen, and the texture is projected along its main axis, one repeat per block, with the same
// rules as LodTexturing: the fraction of unwrapped coordinates is sampled with their gradients, so
// the mip level doesn't jump where the fraction wraps. See scene_lod.vert for what its outputs
// carry then.
layout(constant_id = 1) const bool TRIPLANAR = false;

layout(location = 0) out vec4 buffBaseColor;
layout(location = 1) out vec4 buffNormal;
layout(location = 2) out vec4 buffTangent;
layout(location = 3) out uint buffMaterial;

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

layout(std430, set = 0, binding = 2) readonly buffer Materials {
    Material materials[];
};

// This pixel's texture coordinates, with their gradients for projected ones
vec2 textCoord;
vec2 textCoordDx;
vec2 textCoordDy;

// Sample one of the bindless textures at this pixel's texture coordinates
vec4 sampleTexture(int index)
{
    if (TRIPLANAR) {
        return textureGrad(
            bindlessTextures[nonuniformEXT(index)], fract(textCoord), textCoordDx, textCoordDy);
    }
    return texture(bindlessTextures[nonuniformEXT(index)], textCoord);
}

void main() {
    Material material = materials[outMaterialIdx];

    vec3 surfaceNormal = outNormal;
    vec3 tangent = outTangent;
    vec3 bitangent = outBitangent;
    textCoord = outTextCoord;
    if (TRIPLANAR) {
        // The flat triangle's normal in render space, turned to face the camera at the origin
        vec3 position = outWorldPosition.xyz;
        vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
        if (dot(normal, position) > 0.0) {
            normal = -normal;
        }
        // Coordinates from the camera's block, so their fractions are the world's. The main axis
        // prefers up and down on ties, as LodTexturing.axis does.
        vec3 local = position + outViewPosition.xyz;
        vec3 size = abs(normal);
        vec3 worldTangent;
        vec3 worldBitangent;
        if (size.y >= size.x && size.y >= size.z) {
            textCoord = local.xz;
            worldTangent = vec3(1.0, 0.0, 0.0);
            worldBitangent = vec3(0.0, 0.0, 1.0);
        } else if (size.x >= size.z) {
            // Seen from +x, right is -z; from -x, +z
            float side = normal.x > 0.0 ? -1.0 : 1.0;
            textCoord = vec2(side * local.z, -local.y);
            worldTangent = vec3(0.0, 0.0, side);
            worldBitangent = vec3(0.0, -1.0, 0.0);
        } else {
            // Seen from +z, right is +x; from -z, -x
            float side = normal.z > 0.0 ? 1.0 : -1.0;
            textCoord = vec2(side * local.x, -local.y);
            worldTangent = vec3(side, 0.0, 0.0);
            worldBitangent = vec3(0.0, -1.0, 0.0);
        }
        // The world's axes in view space, from scene_lod.vert
        mat3 toView = mat3(outNormal, outTangent, outBitangent);
        surfaceNormal = normalize(toView * normal);
        tangent = normalize(toView * worldTangent);
        bitangent = normalize(toView * worldBitangent);
    }
    textCoordDx = dFdx(textCoord);
    textCoordDy = dFdy(textCoord);

    vec4 baseColor = material.baseColor;
    if (material.textureIndex > 0) {
        baseColor = sampleTexture(material.textureIndex);
    }
    if (ALPHA_TEST && baseColor.a < 0.5) {
        discard;
    }

    vec3 normal = surfaceNormal;
    if (material.normalMapIndex > 0) {
        mat3 TBN = mat3(tangent, bitangent, surfaceNormal);
        vec3 newNormal = sampleTexture(material.normalMapIndex).rgb;
        newNormal = newNormal * 2.0 - 1.0;
        normal = normalize(TBN * newNormal);
    }

    buffBaseColor = baseColor;
    buffNormal = vec4(normal, 1.0);
    buffTangent = vec4(normalize(tangent), 1.0);
    buffMaterial = outMaterialIdx;
}