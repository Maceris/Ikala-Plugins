/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.NonNull;
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
		new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
	/**
	 * The ambient color of the material.
	 *
	 * @param ambientColor The new color.
	 * @return The ambient color.
	 */
	@NonNull
	private Vector4f ambientColor;
	/**
	 * The diffuse color of the material.
	 *
	 * @param diffuseColor The new color.
	 * @return The diffuse color.
	 */
	@NonNull
	private Vector4f diffuseColor;
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
	 * The path to the normal map for this material, from the resource
	 * directory.
	 *
	 * @param normalMapPath The file path to the normal map.
	 * @return The file path to the normal map.
	 */
	private String normalMapPath;
	/**
	 * The reflectance value of the material.
	 *
	 * @param reflectance The new color.
	 * @return The reflectance value.
	 */
	private float reflectance;
	/**
	 * The specular color of the material.
	 *
	 * @param specularColor The new color.
	 * @return The specular color.
	 */
	@NonNull
	private Vector4f specularColor;
	/**
	 * The path to the texture for this material, from the resource directory.
	 *
	 * @param texturePath The file path to the texture.
	 * @return The file path to the texture.
	 */
	private String texturePath;

	/**
	 * Create a default, non-reflective material with no texture.
	 */
	public Material() {
		this.diffuseColor = Material.DEFAULT_COLOR;
		this.ambientColor = Material.DEFAULT_COLOR;
		this.specularColor = Material.DEFAULT_COLOR;
		this.materialIndex = 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Material)) {
			return false;
		}
		Material other = (Material) obj;

		final float delta = 0.001f;
		if (!this.ambientColor.equals(other.ambientColor, delta)) {
			return false;
		}
		if (!this.diffuseColor.equals(other.diffuseColor, delta)) {
			return false;
		}
		if (!this.specularColor.equals(other.specularColor, delta)) {
			return false;
		}
		if (this.normalMapPath == null && other.normalMapPath != null) {
			return false;
		}
		if (this.normalMapPath != null
			&& !this.normalMapPath.equals(other.normalMapPath)) {
			return false;
		}
		if (this.texturePath == null && other.texturePath != null) {
			return false;
		}
		if (this.texturePath != null
			&& !this.texturePath.equals(other.texturePath)) {
			return false;
		}
		if (Math.abs(this.reflectance - other.reflectance) > delta) {
			return false;
		}

		return true;
	}
}