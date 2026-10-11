package com.ikalagaming.factory.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.lod.DagBuilder;
import com.ikalagaming.graphics.lod.LodCut;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.lod.LodMesh;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tests for building level of detail from Lab chunks.
 *
 * @author Ches Burks
 */
class TestLabLod {

    /** The stone block. */
    private static final String STONE = "t:stone";

    /** The grass block. */
    private static final String GRASS = "t:grass";

    /** The water block, a fluid. */
    private static final String WATER = "t:water";

    /** Stone's material index. */
    private static final int STONE_MATERIAL = 1;

    /** Grass's material index. */
    private static final int GRASS_MATERIAL = 2;

    /** Water's material index. */
    private static final int WATER_MATERIAL = 3;

    /** Grass's top material index in these tests. */
    private static final int GRASS_TOP_MATERIAL = 4;

    /** The unit box's minimum corner. */
    private static final Vector3f BOX_MIN = new Vector3f(0, 0, 0);

    /** The unit box's maximum corner. */
    private static final Vector3f BOX_MAX = new Vector3f(1, 1, 1);

    /** Chunks along each side of the test box. */
    private static final int SIDE = 2;

    /** Where the water's surface is. */
    private static final int WATER_LEVEL = 12;

    /** Screen pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /**
     * A block's entry, as the preview registers it, without a GPU.
     *
     * @param translucent Whether it is a fluid.
     * @return The entry.
     */
    private static BlockMeshes.Entry entry(boolean translucent) {
        Material material = new Material();
        material.setTransparency(
                translucent ? Material.Transparency.TRANSLUCENT : Material.Transparency.OPAQUE);
        final MeshData mesh =
                com.ikalagaming.graphics.benchmark.BlockMeshes.box(
                        new float[] {0, 0, 0}, new float[] {1, 1, 1}, material);
        return new BlockMeshes.Entry(
                new MeshHandle(0, 0, 0, 0, BOX_MIN, BOX_MAX, MeshKind.STANDARD),
                BakeSource.derive(mesh, BOX_MIN, BOX_MAX),
                material);
    }

    /**
     * A box of hilly chunks: stone, grass on top, water filling the low ground.
     *
     * @return The chunks.
     */
    private static Map<ChunkPos, Chunk> hills() {
        final Block stone = new Block(STONE, null);
        final Block grass = new Block(GRASS, null);
        final Block water = new Block(WATER, null);
        Map<ChunkPos, Chunk> chunks = new LinkedHashMap<>();
        for (int cy = 0; cy < SIDE; ++cy) {
            for (int cz = 0; cz < SIDE; ++cz) {
                for (int cx = 0; cx < SIDE; ++cx) {
                    Chunk chunk = new Chunk(World.AIR, "t:plain");
                    for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                        for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                            final int wx = cx * World.CHUNK_SIZE + x;
                            final int wz = cz * World.CHUNK_SIZE + z;
                            final int height =
                                    (int) (10 + 6 * Math.sin(wx * 0.3) * Math.cos(wz * 0.25));
                            for (int y = 0; y < World.CHUNK_SIZE; ++y) {
                                final int wy = cy * World.CHUNK_SIZE + y;
                                if (wy < height) {
                                    chunk.setBlock(x, y, z, stone);
                                } else if (wy == height) {
                                    chunk.setBlock(x, y, z, grass);
                                } else if (wy <= WATER_LEVEL) {
                                    chunk.setBlock(x, y, z, water);
                                }
                            }
                        }
                    }
                    chunks.put(new ChunkPos(cx, cy, cz), chunk);
                }
            }
        }
        return chunks;
    }

    /** Chunks bake into opaque meshes whose DAG has no cracks in any cut. */
    @Test
    void testBakesCrackFree() {
        final Map<String, BlockMeshes.Entry> meshes = new HashMap<>();
        meshes.put(STONE, entry(false));
        meshes.put(GRASS, entry(false));
        meshes.put(WATER, entry(true));
        // Grass has its own material on top, by the side, top, bottom order bakeByFace takes
        final Map<String, int[]> materials =
                Map.of(
                        STONE,
                        new int[] {STONE_MATERIAL, STONE_MATERIAL, STONE_MATERIAL},
                        GRASS,
                        new int[] {GRASS_MATERIAL, GRASS_TOP_MATERIAL, GRASS_MATERIAL},
                        WATER,
                        new int[] {WATER_MATERIAL, WATER_MATERIAL, WATER_MATERIAL});
        final List<LodMesh> baked = LabLod.bake(hills(), meshes, materials);
        assertFalse(baked.isEmpty());
        boolean grassSeen = false;
        boolean grassTopSeen = false;
        for (LodMesh mesh : baked) {
            for (int t = 0; t < mesh.getTriangleCount(); ++t) {
                assertTrue(mesh.material(t) != WATER_MATERIAL, "Water is left out");
                grassSeen |= mesh.material(t) == GRASS_MATERIAL;
                grassTopSeen |= mesh.material(t) == GRASS_TOP_MATERIAL;
            }
        }
        assertTrue(grassSeen, "Grass sides");
        assertTrue(grassTopSeen, "Grass tops");

        final LodDag dag = DagBuilder.build(baked, new DagBuilder.Options(0.5, true, 2, false, 0));
        assertTrue(dag.getLevels() >= 3, dag.getLevels() + " levels");
        final CrackCheck check = new CrackCheck(dag);
        Random random = new Random(7);
        for (int cut = 0; cut < 50; ++cut) {
            final LodCut.View view =
                    new LodCut.View(
                            random.nextDouble() * 96 - 32,
                            random.nextDouble() * 96 - 16,
                            random.nextDouble() * 96 - 32,
                            PIXELS);
            final CrackCheck.Result result =
                    check.check(LodCut.select(dag, view, 0.25 + random.nextDouble() * 16));
            assertEquals(0, result.openEdges().size(), "Cut " + cut + " cracks");
        }
    }
}
