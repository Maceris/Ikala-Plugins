#version 460
#extension GL_EXT_buffer_reference : require

// Draws a short line along the normal or tangent of every vertex of a mesh. Each vertex of the
// mesh becomes two line vertices: even ones at the vertex, odd ones out along the direction.

// Floats per vertex: position, normal, tangent, bitangent, texture coordinates
const uint VERTEX_SIZE = 14;
const uint NORMAL_OFFSET = 3;
const uint TANGENT_OFFSET = 6;

// Vertex data for the mesh, or the animation output for animated meshes
layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer Vertices {
    float vertexData[];
};

// The model matrices for this frame, in render space, written by the model matrix update stage
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Matrices {
    mat4 modelMatrices[];
};

layout(push_constant) uniform Constants {
    mat4 projectionViewMatrix;
    Vertices vertices;
    Matrices matrices;
    // Where this model's matrices start, gl_InstanceIndex is added on like in the scene shader
    uint firstMatrix;
    // Added to the vertex index, which picks the pose for animated meshes
    uint vertexBase;
    // How long each line is, in world units
    float lineLength;
    // 0 for normals, 1 for tangents
    uint mode;
};

layout(location = 0) out vec4 outColor;

void main()
{
    uint vertex = vertexBase + uint(gl_VertexIndex) / 2;
    uint base = vertex * VERTEX_SIZE;
    vec3 position = vec3(vertices.vertexData[base], vertices.vertexData[base + 1],
        vertices.vertexData[base + 2]);
    uint directionBase = base + (mode == 0 ? NORMAL_OFFSET : TANGENT_OFFSET);
    vec3 direction = vec3(vertices.vertexData[directionBase], vertices.vertexData[directionBase + 1],
        vertices.vertexData[directionBase + 2]);

    mat4 modelMatrix = matrices.modelMatrices[firstMatrix + gl_InstanceIndex];
    vec4 renderPosition = modelMatrix * vec4(position, 1.0);
    if ((gl_VertexIndex & 1) == 1) {
        // Fine for uniform scale, which is all entities support
        vec3 renderDirection = normalize(mat3(modelMatrix) * direction);
        renderPosition.xyz += renderDirection * lineLength;
    }

    outColor = mode == 0 ? vec4(0.0, 1.0, 1.0, 1.0) : vec4(1.0, 0.5, 0.0, 1.0);
    gl_Position = projectionViewMatrix * renderPosition;
}
