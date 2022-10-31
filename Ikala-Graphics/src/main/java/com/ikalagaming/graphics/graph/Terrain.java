package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.Rect2D;
import com.ikalagaming.graphics.SceneItem;

import lombok.Getter;
import org.joml.Vector3f;

/**
 * A terrain built upon height maps.
 *
 * @author Ches Burks
 *
 */
public class Terrain {

	private final int blocksPerRow;

	/**
	 * The list of scene items that are comprised of height map meshes.
	 *
	 * @return The list of scene items that make up the terrain.
	 */
	@Getter
	private final SceneItem[] sceneItems;

	private final Rect2D[][] boundingBoxes;

	private HeightMapMesh heightMapMesh;

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
	public Terrain(final int blocksPerRow, final float scale, final float minY,
		final float maxY, final String heightMap, final String textureFile,
		final int textInc) throws Exception {

		this.blocksPerRow = blocksPerRow;
		this.sceneItems = new SceneItem[blocksPerRow * blocksPerRow];

		this.heightMapMesh =
			new HeightMapMesh(minY, maxY, heightMap, textureFile, textInc);
		this.boundingBoxes = new Rect2D[blocksPerRow][blocksPerRow];

		for (int row = 0; row < blocksPerRow; ++row) {
			for (int col = 0; col < blocksPerRow; ++col) {
				float xDisplacement = (col - ((float) blocksPerRow - 1) / 2)
					* scale * HeightMapMesh.getXLength();
				float zDisplacement = (row - ((float) blocksPerRow - 1) / 2)
					* scale * HeightMapMesh.getZLength();

				SceneItem terrainBlock =
					new SceneItem(this.heightMapMesh.getMesh());
				terrainBlock.setScale(scale);
				terrainBlock.setPosition(xDisplacement, 0, zDisplacement);
				this.sceneItems[row * blocksPerRow + col] = terrainBlock;

				this.boundingBoxes[row][col] =
					this.getBoundingBox(terrainBlock);
			}
		}
	}

	/**
	 * Calculate the bounding box for a given terrain block.
	 *
	 * @param terrainBlock The block of terrain we want a bounding box for.
	 * @return The bounding box for the given terrain block.
	 */
	private Rect2D getBoundingBox(SceneItem terrainBlock) {
		float scale = terrainBlock.getScale();
		Vector3f position = terrainBlock.getPosition();

		float topLeftX = HeightMapMesh.STARTX * scale + position.x;
		float topLeftZ = HeightMapMesh.STARTZ * scale + position.z;
		float width = Math.abs(HeightMapMesh.STARTX * 2) * scale;
		float height = Math.abs(HeightMapMesh.STARTZ * 2) * scale;
		Rect2D boundingBox = new Rect2D(topLeftX, topLeftZ, width, height);
		return boundingBox;
	}

	/**
	 * Calculates the z coordinate of a diagonal, given an x position and two
	 * vertices, (x1, z1) and (x2, z2).
	 *
	 * @param x1 The x coordinate of the first vertex.
	 * @param z1 The z coordinate of the first vertex.
	 * @param x2 The x coordinate of the second vertex.
	 * @param z2 The z coordinate of the second vertex.
	 * @param x The x coordinate along the diagonal that we want a corresponding
	 *            z coordinate for.
	 * @return The z coordinate at the x position along the given diagonal.
	 */
	protected float getDiagonalZCoord(float x1, float z1, float x2, float z2,
		float x) {
		float z = ((z1 - z2) / (x1 - x2)) * (x - x1) + z1;
		return z;
	}

	/**
	 * Calculate the height of any given position.
	 *
	 * @param position The position in world coordinates.
	 * @return The height in world coordinates.
	 */
	public float getHeight(Vector3f position) {
		float result = Float.MIN_VALUE;
		/*
		 * For each terrain block we get the bounding box, translate it to view
		 * coodinates and check if the position is contained in that bounding
		 * box
		 */
		Rect2D boundingBox = null;
		boolean found = false;
		SceneItem terrainBlock = null;
		for (int row = 0; row < this.blocksPerRow && !found; row++) {
			for (int col = 0; col < this.blocksPerRow && !found; col++) {
				terrainBlock = this.sceneItems[row * this.blocksPerRow + col];
				boundingBox = this.boundingBoxes[row][col];
				found = boundingBox.contains(position.x, position.z);
			}
		}

		/*
		 * If we have found a terrain block that contains the position we need
		 * to calculate the height of the terrain on that position
		 */
		if (found) {
			Vector3f[] triangle =
				this.getTriangle(position, boundingBox, terrainBlock);
			result = this.interpolateHeight(triangle[0], triangle[1],
				triangle[2], position.x, position.z);
		}

		return result;
	}

	/**
	 * Fetch whichever of the two triangles making up a bounding box contain the
	 * position we are looking for.
	 *
	 * @param position The position we are looking for.
	 * @param boundingBox The bounding box containing the position.
	 * @param terrainBlock The terrain block containing the position.
	 * @return The triangle that the given position is in.
	 */
	protected Vector3f[] getTriangle(Vector3f position, Rect2D boundingBox,
		SceneItem terrainBlock) {
		/*
		 * Get the column and row of the height map associated to the current
		 * position
		 */
		float cellWidth =
			boundingBox.getWidth() / this.heightMapMesh.getWidth();
		float cellHeight =
			boundingBox.getHeight() / this.heightMapMesh.getHeight();
		int col = (int) ((position.x - boundingBox.getX()) / cellWidth);
		int row = (int) ((position.z - boundingBox.getY()) / cellHeight);

		Vector3f[] triangle = new Vector3f[3];
		triangle[1] = new Vector3f(boundingBox.getX() + col * cellWidth,
			this.getWorldHeight(row + 1, col, terrainBlock),
			boundingBox.getY() + (row + 1) * cellHeight);
		triangle[2] = new Vector3f(boundingBox.getX() + (col + 1) * cellWidth,
			this.getWorldHeight(row, col + 1, terrainBlock),
			boundingBox.getY() + row * cellHeight);

		if (position.z < this.getDiagonalZCoord(triangle[1].x, triangle[1].z,
			triangle[2].x, triangle[2].z, position.x)) {
			triangle[0] = new Vector3f(boundingBox.getX() + col * cellWidth,
				this.getWorldHeight(row, col, terrainBlock),
				boundingBox.getY() + row * cellHeight);
		}
		else {
			triangle[0] =
				new Vector3f(boundingBox.getX() + (col + 1) * cellWidth,
					this.getWorldHeight(row + 2, col + 1, terrainBlock),
					boundingBox.getY() + (row + 1) * cellHeight);
		}

		return triangle;
	}

	/**
	 * Convert the height map height to world scale.
	 *
	 * @param row The row (x) coordinate.
	 * @param column The column (z) coordinate.
	 * @param sceneItem the scene item we want to get scale and position from.
	 * @return The height in world coordinates.
	 */
	protected float getWorldHeight(final int row, final int column,
		SceneItem sceneItem) {
		final float y = this.heightMapMesh.getHeight(row, column);
		return y * sceneItem.getScale() + sceneItem.getPosition().y;
	}

	/**
	 * Calculates the height at the given x and z coordinates along the plane
	 * defined by three vertices.
	 *
	 * @param vertex1 The first vertex.
	 * @param vertex2 The second vertex.
	 * @param vertex3 The third vertex.
	 * @param x The x coordinate.
	 * @param z The z coordinate.
	 * @return The height at the given coordinates on the given plane.
	 */
	protected float interpolateHeight(Vector3f vertex1, Vector3f vertex2,
		Vector3f vertex3, float x, float z) {
		// Plane equation: ax + by + cz + d = 0
		final float a = (vertex2.y - vertex1.y) * (vertex3.z - vertex1.z)
			- (vertex3.y - vertex1.y) * (vertex2.z - vertex1.z);
		final float b = (vertex2.z - vertex1.z) * (vertex3.x - vertex1.x)
			- (vertex3.z - vertex1.z) * (vertex2.x - vertex1.x);
		final float c = (vertex2.x - vertex1.x) * (vertex3.y - vertex1.y)
			- (vertex3.x - vertex1.x) * (vertex2.y - vertex1.y);
		final float d = -(a * vertex1.x + b * vertex1.y + c * vertex1.z);
		final float y = (-d - a * x - c * z) / b;
		return y;
	}

}