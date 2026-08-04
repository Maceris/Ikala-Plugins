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

layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec3 outTangent;
layout(location = 2) out vec3 outBitangent;
layout(location = 3) out vec2 outTextCoord;
layout(location = 4) out vec4 outViewPosition;
layout(location = 5) out vec4 outWorldPosition;
layout(location = 6) flat out uint outMaterialIdx;

//TODO(ches) now that these need to be in blocks, figure out the buffer layout again
layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    //TODO(ches) Do we actually want material index to be a uniform?
    uint materialIndex;
    uint meshIndex;
};

layout(std430, set = 0, binding = 1) buffer Matrices {
	mat4 modelMatrices[];
};

layout(std430, set = 0, binding = 2) readonly buffer Materials {
    Material materials[];
};

layout(std430, set = 0, binding = 3) readonly buffer MaterialOverrides {
    uint materialOverrides[];
};

void main()
{
    vec4 initPos = vec4(position, 1.0);
    vec4 initNormal = vec4(normal, 0.0);
    vec4 initTangent = vec4(tangent, 0.0);
    vec4 initBitangent = vec4(bitangent, 0.0);

    uint overrideIndex = gl_BaseInstance + gl_InstanceIndex + meshIndex;
    uint override = materialOverrides[overrideIndex];
    outMaterialIdx = override != 0 ? override : materialIndex;

    mat4 modelMatrix =  modelMatrices[gl_BaseInstance + gl_InstanceIndex];
    mat4 modelViewMatrix = viewMatrix * modelMatrix;
    outWorldPosition = modelMatrix * initPos;
    outViewPosition  = viewMatrix * outWorldPosition;
    gl_Position   = projectionMatrix * outViewPosition;
    outNormal     = normalize(modelViewMatrix * initNormal).xyz;
    outTangent    = normalize(modelViewMatrix * initTangent).xyz;
    outBitangent  = normalize(modelViewMatrix * initBitangent).xyz;
    outTextCoord  = texCoord;
}