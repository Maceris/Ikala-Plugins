#version 460

layout (location=0) in vec2 inPos;

flat out int quadID;

// Used to convert from pixel coordinates to Normalized Device Coordinates of (-1, 1)
uniform vec2 scale;

void main()
{
    gl_Position = vec4(inPos.x * scale.x - 1, inPos.y * scale.y + 1, 0.0, 1.0);
    quadID = gl_VertexID / 6;
}