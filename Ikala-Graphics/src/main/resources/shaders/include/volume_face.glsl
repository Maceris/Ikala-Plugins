#ifndef VOLUME_FACE_GLSL
#define VOLUME_FACE_GLSL

// A volume brick's solid voxel's face where a ray meets it, textured as the block's mesh is: its
// material's texture, one repeat per face, across the face's other two axes, upright on the sides.
// Where the texture is cut out, the ray goes through, on to the blocks behind or the sky, as it
// would through the mesh. The depth and color passes both decide this the same way, so they agree
// on where each pixel's ray stops.
//
// Include light_inputs.glsl and volume.glsl, with VOLUME_SET, first.

#include "alpha_test.glsl"

// The least a ray's slant to a face counts for, so its ray differentials stay finite edge on
const float VOLUME_MIN_SLANT = 0.02;

// A face's texture coordinates as BlockMeshes.uv maps them, for a point or a step across the face:
// seen from +x, right is -z; from +z, right is +x; v grows downward on the sides
vec2 volumeFaceUv(vec3 p, int axis, float side) {
    return axis == 0 ? vec2(side > 0.0 ? -p.z : p.z, -p.y)
        : (axis == 1 ? vec2(p.x, p.z) : vec2(side > 0.0 ? p.x : -p.x, -p.y));
}

// How far a ray's hit on a face moves when the ray turns by a step (ray differentials, Igehy
// 1999): the turn at that distance, less its part along the ray that the face's plane takes back
vec3 volumeFaceStep(vec3 direction, vec3 turn, float distance, int axis) {
    float along = direction[axis];
    along = sign(along) * max(abs(along), VOLUME_MIN_SLANT);
    return distance * (turn - (turn[axis] / along) * direction);
}

// A solid voxel face's texel where a ray meets it: false if its voxel has no textured material.
// texel's rgb is the texture's color as stored, its alpha 1 where it covers the face and 0 where
// it is cut out, as alpha_test.glsl cuts the block's mesh.
//
// local: the point on the face, in voxels from the brick's corner; axis: the axis the face faces
// along; side: 1 if it faces up the axis, -1 down; distance: how far the point is along the ray;
// screenHeight: the drawn area's height in pixels, for how many meters a pixel covers there
bool volumeFaceTexel(uint slot, ivec3 voxel, vec3 local, int axis, float side, vec3 direction,
    float distance, float screenHeight, out vec4 texel) {
    texel = vec4(0.0);
    uint materialIndex = volumeMaterial(slot, voxel);
    if (materialIndex == 0u) {
        return false;
    }
    int textureIndex = materials[materialIndex - 1u].textureIndex;
    if (textureIndex <= 0) {
        return false;
    }
    vec2 uv = fract(volumeFaceUv(fract(local), axis, side));
    // How the coordinates change from one pixel to the next, right and down the screen, so the
    // texture is read as the mesh's is, sharp along a slanted face
    float pixelTurn = 2.0 * abs(invProjectionMatrix[1][1]) / screenHeight;
    vec2 dx = volumeFaceUv(
        volumeFaceStep(direction, invViewMatrix[0].xyz * pixelTurn, distance, axis), axis, side);
    vec2 dy = volumeFaceUv(
        volumeFaceStep(direction, -invViewMatrix[1].xyz * pixelTurn, distance, axis), axis, side);
    vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(textureIndex)], 0));
    texel = textureGrad(bindlessTextures[nonuniformEXT(textureIndex)], uv, dx, dy);
    texel.a = alphaTestPasses(texel.a, alphaTestMip(dx, dy, size), uv, size) ? 1.0 : 0.0;
    return true;
}

// Whether a ray goes through a face's texel: where it is cut out, as the block's mesh cuts it
bool volumeFaceHole(vec4 texel) {
    return texel.a < ALPHA_CUT;
}

#endif
