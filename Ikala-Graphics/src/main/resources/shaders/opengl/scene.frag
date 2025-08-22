#version 460

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

in vec3 outNormal;
in vec3 outTangent;
in vec3 outBitangent;
in vec2 outTextCoord;
in vec4 outViewPosition;
in vec4 outWorldPosition;
flat in uint outMaterialIdx;

layout(std430, binding = 0) readonly buffer Materials {
    Material materials[];
}

layout(bindless_sampler) uniform sampler2D diffuseSampler;
layout(bindless_sampler) uniform sampler2D normalSampler;

layout(location = 0) out vec4 buffBaseColor;
layout(location = 1) out vec4 buffNormal;
layout(location = 2) out vec4 buffTangent;
layout(location = 3) out uint buffMaterial;

vec3 calcNormal(int idx, vec3 normal, vec3 tangent, vec3 bitangent, vec2 textCoords) {
    mat3 TBN = mat3(tangent, bitangent, normal);
    vec3 newNormal = texture(textureSampler[idx], textCoords).rgb;
    newNormal = newNormal * 2.0 - 1.0;
    newNormal = normalize(TBN * newNormal);
    return newNormal;
}

void main() {
    Material material = materials[outMaterialIdx];

    vec4 diffuse = material.diffuse;
    if (material.textureIndex > 0) {
        diffuse = texture(textureSampler[material.textureIndex], outTextCoord);
    }
    if (diffuse.a < 0.5) {
        discard;
    }

    vec3 normal = outNormal;
    if (material.normalMapIndex > 0) {
        normal = calcNormal(material.normalMapIndex, outNormal, outTangent, outBitangent, outTextCoord);
    }

    buffBaseColor = diffuse;
    buffNormal = vec4(normal, 1.0);
    buffTangent = vec4(normalize(outTangent), 1.0);
    buffMaterial = outMaterialIdx;
}