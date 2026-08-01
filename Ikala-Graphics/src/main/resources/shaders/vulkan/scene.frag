#version 460
#extension GL_EXT_nonuniform_qualifier : enable

struct Material
{
    vec4 baseColor;

    float anisotropic;
    float clearcoat;
    float clearcoatGloss;
    float metallic;

    float roughness;
    float sheen;
    float sheenTint;
    float specular;

    float specularTint;
    float subsurface;
    int normalMapIndex;
    int textureIndex;
};

layout(location = 0) in vec3 outNormal;
layout(location = 1) in vec3 outTangent;
layout(location = 2) in vec3 outBitangent;
layout(location = 3) in vec2 outTextCoord;
layout(location = 4) in vec4 outViewPosition;
layout(location = 5) in vec4 outWorldPosition;
layout(location = 6) flat in uint outMaterialIdx;

layout(location = 0) out vec4 buffBaseColor;
layout(location = 1) out vec4 buffNormal;
layout(location = 2) out vec4 buffTangent;
layout(location = 3) out uint buffMaterial;

layout(set = 0, set = 0, binding = 0) uniform sampler2D bindlessTextures[];

layout(std430, set = 0, binding = 1) readonly buffer Materials {
    Material materials[];
};

void main() {
    Material material = materials[outMaterialIdx];

    vec4 baseColor = material.baseColor;
    if (material.textureIndex > 0) {
        baseColor = texture(bindlessTextures[nonuniformEXT(material.textureIndex)], outTextCoord);
    }
    if (baseColor.a < 0.5) {
        discard;
    }

    vec3 normal = outNormal;
    if (material.normalMapIndex > 0) {
        mat3 TBN = mat3(outTangent, outBitangent, outNormal);
        vec3 newNormal = texture(bindlessTextures[nonuniformEXT(material.normalMapIndex)], outTextCoord).rgb;
        newNormal = newNormal * 2.0 - 1.0;
        normal = normalize(TBN * newNormal);
    }

    buffBaseColor = baseColor;
    buffNormal = vec4(normal, 1.0);
    buffTangent = vec4(normalize(outTangent), 1.0);
    buffMaterial = outMaterialIdx;
}