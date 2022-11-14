package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * A material for rendering that defines color and textures for a mesh.
 */
@Getter
@Setter
public class Material {

	/**
	 * The default color to use when unspecified.
	 */
	public static final Vector4f DEFAULT_COLOR =
		new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

	/**
	 * The diffuse color of the material.
	 *
	 * @param diffuseColor The new color.
	 * @return The diffuse color.
	 */
	private Vector4f diffuseColor;
	/**
	 * The ambient color of the material.
	 *
	 * @param ambientColor The new color.
	 * @return The ambient color.
	 */
	private Vector4f ambientColor;
	/**
	 * The specular color of the material.
	 *
	 * @param specularColor The new color.
	 * @return The specular color.
	 */
	private Vector4f specularColor;
	/**
	 * The reflectance value of the material.
	 *
	 * @param reflectance The new color.
	 * @return The reflectance value.
	 */
	private float reflectance;

	/**
	 * The path to the texture for this material, from the resource directory.
	 *
	 * @param texturePath The file path to the texture.
	 * @return The file path to the texture.
	 */
	private String texturePath;
	/**
	 * The path to the normal map for this material, from the resource
	 * directory.
	 *
	 * @param normalMapPath The file path to the normal map.
	 * @return The file path to the normal map.
	 */
	private String normalMapPath;
	/**
	 * A mesh uses only a single material, so if an imported model uses multiple
	 * materials, the import splits up the mesh. This value is used as to index
	 * into the scene's material list.
	 *
	 * @param materialIndex The index to use.
	 * @return The material index.
	 */
	private int materialIndex;

	/**
	 * Create a default, non-reflective material with no texture.
	 */
	public Material() {
		this.diffuseColor = Material.DEFAULT_COLOR;
		this.diffuseColor = Material.DEFAULT_COLOR;
		this.ambientColor = Material.DEFAULT_COLOR;
		this.specularColor = Material.DEFAULT_COLOR;
		this.materialIndex = 0;
	}

}