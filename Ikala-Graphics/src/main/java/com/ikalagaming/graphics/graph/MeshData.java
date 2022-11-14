package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Raw mesh data that is loaded from a file, after some processing.
 */
@Getter
public class MeshData {
	/**
	 * The maximum value (corner) of the axis-aligned bounding box that is
	 * generated by Assimp.
	 *
	 * @return The max value of the AABB.
	 */
	private Vector3f aabbMax;
	/**
	 * The minimum value (corner) of the axis-aligned bounding box that is
	 * generated by Assimp.
	 *
	 * @return The min value of the AABB.
	 */
	private Vector3f aabbMin;
	/**
	 * A list of bitangent values, in x, y, z order. Bitangents point in the
	 * direction of the positive Y texture axis.
	 *
	 * @return The values of tangents.
	 */
	private float[] bitangents;
	/**
	 * Lists of bone IDs that affect the vertices, ordered by vertex ID. Each
	 * vertex has {@link Mesh#MAX_WEIGHTS} entries in the list, and if there are
	 * less than that number of bones affecting the vertex, the remaining
	 * positions are filled with zeroes. Each index in this list is the bone ID
	 * corresponding to the weight in the weight list at the same position.
	 *
	 * @return The bone ID list.
	 */
	private int[] boneIndices;
	/**
	 * The indices of all the faces.
	 *
	 * @return The indices.
	 */
	private int[] indices;
	/**
	 * A mesh uses only a single material, so if an imported model uses multiple
	 * materials, the import splits up the mesh. This value is used to index
	 * into the scene's material list.
	 *
	 * @param materialIndex The index to use.
	 * @return The material index.
	 */
	@Setter
	private int materialIndex;
	/**
	 * A list of normal values, in x, y, z order.
	 *
	 * @return The values of normals.
	 */
	private float[] normals;
	/**
	 * A list of vertex coordinates, in x, y, z order.
	 *
	 * @return The coordinates of vertices.
	 */
	private float[] positions;
	/**
	 * A list of tangent values, in x, y, z order. Tangents point in the
	 * direction of the positive X texture axis.
	 *
	 * @return The values of tangents.
	 */
	private float[] tangents;
	/**
	 * A list of texture coordinates, in x, y order.
	 *
	 * @return The values of the texture coordinates.
	 */
	private float[] textureCoordinates;
	/**
	 * Lists of weights that affect the vertices, ordered by vertex ID. Each
	 * vertex has {@link Mesh#MAX_WEIGHTS} entries in the list, and if there are
	 * less than that number of bones affecting the vertex, the remaining
	 * positions are filled with zeroes. Each index in this list is the weight
	 * corresponding to the bone ID in the bone ID list at the same position.
	 *
	 * @return The weight list.
	 */
	private float[] weights;

	/**
	 * Create new mesh data.
	 *
	 * @param positions A list of tangent values, in x, y, z order.
	 * @param normals A list of vertex coordinates, in x, y, z order.
	 * @param tangents A list of tangent values, in x, y, z order. Tangents
	 *            point in the direction of the positive X texture axis.
	 * @param bitangents A list of bitangent values, in x, y, z order.
	 *            Bitangents point in the direction of the positive Y texture
	 *            axis.
	 * @param textureCoordinates A list of texture coordinates, in x, y order.
	 * @param indices The indices of all the faces.
	 * @param boneIndices Lists of bone IDs that affect the vertices, ordered by
	 *            vertex ID. Each vertex has {@link Mesh#MAX_WEIGHTS} entries in
	 *            the list.
	 * @param weights Lists of weights that affect the vertices, ordered by
	 *            vertex ID. Each vertex has {@link Mesh#MAX_WEIGHTS} entries in
	 *            the list.
	 * @param aabbMin The minimum value (corner) of the axis-aligned bounding
	 *            box that is generated by Assimp.
	 * @param aabbMax The maximum value (corner) of the axis-aligned bounding
	 *            box that is generated by Assimp.
	 */
	public MeshData(float[] positions, float[] normals, float[] tangents,
		float[] bitangents, float[] textureCoordinates, int[] indices,
		int[] boneIndices, float[] weights, Vector3f aabbMin,
		Vector3f aabbMax) {
		this.materialIndex = 0;
		this.positions = positions;
		this.normals = normals;
		this.tangents = tangents;
		this.bitangents = bitangents;
		this.textureCoordinates = textureCoordinates;
		this.indices = indices;
		this.boneIndices = boneIndices;
		this.weights = weights;
		this.aabbMin = aabbMin;
		this.aabbMax = aabbMax;
	}
}
