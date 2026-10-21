#ifndef EQUIRECT_GLSL
#define EQUIRECT_GLSL

// Where a unit direction, y up, falls on an equirectangular sky, see SkyboxLayout.EQUIRECTANGULAR:
// longitude across from -z at the middle, +x a quarter to the right; latitude down from straight
// up. Match EnvironmentMath.equirectUv.
vec2 equirectUv(vec3 d) {
    const float PI_EQUIRECT = 3.14159265359;
    float u = 0.5 + atan(d.x, -d.z) / (2.0 * PI_EQUIRECT);
    float v = acos(clamp(d.y, -1.0, 1.0)) / PI_EQUIRECT;
    return vec2(u, v);
}

#endif
