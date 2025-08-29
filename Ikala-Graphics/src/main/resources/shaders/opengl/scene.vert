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


layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;

out vec3 outNormal;
out vec3 outTangent;
out vec3 outBitangent;
out vec2 outTextCoord;
out vec4 outViewPosition;
out vec4 outWorldPosition;
flat out uint outMaterialIdx;

layout(std430, binding = 0) buffer Matrices {
	mat4 modelMatrices[];
};

layout(std430, binding = 1) readonly buffer Materials {
    Material materials[];
};

layout(std430, binding = 2) readonly buffer MaterialOverrides {
    uint materialOverrides[];
};

uniform uint materialIndex;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform uint meshIndex;

void main()
{
    vec4 initPos = vec4(position, 1.0);
    vec4 initNormal = vec4(normal, 0.0);
    vec4 initTangent = vec4(tangent, 0.0);
    vec4 initBitangent = vec4(bitangent, 0.0);

    uint overrideIndex = gl_BaseInstance + gl_InstanceID + meshIndex;
    uint override = materialOverrides[overrideIndex];
    outMaterialIdx = override != 0 ? override : materialIndex;

    mat4 modelMatrix =  modelMatrices[gl_BaseInstance + gl_InstanceID];
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * initPos;
    outViewPosition  = viewMatrix * outWorldPosition;
    gl_Position   = projectionMatrix * outViewPosition;
    outNormal     = normalize(modelViewMatrix * initNormal).xyz;
    outTangent    = normalize(modelViewMatrix * initTangent).xyz;
    outBitangent  = normalize(modelViewMatrix * initBitangent).xyz;
    outTextCoord  = texCoord;
}