#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "color.glsl"
// The physical sky's tables are in the environment's set, set 2, see SkyboxRender
#define ENVIRONMENT_SET 2
#include "atmosphere.glsl"
#include "environment.glsl"
#include "equirect.glsl"
// The fog volume, set 3, see SkyboxRender
#define FOG_SET 3
#include "fog.glsl"

layout(location = 0) in vec2 outTextCoord;
layout(location = 1) in vec3 outDirection;

layout(location = 0) out vec4 fragColor;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    vec4 diffuse;
    int hasTexture;
    int textureIndex;
    // Nonzero to draw the physical sky, from its sky view table
    int physicalSky;
    // How the texture lays the sky out, see SkyboxLayout
    int skyLayout;
    // Toward the sun, y up, and in w the camera's distance from the planet's center in kilometers
    vec4 sunAndRadius;
    // What the physical sky is scaled by, the sun's color times its intensity times the sky's
    // strength, with w 1; or with w 2, the ambient light an environment map's average is scaled to
    vec4 skyScale;
    // Nonzero to see the sky through all the fog in the volume
    int fogEnabled;
    // The physical sky's own glow, not scaled by the sun, and in w the illuminance of a magnitude
    // 0 star, see PhysicalSky
    vec4 glow;
    // The rotation from world directions to the stars', a quaternion
    vec4 starRotation;
    // The glow the ground below the horizon sends back, its albedo times the glow
    vec4 groundGlow;
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// The sun's disk: its light through the atmosphere spread over its solid angle, with an edge a
// fraction of its radius wide so it doesn't alias
const float SUN_EDGE = 0.1;

// Match SkyboxLayout
const int LAYOUT_EQUIRECTANGULAR = 1;

// The stars: about as many as the eye sees on a clear night, down to magnitude 6.5, the count
// growing by 10^0.46 a magnitude fainter, placed at random one at most in each cell of a grid on
// the cube around the sky, and drawn as small spots about a pixel wide holding their light
const float STAR_COUNT = 9000.0;
const float STAR_FAINTEST = 6.5;
const float STAR_BRIGHTEST = -1.5;
const float STAR_COUNT_GROWTH = 0.46;
const float STAR_CELLS = 160.0;
// Kept away from the cells' edges, so no spot is cut off
const float STAR_MARGIN = 0.3;
// The spots' spread in radians
const float STAR_SPREAD = 0.001;

// Rotate a vector by a unit quaternion
vec3 rotate(vec4 q, vec3 v) {
    return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
}

// Jarzynski and Olano's PCG hash of three numbers
uvec3 pcg3d(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

// A direction from a cube face's coordinates, each from -1 to 1
vec3 fromFace(int face, vec2 uv) {
    float s = (face & 1) == 0 ? 1.0 : -1.0;
    if (face < 2) {
        return normalize(vec3(s, uv));
    }
    if (face < 4) {
        return normalize(vec3(uv.x, s, uv.y));
    }
    return normalize(vec3(uv, s));
}

// The starlight along a direction on the star sphere, per unit of a magnitude 0 star's
// illuminance
float starLight(vec3 d) {
    vec3 a = abs(d);
    int face;
    vec2 uv;
    if (a.x >= a.y && a.x >= a.z) {
        face = d.x > 0.0 ? 0 : 1;
        uv = d.yz / a.x;
    } else if (a.y >= a.z) {
        face = d.y > 0.0 ? 2 : 3;
        uv = d.xz / a.y;
    } else {
        face = d.z > 0.0 ? 4 : 5;
        uv = d.xy / a.z;
    }
    vec2 cell = min(floor((uv * 0.5 + 0.5) * STAR_CELLS), STAR_CELLS - 1.0);
    vec3 random = vec3(pcg3d(uvec3(uvec2(cell), uint(face)))) / 4294967295.0;
    float chance = STAR_COUNT / (6.0 * STAR_CELLS * STAR_CELLS);
    if (random.x >= chance) {
        return 0.0;
    }
    // A star in a cell is brighter than a magnitude for the fraction of stars that are
    float magnitude = max(STAR_FAINTEST
        + log(max(random.x / chance, 1e-6)) / (STAR_COUNT_GROWTH * log(10.0)), STAR_BRIGHTEST);
    vec2 place = (cell + STAR_MARGIN + (1.0 - 2.0 * STAR_MARGIN) * random.yz) / STAR_CELLS;
    vec3 star = fromFace(face, place * 2.0 - 1.0);
    // The angle to the star, small enough for the chord
    float angle = length(d - star);
    float spot = exp(-angle * angle / (2.0 * STAR_SPREAD * STAR_SPREAD))
        / (2.0 * ATMOSPHERE_PI * STAR_SPREAD * STAR_SPREAD);
    return spot * pow(10.0, -0.4 * magnitude);
}

// The scene color is linear light, tone mapped later. The sky's texture samples in linear light
// already; its color is given sRGB encoded, as material colors are.
// The sky's light, seen through the fog
void finish(vec3 color)
{
    if (fogEnabled != 0) {
        FogSeen seen = fogBeyond(gl_FragCoord.xy);
        color = color * seen.through + seen.light;
    }
    fragColor = vec4(color, 1.0);
}

void main()
{
    if (physicalSky != 0) {
        vec3 d = normalize(outDirection);
        vec3 sun = sunAndRadius.xyz;
        float r = sunAndRadius.w;
        vec3 color = skyRadiance(d, sun, r);
        float angle = acos(clamp(dot(d, sun), -1.0, 1.0));
        float disk = 1.0 - smoothstep(SUN_ANGULAR_RADIUS * (1.0 - SUN_EDGE),
            SUN_ANGULAR_RADIUS * (1.0 + SUN_EDGE), angle);
        if (disk > 0.0 && distanceToGround(r, d.y) < 0.0) {
            float solidAngle = ATMOSPHERE_PI * SUN_ANGULAR_RADIUS * SUN_ANGULAR_RADIUS;
            color += disk * sunTransmittance(r, sun.y) / solidAngle;
        }
        color *= skyScale.rgb;
        // The sky's own glow and the stars, through the air, above the horizon; below it the
        // ground lit by the glow
        if (distanceToGround(r, d.y) < 0.0) {
            color += glow.rgb;
            if (glow.w > 0.0) {
                color += glow.w * starLight(rotate(starRotation, d)) * sunTransmittance(r, d.y);
            }
        } else {
            color += groundGlow.rgb;
        }
        finish(color);
        return;
    }
    if (hasTexture == 1 && skyLayout == LAYOUT_EQUIRECTANGULAR) {
        // Longitude wraps across the map's sides, where the derivatives would jump a whole turn
        // and pick the smallest mip
        vec2 uv = equirectUv(normalize(outDirection));
        vec2 dx = dFdx(uv);
        vec2 dy = dFdy(uv);
        dx.x -= round(dx.x);
        dy.x -= round(dy.x);
        vec3 color = textureGrad(bindlessTextures[nonuniformEXT(textureIndex)], uv, dx, dy).rgb;
        // As bright as it lights the scene
        float average = max(dot(environmentAverage(), ENVIRONMENT_LUMINANCE), 1e-4);
        finish(color * skyScale.rgb / average);
        return;
    }
    vec3 color = hasTexture == 1
        ? texture(bindlessTextures[nonuniformEXT(textureIndex)], outTextCoord).rgb
        : sRGBToLinear(diffuse.rgb);
    finish(color);
}
