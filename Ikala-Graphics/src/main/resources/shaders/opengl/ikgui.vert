#version 460

layout (location=0) in vec2 inPos;

flat out int quadID;

// Used to convert from pixel coordinates to Normalized Device Coordinates of (-1, 1)
uniform vec2 scale;

// The top left of the viewport being rendered, since positions are absolute (desktop coordinates
// when using multiple viewports)
uniform vec2 displayPosition;

void main()
{
    vec2 pos = inPos - displayPosition;
    gl_Position = vec4(pos.x * scale.x - 1, pos.y * scale.y + 1, 0.0, 1.0);
    quadID = gl_VertexID / 6;
}