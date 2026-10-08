#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// The default filter, which copies the rendered scene. Custom filters should declare the same
// inputs, even the ones they don't use.

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 color;

// The lit scene, including the skybox
layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(set = 0, binding = 1) uniform FilterUniforms {
    mat4 projectionMatrix;
    mat4 inverseProjectionMatrix;
    mat4 viewMatrix;
    mat4 inverseViewMatrix;
    // The size of the screen in pixels
    vec2 screenSize;
    // Multiply screen texture coordinates by this before sampling. The images can be larger than
    // the screen, with the rendered part in the top left.
    vec2 uvScale;
    // 1 if the scene stage drew the g-buffer this frame. If not, don't read the g-buffer.
    int hasGBuffer;
    // Bindless slots of the g-buffer textures. Base color, normal, and tangent are RGBA floats,
    // with normals and tangents in view space. Material is the material index, read it from
    // bindlessUintTextures. Depth is [0, 1], use inverseProjectionMatrix to get view positions.
    // The g-buffer is the same size as the screen texture, so texelFetch(..., ivec2(gl_FragCoord.xy), 0)
    // reads the pixel under this fragment.
    int baseColorIndex;
    int normalIndex;
    int tangentIndex;
    int materialIndex;
    int depthIndex;
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];
layout(set = 1, binding = 0) uniform usampler2D bindlessUintTextures[];

void main()
{
	color = texture(screenTexture, outTextCoord * uvScale);
}
