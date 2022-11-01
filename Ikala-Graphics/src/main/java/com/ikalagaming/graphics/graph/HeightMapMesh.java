package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Utils;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;

import lombok.Getter;
import org.joml.Vector3f;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to create a mesh out of a height map texture.
 *
 * @author Ches Burks
 *
 */
public class HeightMapMesh {

	/**
	 * We use the three RGB components of PNG to allow for more values.
	 */
	private static final int MAX_COLOR = 255 * 255 * 255;

	/**
	 * The starting x position.
	 */
	static final float STARTX = -0.5f;
	/**
	 * The starting z position.
	 */
	static final float STARTZ = -0.5f;

	/**
	 * Get the size of the height map in the x dimension.
	 *
	 * @return The length in the x dimension.
	 */
	public static float getXLength() {
		return Math.abs(-HeightMapMesh.STARTX * 2);
	}

	/**
	 * Get the size of the height map in the z dimension.
	 *
	 * @return The length in the z dimension.
	 */
	public static float getZLength() {
		return Math.abs(-HeightMapMesh.STARTZ * 2);
	}

	private final float minY;
	private final float maxY;

	/**
	 * The width of the height map file in pixels.
	 */
	@Getter
	private int width;

	/**
	 * The height of the height map file in pixels.
	 */
	@Getter
	private int height;

	/**
	 * The mesh generated from the height map.
	 *
	 * @return The generated mesh.
	 */
	@Getter
	private final Mesh mesh;

	/**
	 * Cached position array for quickly referencing the height at any pixel.
	 */
	private float[] positionArray;

	/**
	 * Create a new height map mesh from a height map file.
	 *
	 * @param minY The lowest Y value.
	 * @param maxY The highest Y value.
	 * @param heightMapFile The height map file to generate a mesh from.
	 * @param textureFile The texture to apply to the mesh.
	 * @param textInc A multiplier for texture coordinates used to increase the
	 *            number of pixels of the texture to be used between adjacent
	 *            vertices.
	 * @throws Exception If something has gone wrong.
	 */
	public HeightMapMesh(float minY, float maxY, String heightMapFile,
		String textureFile, int textInc) throws Exception {
		this.minY = minY;
		this.maxY = maxY;

		ByteBuffer buffer = null;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);

			buffer = STBImage.stbi_load(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, heightMapFile).getAbsolutePath(),
				w, h, channels, 4);
			if (buffer == null) {
				throw new Exception(
					"Image file ["
						+ PluginFolder
							.getResource(GraphicsPlugin.PLUGIN_NAME,
								ResourceType.DATA, heightMapFile)
							.getAbsolutePath()
						+ "] not loaded: " + STBImage.stbi_failure_reason());
			}

			this.width = w.get();
			this.height = h.get();
		}

		Texture texture = new Texture(textureFile);

		float incX = HeightMapMesh.getXLength() / (this.width - 1);
		float incZ = HeightMapMesh.getZLength() / (this.height - 1);

		List<Float> positions = new ArrayList<>();
		List<Float> textCoords = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();

		for (int row = 0; row < this.height; row++) {
			for (int col = 0; col < this.width; col++) {
				// Create vertex for current position
				positions.add(HeightMapMesh.STARTX + col * incX); // x
				positions.add(this.calculateHeight(col, row, buffer)); // y
				positions.add(HeightMapMesh.STARTZ + row * incZ); // z

				// Set texture coordinates
				textCoords.add((float) textInc * (float) col / this.width);
				textCoords.add((float) textInc * (float) row / this.height);

				// Create indices
				if (col < this.width - 1 && row < this.height - 1) {
					int topLeft = row * this.width + col;
					int bottomLeft = (row + 1) * this.width + col;
					int topRight = row * this.width + col + 1;
					int bottomRight = (row + 1) * this.width + col + 1;

					indices.add(topLeft);
					indices.add(bottomLeft);
					indices.add(topRight);

					indices.add(topRight);
					indices.add(bottomLeft);
					indices.add(bottomRight);
				}
			}
		}

		this.positionArray = Utils.listToArray(positions);
		int[] indicesArray = indices.stream().mapToInt(i -> i).toArray();
		float[] textCoordsArray = Utils.listToArray(textCoords);
		float[] normalsArray =
			this.calculateNormals(this.positionArray, this.width, this.height);
		this.mesh = new Mesh(this.positionArray, textCoordsArray, normalsArray,
			indicesArray);
		Material material = new Material(texture, 0.0f);
		this.mesh.setMaterial(material);

		STBImage.stbi_image_free(buffer);
	}

	/**
	 * Calculate the height at a position in a byte buffer, based on the colors.
	 *
	 * @param x The x coordinate.
	 * @param z The z coordinate.
	 * @param buffer The byte buffer to read from.
	 * @return The data to read from.
	 */
	private float calculateHeight(final int x, final int z,
		final ByteBuffer buffer) {
		byte r = buffer.get(x * 4 + 0 + z * 4 * this.width);
		byte g = buffer.get(x * 4 + 1 + z * 4 * this.width);
		byte b = buffer.get(x * 4 + 2 + z * 4 * this.width);
		byte a = buffer.get(x * 4 + 3 + z * 4 * this.width);
		int argb = ((0xFF & a) << 24) | ((0xFF & r) << 16) | ((0xFF & g) << 8)
			| (0xFF & b);
		return this.minY + Math.abs(this.maxY - this.minY)
			* ((float) argb / (float) HeightMapMesh.MAX_COLOR);
	}

	/**
	 * Calculate normals for an array of positions.
	 *
	 * @param posArray The array of positions to calculate normals of.
	 * @param columns The number of columns.
	 * @param rows The number of rows.
	 * @return The normal array.
	 */
	private float[] calculateNormals(float[] posArray, int columns, int rows) {
		Vector3f v0 = new Vector3f();
		Vector3f v1 = new Vector3f();
		Vector3f v2 = new Vector3f();
		Vector3f v3 = new Vector3f();
		Vector3f v4 = new Vector3f();
		Vector3f v12 = new Vector3f();
		Vector3f v23 = new Vector3f();
		Vector3f v34 = new Vector3f();
		Vector3f v41 = new Vector3f();
		List<Float> normals = new ArrayList<>();
		Vector3f normal = new Vector3f();
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < columns; col++) {
				if (row > 0 && row < rows - 1 && col > 0 && col < columns - 1) {
					int i0 = row * columns * 3 + col * 3;
					v0.x = posArray[i0];
					v0.y = posArray[i0 + 1];
					v0.z = posArray[i0 + 2];

					int i1 = row * columns * 3 + (col - 1) * 3;
					v1.x = posArray[i1];
					v1.y = posArray[i1 + 1];
					v1.z = posArray[i1 + 2];
					v1 = v1.sub(v0);

					int i2 = (row + 1) * columns * 3 + col * 3;
					v2.x = posArray[i2];
					v2.y = posArray[i2 + 1];
					v2.z = posArray[i2 + 2];
					v2 = v2.sub(v0);

					int i3 = (row) * columns * 3 + (col + 1) * 3;
					v3.x = posArray[i3];
					v3.y = posArray[i3 + 1];
					v3.z = posArray[i3 + 2];
					v3 = v3.sub(v0);

					int i4 = (row - 1) * columns * 3 + col * 3;
					v4.x = posArray[i4];
					v4.y = posArray[i4 + 1];
					v4.z = posArray[i4 + 2];
					v4 = v4.sub(v0);

					v1.cross(v2, v12);
					v12.normalize();

					v2.cross(v3, v23);
					v23.normalize();

					v3.cross(v4, v34);
					v34.normalize();

					v4.cross(v1, v41);
					v41.normalize();

					normal = v12.add(v23).add(v34).add(v41);
					normal.normalize();
				}
				else {
					normal.x = 0;
					normal.y = 1;
					normal.z = 0;
				}
				normal.normalize();
				normals.add(normal.x);
				normals.add(normal.y);
				normals.add(normal.z);
			}
		}
		return Utils.listToArray(normals);
	}

	/**
	 * Fetch the height at a given position.
	 *
	 * @param x The x position in pixels.
	 * @param z The z position in pixels.
	 * @return The height at the given position.
	 */
	public float getHeight(final int x, final int z) {
		if (x < 0 || x > this.width || z < 0 || z > this.height) {
			return 0f;
		}
		int i = x * this.width * 3 + z * 3;
		return this.positionArray[i + 1];
	}

}
