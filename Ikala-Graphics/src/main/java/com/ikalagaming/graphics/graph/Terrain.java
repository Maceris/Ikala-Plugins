package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.SceneItem;

import lombok.Getter;

/**
 * A terrain built upon height maps.
 *
 * @author Ches Burks
 *
 */
public class Terrain {

	/**
	 * The list of scene items that are comprised of height map meshes.
	 *
	 * @return The list of scene items that make up the terrain.
	 */
	@Getter
	private final SceneItem[] sceneItems;

	/**
	 * Create new terrain.
	 *
	 * @param blocksPerRow The number of blocks (height map meshes) on one side.
	 * @param scale How much the blocks are scaled.
	 * @param minY The smallest height value.
	 * @param maxY The largest height value.
	 * @param heightMap The name of the height map file to generate a mesh from.
	 * @param textureFile The name of the file for the texture to apply to the
	 *            mesh.
	 * @param textInc A multiplier for texture coordinates used to increase the
	 *            number of pixels of the texture to be used between adjacent
	 *            vertices.
	 * @throws Exception If there is a problem generating the meshes.
	 */
	public Terrain(int blocksPerRow, float scale, float minY, float maxY,
		String heightMap, String textureFile, int textInc) throws Exception {

		this.sceneItems = new SceneItem[blocksPerRow * blocksPerRow];
		HeightMapMesh heightMapMesh =
			new HeightMapMesh(minY, maxY, heightMap, textureFile, textInc);

		for (int row = 0; row < blocksPerRow; ++row) {
			for (int col = 0; col < blocksPerRow; ++col) {
				float xDisplacement = (col - ((float) blocksPerRow - 1) / 2)
					* scale * HeightMapMesh.getXLength();
				float zDisplacement = (row - ((float) blocksPerRow - 1) / 2)
					* scale * HeightMapMesh.getZLength();

				SceneItem terrainBlock = new SceneItem(heightMapMesh.getMesh());
				terrainBlock.setScale(scale);
				terrainBlock.setPosition(xDisplacement, 0, zDisplacement);
				this.sceneItems[row * blocksPerRow + col] = terrainBlock;
			}
		}
	}

}