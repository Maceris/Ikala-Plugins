#ifndef COVERAGE_BRICKS_GLSL
#define COVERAGE_BRICKS_GLSL

// Coverage volumes' bricks, see CoverageBricks: each brick is words of four uints, a header, a
// 512-bit mask of which of its voxels hold anything, then only those voxels, in order. A brick's
// boxes carry where it starts in the brick table, in words.

#extension GL_EXT_buffer_reference : require

layout(buffer_reference, std430, buffer_reference_align = 16) readonly buffer Bricks {
    uvec4 words[];
};

// Match CoverageBricks
const int BRICK_SIDE = 8;
// Words of a brick before its voxels: the header, then the mask
const uint BRICK_HEAD_WORDS = 5u;

// A brick's voxel, by its place in the brick, x fastest, or all zero where it holds nothing
uvec4 brickVoxel(Bricks bricks, uint brick, int place)
{
    uint bit = uint(place) & 31u;
    uint word = uint(place) >> 5u;
    uint quarter = word >> 2u;
    uvec4 mask = bricks.words[brick + 1u + quarter];
    uint bits = mask[word & 3u];
    if (((bits >> bit) & 1u) == 0u) {
        return uvec4(0u);
    }
    // How many voxels the brick holds before this one: the quarters before, from the header, then
    // this quarter's uints before, then this uint's bits below
    uint rank = 0u;
    if (quarter > 0u) {
        uvec4 header = bricks.words[brick];
        rank = quarter == 1u ? (header.y & 0xFFFFu) : (quarter == 2u ? header.y >> 16u : header.z);
    }
    for (uint i = 0u; i < (word & 3u); ++i) {
        rank += uint(bitCount(mask[i]));
    }
    rank += uint(bitCount(bits & ((1u << bit) - 1u)));
    return bricks.words[brick + BRICK_HEAD_WORDS + rank];
}

#endif
