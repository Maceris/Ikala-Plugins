#version 460

// A triangle over the whole of a shadow map, from the vertex index alone, for shadow_scroll.frag.

void main()
{
    vec2 corner = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
