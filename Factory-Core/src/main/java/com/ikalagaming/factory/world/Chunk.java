package com.ikalagaming.factory.world;

/**
 * A region of blocks in the world.
 * 
 * Chunks have a {@value World#CHUNK_WIDTH}x{@value World#CHUNK_WIDTH} square
 * footprint but are {@value World#WORLD_HEIGHT_TOTAL} blocks tall.
 * 
 * @author Ches Burks
 *
 */
public class Chunk {
	private byte[][] biomes;

	Chunk() {
		biomes = new byte[World.CHUNK_WIDTH / 4][World.CHUNK_WIDTH / 4];
	}
}
