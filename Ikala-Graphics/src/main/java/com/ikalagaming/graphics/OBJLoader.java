package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.MeshException;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads meshes from OBJ files.
 */
@Slf4j
public class OBJLoader {

	/**
	 * A face triangle in the object.
	 */
	protected static class Face {

		/**
		 * List of idxGroup groups for a face triangle (3 vertices per face).
		 */
		private IndexGroup[] indexGroups = new IndexGroup[3];

		/**
		 * Create a new face from three index groups strings.
		 *
		 * @param v1 The first vertex.
		 * @param v2 The second vertex.
		 * @param v3 The third vertex.
		 */
		public Face(String v1, String v2, String v3) {
			this.indexGroups = new IndexGroup[3];
			// Parse the lines
			this.indexGroups[0] = this.parseLine(v1);
			this.indexGroups[1] = this.parseLine(v2);
			this.indexGroups[2] = this.parseLine(v3);
		}

		/**
		 * Gets the list of indexes for the face vertexes.
		 *
		 * @return The index groups.
		 */
		public IndexGroup[] getFaceVertexIndices() {
			return this.indexGroups;
		}

		/**
		 * Parse an index group line.
		 *
		 * @param line The line of the OBJ file.
		 * @return The newly created index group.
		 */
		private IndexGroup parseLine(String line) {
			IndexGroup indexGroup = new IndexGroup();
			String[] lineTokens = line.split("/");

			final int length = lineTokens.length;
			indexGroup.indexPos = Integer.parseInt(lineTokens[0]) - 1;
			if (length > 1) {
				/*
				 * It can be empty if the obj does not define texture
				 * coordinates
				 */
				String textCoord = lineTokens[1];
				indexGroup.indexTextureCoordinate =
					textCoord.length() > 0 ? Integer.parseInt(textCoord) - 1
						: IndexGroup.NO_VALUE;
				if (length > 2) {
					indexGroup.indexVectorNormal =
						Integer.parseInt(lineTokens[2]) - 1;
				}
			}

			return indexGroup;
		}
	}

	/**
	 * An index group used in parsing face definitions in OBJ files.
	 */
	protected static class IndexGroup {

		/**
		 * A value was not specified.
		 */
		public static final int NO_VALUE = -1;
		/**
		 * The position part of the group.
		 */
		public int indexPos;
		/**
		 * The texture coordinate part of the group.
		 */
		public int indexTextureCoordinate;
		/**
		 * The vector normal part of the group.
		 */
		public int indexVectorNormal;

		/**
		 * Create a new index group.
		 */
		public IndexGroup() {
			this.indexPos = IndexGroup.NO_VALUE;
			this.indexTextureCoordinate = IndexGroup.NO_VALUE;
			this.indexVectorNormal = IndexGroup.NO_VALUE;
		}
	}

	/**
	 * Load a mesh from OBJ file.
	 *
	 * @param fileName The path to the file, starting from the plugin data
	 *            directory.
	 * @return The newly created mesh.
	 * @throws MeshException If the mesh could not be loaded.
	 */
	public static Mesh loadMesh(String fileName) {
		File file = PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
			ResourceType.DATA, fileName);
		if (!file.canRead()) {
			OBJLoader.log
				.warn(SafeResourceLoader.getString("MESH_ERROR_MISSING",
					GraphicsPlugin.getResourceBundle()), fileName);
			throw new MeshException();
		}

		List<String> lines;
		try {
			lines = Files.readAllLines(file.toPath());
		}
		catch (IOException e) {
			OBJLoader.log
				.warn(SafeResourceLoader.getString("MESH_ERROR_READING",
					GraphicsPlugin.getResourceBundle()), fileName);
			throw new MeshException(e);
		}

		List<Vector3f> vertices = new ArrayList<>();
		List<Vector2f> textures = new ArrayList<>();
		List<Vector3f> normals = new ArrayList<>();
		List<Face> faces = new ArrayList<>();

		for (String line : lines) {
			String[] tokens = line.split("\\s+");
			switch (tokens[0]) {
				case "v":
					// Geometric vertex
					Vector3f vec3f = new Vector3f(Float.parseFloat(tokens[1]),
						Float.parseFloat(tokens[2]),
						Float.parseFloat(tokens[3]));
					vertices.add(vec3f);
					break;
				case "vt":
					// Texture coordinate
					Vector2f vec2f = new Vector2f(Float.parseFloat(tokens[1]),
						Float.parseFloat(tokens[2]));
					textures.add(vec2f);
					break;
				case "vn":
					// Vertex normal
					Vector3f vec3fNorm =
						new Vector3f(Float.parseFloat(tokens[1]),
							Float.parseFloat(tokens[2]),
							Float.parseFloat(tokens[3]));
					normals.add(vec3fNorm);
					break;
				case "f":
					Face face = new Face(tokens[1], tokens[2], tokens[3]);
					faces.add(face);
					break;
				default:
					// Ignore other lines
					break;
			}
		}
		return OBJLoader.reorderLists(vertices, textures, normals, faces);
	}

	/**
	 * Populates the indices list, texture coordinates, and normals using the
	 * provided lists.
	 *
	 * @param indices The index group for this face vertex.
	 * @param textCoordList The overall texture coordinate list.
	 * @param normList The overall normal list.
	 * @param indicesList The list of indices that we are building.
	 * @param texCoordArr The texture coordinate array we are building.
	 * @param normArr The normal array we are building.
	 */
	private static void processFaceVertex(IndexGroup indices,
		List<Vector2f> textCoordList, List<Vector3f> normList,
		List<Integer> indicesList, float[] texCoordArr, float[] normArr) {

		// Set index for vertex coordinates
		int posIndex = indices.indexPos;
		indicesList.add(posIndex);

		// Reorder texture coordinates
		if (indices.indexTextureCoordinate >= 0) {
			Vector2f textCoord =
				textCoordList.get(indices.indexTextureCoordinate);
			texCoordArr[posIndex * 2] = textCoord.x;
			texCoordArr[posIndex * 2 + 1] = 1 - textCoord.y;
		}
		if (indices.indexVectorNormal >= 0) {
			// Reorder vector normals
			Vector3f vecNorm = normList.get(indices.indexVectorNormal);
			normArr[posIndex * 3] = vecNorm.x;
			normArr[posIndex * 3 + 1] = vecNorm.y;
			normArr[posIndex * 3 + 2] = vecNorm.z;
		}
	}

	/**
	 * Reorders the information to fit what the Mesh class expects.
	 *
	 * @param posList The list of positions.
	 * @param textCoordList The list of texture coordinates.
	 * @param normList The list of normals.
	 * @param facesList The list of faces.
	 * @return A newly created mesh based on this information.
	 */
	private static Mesh reorderLists(List<Vector3f> posList,
		List<Vector2f> textCoordList, List<Vector3f> normList,
		List<Face> facesList) {

		List<Integer> indices = new ArrayList<>();
		// Create position array in the order it has been declared
		float[] posArr = new float[posList.size() * 3];
		int i = 0;
		for (Vector3f pos : posList) {
			posArr[i * 3] = pos.x;
			posArr[i * 3 + 1] = pos.y;
			posArr[i * 3 + 2] = pos.z;
			i++;
		}
		float[] textCoordArr = new float[posList.size() * 2];
		float[] normArr = new float[posList.size() * 3];

		for (Face face : facesList) {
			IndexGroup[] faceVertexIndices = face.getFaceVertexIndices();
			for (IndexGroup indValue : faceVertexIndices) {
				OBJLoader.processFaceVertex(indValue, textCoordList, normList,
					indices, textCoordArr, normArr);
			}
		}
		int[] indicesArr =
			indices.stream().mapToInt((Integer v) -> v).toArray();
		return new Mesh(posArr, textCoordArr, normArr, indicesArr);
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private OBJLoader() {}
}
