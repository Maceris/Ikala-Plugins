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
    // Its emission and how far its billboard stretches along its motion, in seconds, as halves
    uint emissiveStretch;
    // How it behaves, see ParticleMath.packBehavior: its anisotropy, a signed byte in bits 0 to 7;
    // whether lights light it, bit 8; what it does when it hits the world, bits 9 and 10; whether
    // it is smoke in the fog volume, bit 11; its emitter's link, bits 12 to 15; and the bounce and
    // friction, unsigned bytes in bits 16 to 23 and 24 to 31
    uint behavior;
};

float particleEmissive(Particle particle) {
    return unpackHalf2x16(particle.emissiveStretch).x;
}

float particleStretch(Particle particle) {
    return unpackHalf2x16(particle.emissiveStretch).y;
}

// One emitter's spawns this frame, ParticleMath.EMITTER_SIZE bytes, its position relative to the
// anchor and its first spawn's index among the frame's
struct Emitter {
    vec3 position;
    uint firstSpawn;
    vec3 direction;
    float cosSpread;
    vec3 acceleration;
    float drag;
    // Lifetime's range, then speed's
    vec4 lifetimeSpeed;
    vec4 colorStart;
    vec4 colorEnd;
    // Size at birth and death, emission, and the spawn ball's radius
    vec4 sizes;
    uint spawnCount;
    uint seed;
    // How its particles behave, see Particle.behavior
    uint behavior;
    // How far their billboards stretch along their motion, in seconds
    float stretch;
};

// What particles of a linked emitter look up while they live, ParticleMath.LINK_SIZE bytes: its
// spawn box and the splash its particles' hits spawn
struct Link {
    // The box's middle relative to the anchor, and 1 if it follows the camera, so its particles
    // wrap around in the box and hide under cover
    vec4 center;
    // Half the box's size, 0 for the spawn ball
    vec3 halfSize;
    // How many splash particles a hit spawns
    uint splashCount;
    Emitter splash;
};

// The frame's links, see ParticleMath.LINKS_HEADER and on: the sky occlusion map, which holds the
// highest thing over each place, as seen from the anchor's space; the water map, see WaterVolume,
// with its corner relative to the anchor, x and z, and the anchor's height; then the links by link
// less 1, the emitters' and then the water's
layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer LinkList {
    mat4 skyMatrix;
    int skyMap;
    float skyDepthRange;
    uint splashSeed;
    uint linkPadding;
    uvec2 waterMap;
    uvec2 waterPadding;
    vec4 waterCorner;
    Link links[];
};

// The water's own links, which only hits carry, see ParticleMath.WATER_LINK_SPRAY and on
const uint WATER_LINK_SPRAY = 16;
const uint WATER_LINK_MIST = 17;
const uint WATER_LINK_SPLASH = 18;
const uint WATER_LINK_DROP = 19;

// The most hits the water records in a frame, ParticleMath.MAX_WATER_HITS
const uint MAX_WATER_HITS = 2048;

// How fast a particle of a linked emitter must go down into the water to splash there,
// ParticleMath.WATER_DROP_SPEED
const float WATER_DROP_SPEED = 2.0;

// A hit the simulation recorded for splashes: where, relative to the anchor, and the link
struct Hit {
    vec3 position;
    uint link;
};

layout(buffer_reference, std430, buffer_reference_align = 16) buffer HitList {
    Hit hits[];
};

// The most hits recorded in a frame, ParticleMath.MAX_HITS
const uint MAX_HITS = 4096;

// How far below the highest thing over it a particle around the camera must be to count as under
// cover, in meters, ParticleMath.COVER_BIAS
const float COVER_BIAS = 0.05;

layout(buffer_reference, std430, buffer_reference_align = 16) buffer ParticleBuffer {
    Particle particles[];
};

// Each particle's light, by its index in the pool: half floats, red, green, blue and unused. What
// lights a lit particle per unit of its color, or 1 for an unlit one, see particle_light.comp.
layout(buffer_reference, std430, buffer_reference_align = 8) buffer RadianceList {
    uvec2 radiance[];
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
    // Alive volumetric particles, which the CPU reads back; then the froxel splat's dispatch, a
    // workgroup per drawn particle
    uvec4 counts;
    // Hits the simulation recorded this frame for splashes, and the water last frame, then unused
    uvec4 hits;
};

// Threads in a workgroup, ParticleMath.WORKGROUP_SIZE
const uint PARTICLE_WORKGROUP = 64;

// Vertices in a billboard, ParticleMath.BILLBOARD_VERTICES
const uint BILLBOARD_VERTICES = 6;

// ParticleMath.NO_DRAG
const float NO_DRAG = 1e-6;

// Values each fog froxel gathers from volumetric particles, and their fixed point scale, see
// ParticleMath.FROXEL_CHANNELS and FROXEL_SCALE
const uint PARTICLE_FROXEL_CHANNELS = 8;
const float PARTICLE_FROXEL_SCALE = 65536.0;

// How far a volumetric particle's profile reaches through its middle per meter of radius,
// ParticleMath.SPHERE_PROFILE_LENGTH
const float SPHERE_PROFILE_LENGTH = 16.0 / 15.0;

// A volumetric particle's density in its middle, see ParticleMath.sphereDensity
float sphereDensity(float opacity, float radius) {
    if (radius <= 0.0) {
        return 0.0;
    }
    return -log(1.0 - min(opacity, 0.999)) / (SPHERE_PROFILE_LENGTH * radius);
}

// How much of its middle density is at a distance, see ParticleMath.sphereProfile
float sphereProfile(float distance, float radius) {
    if (radius <= 0.0 || distance >= radius) {
        return 0.0;
    }
    float fraction = distance / radius;
    float falloff = 1.0 - fraction * fraction;
    return falloff * falloff;
}

// The optical depth from a point in a volumetric particle out to its edge, see
// ParticleMath.sphereDepthToEdge
float sphereDepthToEdge(float middleDensity, float distance, float radius) {
    if (radius <= 0.0 || distance >= radius) {
        return 0.0;
    }
    float s = max(distance, 0.0) / radius;
    float s2 = s * s;
    return middleDensity * radius * (8.0 / 15.0 - s * (1.0 - 2.0 * s2 / 3.0 + s2 * s2 / 5.0));
}

// The optical depth from a point in a volumetric particle out to its edge along a direction of unit
// length, see ParticleMath.sphereDepthAlong
float sphereDepthAlong(float middleDensity, vec3 offset, vec3 direction, float radius) {
    float distanceSquared = dot(offset, offset);
    float r2 = radius * radius;
    if (radius <= 0.0 || distanceSquared >= r2) {
        return 0.0;
    }
    float m = dot(offset, direction);
    float h = sqrt(max(0.0, m * m - distanceSquared + r2));
    float h2 = h * h;
    float m2 = m * m;
    float toEdge = h * h2 * h2 * (8.0 / 15.0);
    float toPoint = m * (h2 * h2 - 2.0 * h2 * m2 / 3.0 + m2 * m2 / 5.0);
    return middleDensity * (toEdge - toPoint) / (r2 * r2);
}

// What a particle does when it hits the world, ParticleEffect.Collision's ordinals
const uint COLLISION_NONE = 0;
const uint COLLISION_BOUNCE = 1;
const uint COLLISION_STICK = 2;
const uint COLLISION_DIE = 3;

float behaviorAnisotropy(uint behavior) {
    return float(int(behavior << 24) >> 24) / 127.0;
}

bool behaviorLit(uint behavior) {
    return (behavior & (1u << 8)) != 0u;
}

bool behaviorVolumetric(uint behavior) {
    return (behavior & (1u << 11)) != 0u;
}

uint behaviorLink(uint behavior) {
    return (behavior >> 12) & 15u;
}

uint behaviorCollision(uint behavior) {
    return (behavior >> 9) & 3u;
}

float behaviorBounce(uint behavior) {
    return float((behavior >> 16) & 255u) / 255.0;
}

float behaviorFriction(uint behavior) {
    return float(behavior >> 24) / 255.0;
}

// Turn a particle back from a solid voxel it stepped into, see ParticleMath.collide: back where it
// was, its speed into the last face it crossed reversed and scaled by the bounce, its speed along
// the face scaled down by the friction. Positions are relative to the voxel grid's corner.
void collideParticle(vec3 previous, vec3 next, inout vec3 velocity, float voxel, float bounce,
        float friction) {
    int axis = -1;
    float latest = -1.0;
    for (int i = 0; i < 3; ++i) {
        float fromCell = floor(previous[i] / voxel);
        float toCell = floor(next[i] / voxel);
        if (fromCell == toCell) {
            continue;
        }
        float boundary = (next[i] > previous[i] ? toCell : toCell + 1.0) * voxel;
        float crossing = (boundary - previous[i]) / (next[i] - previous[i]);
        if (crossing > latest) {
            latest = crossing;
            axis = i;
        }
    }
    if (axis < 0) {
        velocity = vec3(0.0);
        return;
    }
    vec3 normal = vec3(0.0);
    normal[axis] = next[axis] > previous[axis] ? -1.0 : 1.0;
    float into = dot(velocity, normal);
    vec3 along = (velocity - into * normal) * (1.0 - friction);
    velocity = along + normal * (into < 0.0 ? -bounce * into : into);
}

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

// A coordinate wrapped into a box around a middle, each axis with a half size above 0, see
// ParticleMath.wrap
vec3 wrapInBox(vec3 value, vec3 middle, vec3 halfSize) {
    vec3 size = 2.0 * halfSize;
    vec3 wrapped = middle - halfSize + mod(value - middle + halfSize, max(size, vec3(1e-6)));
    return mix(value, wrapped, greaterThan(halfSize, vec3(0.0)));
}

// A point even in a ball around the origin: a direction, and a radius by the cube root for even
// volume
vec3 ballOffset(float radius, inout uint seed) {
    return coneDirection(vec3(0.0, 1.0, 0.0), -1.0, particleRandom(seed), particleRandom(seed))
        * (radius * pow(particleRandom(seed), 1.0 / 3.0));
}

// A new particle from an emitter at a place, with its own speed, direction, lifetime and look
Particle newParticle(Emitter emitter, vec3 position, inout uint seed) {
    vec3 direction = coneDirection(emitter.direction, emitter.cosSpread, particleRandom(seed),
        particleRandom(seed));
    float speed = mix(emitter.lifetimeSpeed.z, emitter.lifetimeSpeed.w, particleRandom(seed));
    Particle particle;
    particle.position = position;
    particle.age = 0.0;
    particle.velocity = direction * speed;
    particle.lifetime = mix(emitter.lifetimeSpeed.x, emitter.lifetimeSpeed.y, particleRandom(seed));
    particle.acceleration = emitter.acceleration;
    particle.drag = emitter.drag;
    particle.colorStart = packParticleColor(emitter.colorStart);
    particle.colorEnd = packParticleColor(emitter.colorEnd);
    particle.sizeStart = emitter.sizes.x;
    particle.sizeEnd = emitter.sizes.y;
    particle.emissiveStretch = packHalf2x16(vec2(emitter.sizes.z, emitter.stretch));
    particle.behavior = emitter.behavior;
    return particle;
}

// How long a billboard stretched along its motion is, see ParticleMath.streakLength
float streakLength(float size, float speed, float stretch) {
    return size + max(speed, 0.0) * max(stretch, 0.0);
}

#endif
