#version 460

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
out uint outMaterialIdx;

layout(std430, binding = 1) buffer Matrices {
	mat4 modelMatrices[];
};

uniform uint materialIndex;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    vec4 initPos = vec4(position, 1.0);
    vec4 initNormal = vec4(normal, 0.0);
    vec4 initTangent = vec4(tangent, 0.0);
    vec4 initBitangent = vec4(bitangent, 0.0);

    outMaterialIdx = materialIndex;
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