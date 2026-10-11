#ifndef LIGHT_STRUCT_GLSL
#define LIGHT_STRUCT_GLSL

// Must match LightType.getShaderValue()
const uint LIGHT_TYPE_POINT = 0;
const uint LIGHT_TYPE_SPOT = 1;

// A point light or spotlight, with the position and direction in view space
struct Light {
    vec3 position;
    float range;
    vec3 color;
    float intensity;
    vec3 direction;
    float cosOuter;
    float cosInner;
    uint type;
    vec2 _padding;
};

#endif
