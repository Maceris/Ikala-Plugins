#ifndef PARTICLES_GLSL
#define PARTICLES_GLSL

// GPU particles, see ParticlePool for the buffers and ParticleMath for the math in Java, with
// tests; keep them in step. Positions are relative to the pool's anchor, a point on a grid near
// the camera. Includers enable GL_EXT_buffer_reference.

// One particle, ParticleMath.PARTICLE_SIZE bytes. Colors are linear, packed as half floats.
struct Particle {
    vec3 position;
    float age;
    vec3 velocity;
    float lifetime;
    vec3 acceleration;
    float drag;
    uvec2 colorStart;
    uvec2 colorEnd;
    float sizeStart;
    float sizeEnd;
    float emissive;
    uint seed;
};

layout(buffer_reference, std430, buffer_reference_align = 16) buffer ParticleBuffer {
    Particle particles[];
};

// A list of particle indices: an alive list or the free list
layout(buffer_reference, std430, buffer_reference_align = 4) buffer IndexList {
    uint indices[];
};

// Counts and indirect arguments, see ParticleMath.CONTROL_ALIVE and on
layout(buffer_reference, std430, buffer_reference_align = 16) buffer Control {
    uint alive[2];
    int dead;
    uint dropped;
    uvec4 dispatchArgs;
    uvec4 drawArgs;
};

// Threads in a workgroup, ParticleMath.WORKGROUP_SIZE
const uint PARTICLE_WORKGROUP = 64;

// Vertices in a billboard, ParticleMath.BILLBOARD_VERTICES
const uint BILLBOARD_VERTICES = 6;

// ParticleMath.NO_DRAG
const float NO_DRAG = 1e-6;

vec4 unpackParticleColor(uvec2 packed) {
    return vec4(unpackHalf2x16(packed.x), unpackHalf2x16(packed.y));
}

uvec2 packParticleColor(vec4 color) {
    return uvec2(packHalf2x16(color.xy), packHalf2x16(color.zw));
}

// Move under constant acceleration and linear drag in closed form, see ParticleMath.advance
void advanceParticle(inout vec3 position, inout vec3 velocity, vec3 acceleration, float drag,
        float seconds) {
    if (drag < NO_DRAG) {
        position += velocity * seconds + 0.5 * acceleration * seconds * seconds;
        velocity += acceleration * seconds;
        return;
    }
    float decay = exp(-drag * seconds);
    vec3 terminal = acceleration / drag;
    vec3 excess = velocity - terminal;
    position += terminal * seconds + excess * ((1.0 - decay) / drag);
    velocity = terminal + excess * decay;
}

// A direction within a cone, even over its cap of the sphere, see ParticleMath.coneDirection
vec3 coneDirection(vec3 axis, float cosSpread, float u, float v) {
    float cosTheta = 1.0 - u * (1.0 - cosSpread);
    float sinTheta = sqrt(max(0.0, 1.0 - cosTheta * cosTheta));
    float phi = 6.28318530718 * v;
    vec3 helper = abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(helper, axis));
    vec3 bitangent = cross(axis, tangent);
    return axis * cosTheta + tangent * (sinTheta * cos(phi)) + bitangent * (sinTheta * sin(phi));
}

// How far through its life a particle is, 0 to 1, see ParticleMath.lifeFraction
float lifeFraction(float age, float lifetime) {
    return lifetime <= 0.0 ? 1.0 : clamp(age / lifetime, 0.0, 1.0);
}

// A round billboard's opacity across it, (1 - r^2)^2, see ParticleMath.roundShape
float roundShape(vec2 corner) {
    float r2 = dot(corner, corner);
    if (r2 >= 1.0) {
        return 0.0;
    }
    float falloff = 1.0 - r2;
    return falloff * falloff;
}

// The fade near the opaque scene, see ParticleMath.softFade
float softFade(float particleDepth, float sceneDepth, float fadeDistance) {
    if (fadeDistance <= 0.0) {
        return sceneDepth > particleDepth ? 1.0 : 0.0;
    }
    return clamp((sceneDepth - particleDepth) / fadeDistance, 0.0, 1.0);
}

// A well mixed 32-bit hash (Jarzynski and Olano's PCG hash, JCGT 2020)
uint particleHash(uint value) {
    uint state = value * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// A number from 0 to 1 from a hash, advancing it
float particleRandom(inout uint seed) {
    seed = particleHash(seed);
    return float(seed >> 8) / 16777216.0;
}

#endif
