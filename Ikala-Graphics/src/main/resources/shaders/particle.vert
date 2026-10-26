#version 460
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_buffer_reference : require

// A particle's billboard, facing the camera: six vertices per particle, one instance per entry of
// the alive list the simulation just wrote, drawn indirectly with its count. Its size and color
// follow its life, see ParticleDraw; its color is lit by its light, plus its own emission.

#include "particle_draw.glsl"

// The scene stage's matrices, as the transparent stages bind its set
layout(set = 2, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

layout(location = PARTICLE_VARYING_CORNER) out vec2 outCorner;
layout(location = PARTICLE_VARYING_COLOR) out vec4 outColor;
layout(location = PARTICLE_VARYING_VIEW) out vec4 outViewPosition;
layout(location = PARTICLE_VARYING_SIZE) out vec2 outSizeEmissive;

const vec2 CORNERS[6] = vec2[](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0),
    vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));

void main() {
    uint slot = drawn.indices[gl_InstanceIndex];
    Particle particle = pool.particles[slot];
    float life = lifeFraction(particle.age, particle.lifetime);
    float size = mix(particle.sizeStart, particle.sizeEnd, life);
    vec2 corner = CORNERS[gl_VertexIndex % BILLBOARD_VERTICES];
    vec4 view = viewMatrix * vec4(particle.position + anchor, 1.0);
    view.xy += corner * (0.5 * size);
    outCorner = corner;
    vec4 color = mix(unpackParticleColor(particle.colorStart),
        unpackParticleColor(particle.colorEnd), life);
    vec3 light = unpackParticleColor(radianceList.radiance[slot]).rgb;
    outColor = vec4(color.rgb * (light + particle.emissive), color.a);
    outViewPosition = view;
    outSizeEmissive = vec2(size, particle.emissive);
    gl_Position = projectionMatrix * view;
}
