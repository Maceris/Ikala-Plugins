package com.ikalagaming.graphics.bake;

import lombok.NonNull;

import java.util.Arrays;

/**
 * The 24 rotations that turn a cube onto itself, about its center. Baked placements can only use
 * these, since a coverage mask is then rotated with a lookup table instead of being worked out
 * again. Anything turned another way has to be an instance.
 *
 * <p>A rotation is a 3×3 matrix of -1, 0 and 1, one non-zero per row and column, with determinant
 * 1, so it never mirrors and triangles keep their winding. Rotation 0 is the identity. The rest are
 * in a fixed order, so a rotation index means the same thing everywhere.
 */
public final class CubeRotations {

    /** How many rotations there are. */
    public static final int COUNT = 24;

    /** The identity rotation. */
    public static final int IDENTITY = 0;

    /** Each rotation's matrix, row by row: {@code out[r] = sum(m[r * 3 + c] * in[c])}. */
    private static final int[][] MATRICES = new int[COUNT][];

    /** Where each face ends up under each rotation, by rotation then face ordinal. */
    private static final Face[][] FACES = new Face[COUNT][Face.ALL.length];

    /**
     * Where each cell of each face's coverage grid ends up under each rotation, on the face it
     * turns into: by rotation, face ordinal, then cell.
     */
    private static final int[][][] CELLS = new int[COUNT][Face.ALL.length][FaceCoverage.CELL_COUNT];

    /** The rotation that undoes each rotation. */
    private static final int[] INVERSES = new int[COUNT];

    static {
        int count = 0;
        final int[][] permutations = {
            {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
        };
        for (int[] permutation : permutations) {
            for (int signs = 0; signs < 8; ++signs) {
                int[] matrix = new int[9];
                for (int row = 0; row < 3; ++row) {
                    matrix[row * 3 + permutation[row]] = (signs & (1 << row)) == 0 ? 1 : -1;
                }
                if (determinant(matrix) == 1) {
                    MATRICES[count++] = matrix;
                }
            }
        }
        // The identity comes first, the permutation and signs above are tried in that order
        for (int rotation = 0; rotation < COUNT; ++rotation) {
            for (Face face : Face.ALL) {
                FACES[rotation][face.ordinal()] = turnFace(rotation, face);
            }
            for (Face face : Face.ALL) {
                CELLS[rotation][face.ordinal()] = turnCells(rotation, face);
            }
        }
        for (int rotation = 0; rotation < COUNT; ++rotation) {
            for (int other = 0; other < COUNT; ++other) {
                if (compose(rotation, other) == IDENTITY) {
                    INVERSES[rotation] = other;
                }
            }
        }
    }

    /**
     * The determinant of a 3×3 matrix.
     *
     * @param m The matrix, row by row.
     * @return The determinant.
     */
    private static int determinant(int @NonNull [] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7])
                - m[1] * (m[3] * m[8] - m[5] * m[6])
                + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    /**
     * Work out where a face ends up, from where its outward direction is turned to.
     *
     * @param rotation The rotation.
     * @param face The face.
     * @return The face it becomes.
     */
    private static Face turnFace(int rotation, @NonNull Face face) {
        final float[] direction = new float[3];
        direction[face.getAxis()] = face.getSign();
        final float[] turned = new float[3];
        apply(rotation, direction, turned);
        for (int axis = 0; axis < 3; ++axis) {
            if (turned[axis] != 0) {
                return Face.of(axis, turned[axis] > 0 ? 1 : -1);
            }
        }
        throw new IllegalStateException("A rotation turned a direction into nothing");
    }

    /**
     * Work out where each cell of a face's grid ends up, by turning the cell's center on a unit
     * cube centered at the origin.
     *
     * @param rotation The rotation.
     * @param face The face.
     * @return The cell each cell becomes, on the face this one becomes.
     */
    private static int[] turnCells(int rotation, @NonNull Face face) {
        final int size = FaceCoverage.GRID_SIZE;
        final Face to = FACES[rotation][face.ordinal()];
        final int[] cells = new int[FaceCoverage.CELL_COUNT];
        final float[] point = new float[3];
        final float[] turned = new float[3];
        for (int row = 0; row < size; ++row) {
            for (int column = 0; column < size; ++column) {
                point[face.getAxis()] = 0.5f * face.getSign();
                point[face.getUAxis()] = (column + 0.5f) / size - 0.5f;
                point[face.getVAxis()] = (row + 0.5f) / size - 0.5f;
                apply(rotation, point, turned);
                // Cell centers land on cell centers, so flooring is exact
                final int toColumn = (int) Math.floor((turned[to.getUAxis()] + 0.5f) * size);
                final int toRow = (int) Math.floor((turned[to.getVAxis()] + 0.5f) * size);
                cells[FaceCoverage.cell(column, row)] = FaceCoverage.cell(toColumn, toRow);
            }
        }
        return cells;
    }

    /**
     * Turn a vector.
     *
     * @param rotation The rotation.
     * @param in The vector, 3 floats.
     * @param out Receives the turned vector, 3 floats, which may not be {@code in}.
     */
    public static void apply(int rotation, float @NonNull [] in, float @NonNull [] out) {
        apply(rotation, in, 0, out, 0);
    }

    /**
     * Turn a vector stored inside an array.
     *
     * @param rotation The rotation.
     * @param in The array holding the vector.
     * @param inOffset Where the vector starts in it.
     * @param out The array to write the turned vector to, which may not overlap the input.
     * @param outOffset Where to write it.
     */
    public static void apply(
            int rotation,
            float @NonNull [] in,
            int inOffset,
            float @NonNull [] out,
            int outOffset) {
        final int[] m = MATRICES[rotation];
        for (int row = 0; row < 3; ++row) {
            out[outOffset + row] =
                    m[row * 3] * in[inOffset]
                            + m[row * 3 + 1] * in[inOffset + 1]
                            + m[row * 3 + 2] * in[inOffset + 2];
        }
    }

    /**
     * One entry of a rotation's matrix.
     *
     * @param rotation The rotation.
     * @param row The row.
     * @param column The column.
     * @return -1, 0 or 1.
     */
    public static int element(int rotation, int row, int column) {
        return MATRICES[rotation][row * 3 + column];
    }

    /**
     * Where a face ends up.
     *
     * @param rotation The rotation.
     * @param face The face before turning.
     * @return The face after turning.
     */
    public static Face turn(int rotation, @NonNull Face face) {
        return FACES[rotation][face.ordinal()];
    }

    /**
     * Turn a face's coverage mask, giving the mask of the face it turns into.
     *
     * @param mask The mask on {@code face}.
     * @param rotation The rotation.
     * @param face The face the mask is on.
     * @return A new mask, on {@link #turn(int, Face)}.
     */
    public static long[] turnMask(long @NonNull [] mask, int rotation, @NonNull Face face) {
        if (rotation == IDENTITY) {
            return mask.clone();
        }
        final int[] cells = CELLS[rotation][face.ordinal()];
        final long[] turned = new long[FaceCoverage.WORDS];
        for (int cell = 0; cell < FaceCoverage.CELL_COUNT; ++cell) {
            if (FaceCoverage.get(mask, cell)) {
                FaceCoverage.set(turned, cells[cell]);
            }
        }
        return turned;
    }

    /**
     * Turn a face mask: each face's bit moves to the face it turns into.
     *
     * @param faces The face mask, one bit per {@link Face#bit()}.
     * @param rotation The rotation.
     * @return The turned face mask.
     */
    public static int turnFaces(int faces, int rotation) {
        int turned = 0;
        for (Face face : Face.ALL) {
            if ((faces & face.bit()) != 0) {
                turned |= turn(rotation, face).bit();
            }
        }
        return turned;
    }

    /**
     * The rotation that turns by {@code second} after {@code first}.
     *
     * @param first The rotation applied first.
     * @param second The rotation applied after it.
     * @return The combined rotation.
     */
    public static int compose(int first, int second) {
        final int[] a = MATRICES[second];
        final int[] b = MATRICES[first];
        final int[] product = new int[9];
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 3; ++column) {
                int sum = 0;
                for (int k = 0; k < 3; ++k) {
                    sum += a[row * 3 + k] * b[k * 3 + column];
                }
                product[row * 3 + column] = sum;
            }
        }
        for (int rotation = 0; rotation < COUNT; ++rotation) {
            if (Arrays.equals(MATRICES[rotation], product)) {
                return rotation;
            }
        }
        throw new IllegalStateException("Two cube rotations combined into something else");
    }

    /**
     * The rotation that undoes a rotation.
     *
     * @param rotation The rotation.
     * @return Its inverse.
     */
    public static int inverse(int rotation) {
        return INVERSES[rotation];
    }

    /** Static helpers only. */
    private CubeRotations() {}
}
