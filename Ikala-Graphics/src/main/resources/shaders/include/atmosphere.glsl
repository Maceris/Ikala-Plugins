#ifndef ATMOSPHERE_GLSL
#define ATMOSPHERE_GLSL

// A physical sky: Earth's atmosphere of air (Rayleigh), haze (Mie) and ozone over a planet, after
// Hillaire ("A Scalable and Production Ready Sky and Atmosphere Rendering Technique", 2020), with
// Bruneton and Neyret's constants and transmittance mapping ("Precomputed Atmospheric Scattering",
// 2008). Distances in kilometers; light per unit of the sun's illuminance, which the user of the
// tables scales by the sun's color and intensity. AtmosphereMath has the mappings in Java, with
// tests. Include with ENVIRONMENT_SET defined, for the tables, see Environment.

const float ATMOSPHERE_PI = 3.14159265359;

// The ground's and the atmosphere's top radii
const float BOTTOM_RADIUS = 6360.0;
const float TOP_RADIUS = 6460.0;

// Air: scattering per kilometer at sea level, by color, and the height over which it falls by e
const vec3 RAYLEIGH_SCATTERING = vec3(5.802e-3, 13.558e-3, 33.1e-3);
const float RAYLEIGH_HEIGHT = 8.0;
// Haze: scattering and extinction per kilometer at sea level, its height, and how far forward it
// scatters
const float MIE_SCATTERING = 3.996e-3;
const float MIE_EXTINCTION = 4.440e-3;
const float MIE_HEIGHT = 1.2;
const float MIE_G = 0.8;
// Ozone: absorption per kilometer at its peak, in a layer 30 km thick around 25 km up
const vec3 OZONE_ABSORPTION = vec3(0.650e-3, 1.881e-3, 0.085e-3);
const float OZONE_CENTER = 25.0;
const float OZONE_HALF_WIDTH = 15.0;

// The tables' sizes. Match Environment.
const ivec2 TRANSMITTANCE_SIZE = ivec2(256, 64);
const ivec2 MULTIPLE_SCATTERING_SIZE = ivec2(32, 32);
const ivec2 SKY_VIEW_SIZE = ivec2(192, 108);

// The sun's angular radius, for its disk
const float SUN_ANGULAR_RADIUS = 0.004675;

layout(set = ENVIRONMENT_SET, binding = 8) uniform sampler2D transmittanceTable;
layout(set = ENVIRONMENT_SET, binding = 9) uniform sampler2D multipleScatteringTable;
layout(set = ENVIRONMENT_SET, binding = 10) uniform sampler2D skyViewTable;

// What the atmosphere does at a height above the ground
struct Medium {
    vec3 scattering;
    vec3 extinction;
    vec3 rayleigh;
    float mie;
};

Medium mediumAt(float height) {
    float rayleighDensity = exp(-height / RAYLEIGH_HEIGHT);
    float mieDensity = exp(-height / MIE_HEIGHT);
    float ozoneDensity = max(0.0, 1.0 - abs(height - OZONE_CENTER) / OZONE_HALF_WIDTH);
    Medium m;
    m.rayleigh = RAYLEIGH_SCATTERING * rayleighDensity;
    m.mie = MIE_SCATTERING * mieDensity;
    m.scattering = m.rayleigh + m.mie;
    m.extinction = m.rayleigh + MIE_EXTINCTION * mieDensity + OZONE_ABSORPTION * ozoneDensity;
    return m;
}

// How far along a ray from radius r at the cosine mu of its angle from straight up it meets a
// sphere of a radius, the far side if it starts inside, or -1 for never
float distanceToSphere(float r, float mu, float radius) {
    float discriminant = r * r * (mu * mu - 1.0) + radius * radius;
    if (discriminant < 0.0) {
        return -1.0;
    }
    return -r * mu + sqrt(discriminant);
}

// How far to the ground, or -1 if the ray misses it
float distanceToGround(float r, float mu) {
    float discriminant = r * r * (mu * mu - 1.0) + BOTTOM_RADIUS * BOTTOM_RADIUS;
    if (mu >= 0.0 || discriminant < 0.0) {
        return -1.0;
    }
    return -r * mu - sqrt(discriminant);
}

// Bruneton's mapping of a height and a direction to the transmittance table
vec2 transmittanceUv(float r, float mu) {
    float h = sqrt(TOP_RADIUS * TOP_RADIUS - BOTTOM_RADIUS * BOTTOM_RADIUS);
    float rho = sqrt(max(r * r - BOTTOM_RADIUS * BOTTOM_RADIUS, 0.0));
    float d = max(distanceToSphere(r, mu, TOP_RADIUS), 0.0);
    float dMin = TOP_RADIUS - r;
    float dMax = rho + h;
    return vec2((d - dMin) / (dMax - dMin), rho / h);
}

// The height and direction of a texel of the transmittance table
void transmittanceParameters(vec2 uv, out float r, out float mu) {
    float h = sqrt(TOP_RADIUS * TOP_RADIUS - BOTTOM_RADIUS * BOTTOM_RADIUS);
    float rho = h * uv.y;
    r = sqrt(rho * rho + BOTTOM_RADIUS * BOTTOM_RADIUS);
    float dMin = TOP_RADIUS - r;
    float dMax = rho + h;
    float d = dMin + uv.x * (dMax - dMin);
    mu = d == 0.0 ? 1.0 : (h * h - rho * rho - d * d) / (2.0 * r * d);
    mu = clamp(mu, -1.0, 1.0);
}

// How much of the sun's light gets from the top of the atmosphere to a height, toward the sun
vec3 sunTransmittance(float r, float muSun) {
    return textureLod(transmittanceTable, transmittanceUv(r, muSun), 0.0).rgb;
}

// The light scattered more than once arriving at a height, per unit of scattering, from Hillaire's
// table, by the sun's angle
vec3 multipleScattering(float r, float muSun) {
    vec2 uv = vec2(muSun * 0.5 + 0.5, (r - BOTTOM_RADIUS) / (TOP_RADIUS - BOTTOM_RADIUS));
    // Keep to texel centers
    uv = (uv * vec2(MULTIPLE_SCATTERING_SIZE - 1) + 0.5) / vec2(MULTIPLE_SCATTERING_SIZE);
    return textureLod(multipleScatteringTable, uv, 0.0).rgb;
}

float rayleighPhase(float cosTheta) {
    return 3.0 / (16.0 * ATMOSPHERE_PI) * (1.0 + cosTheta * cosTheta);
}

// Cornette and Shanks's phase function for haze
float miePhase(float cosTheta) {
    float g2 = MIE_G * MIE_G;
    float k = 3.0 / (8.0 * ATMOSPHERE_PI) * (1.0 - g2) / (2.0 + g2);
    return k * (1.0 + cosTheta * cosTheta) / pow(1.0 + g2 - 2.0 * MIE_G * cosTheta, 1.5);
}

// The angle of the horizon from straight up at a height, as the sky view table's mapping needs it
float zenithHorizonAngle(float r) {
    float horizon = sqrt(max(r * r - BOTTOM_RADIUS * BOTTOM_RADIUS, 0.0));
    return ATMOSPHERE_PI - acos(horizon / r);
}

// Hillaire's mapping of the sky view table: across, the cosine of the angle around from the sun,
// squeezed toward it; down, the angle from straight up, squeezed toward the horizon from both sides
vec2 skyViewUv(float r, float viewZenithCos, float lightViewCos) {
    float horizonAngle = zenithHorizonAngle(r);
    float beta = ATMOSPHERE_PI - horizonAngle;
    float viewZenith = acos(clamp(viewZenithCos, -1.0, 1.0));
    float v;
    if (viewZenith < horizonAngle) {
        float coord = 1.0 - sqrt(max(1.0 - viewZenith / horizonAngle, 0.0));
        v = 0.5 * coord;
    } else {
        float coord = sqrt(max((viewZenith - horizonAngle) / beta, 0.0));
        v = 0.5 + 0.5 * coord;
    }
    float u = sqrt(clamp(-lightViewCos * 0.5 + 0.5, 0.0, 1.0));
    vec2 uv = vec2(u, v);
    return (uv * vec2(SKY_VIEW_SIZE - 1) + 0.5) / vec2(SKY_VIEW_SIZE);
}

// The view direction of a texel of the sky view table, its inverse
void skyViewParameters(vec2 uv, float r, out float viewZenithCos, out float lightViewCos) {
    float horizonAngle = zenithHorizonAngle(r);
    float beta = ATMOSPHERE_PI - horizonAngle;
    float viewZenith;
    if (uv.y < 0.5) {
        float coord = 1.0 - 2.0 * uv.y;
        coord = 1.0 - coord * coord;
        viewZenith = horizonAngle * coord;
    } else {
        float coord = 2.0 * uv.y - 1.0;
        viewZenith = horizonAngle + beta * coord * coord;
    }
    viewZenithCos = cos(viewZenith);
    lightViewCos = -(uv.x * uv.x * 2.0 - 1.0);
}

// The sky's light toward a unit world direction, y up, seen from a radius, with the sun toward a
// unit world direction, per unit of the sun's illuminance, from the sky view table
vec3 skyRadiance(vec3 d, vec3 sun, float r) {
    vec2 horizontal = d.xz;
    vec2 sunHorizontal = sun.xz;
    float lengths = length(horizontal) * length(sunHorizontal);
    float lightViewCos = lengths > 1e-6 ? dot(horizontal, sunHorizontal) / lengths : 1.0;
    return textureLod(skyViewTable, skyViewUv(r, d.y, lightViewCos), 0.0).rgb;
}

#endif
