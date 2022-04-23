package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * A material for rendering.
 */
@Getter
@Setter
public class Material {

	private static final Vector4f DEFAULT_COLOR =
		new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

	/**
	 * The ambient color of the material.
	 *
	 * @param ambientColor The new color.
	 * @return The ambient color.
	 */
	private Vector4f ambientColor;

	/**
	 * The diffuse color of the material.
	 *
	 * @param diffuseColor The new color.
	 * @return The diffuse color.
	 */
	private Vector4f diffuseColor;

	/**
	 * The reflectance index of the material.
	 *
	 * @param reflectance The new reflectance.
	 * @return The reflectance index.
	 */
	private float reflectance;

	/**
	 * The specular color of the material.
	 *
	 * @param specularColor The new color.
	 * @return The specular color.
	 */
	private Vector4f specularColor;

	/**
	 * The materials texture, might be null.
	 *
	 * @param texture The texture to use.
	 * @return The texture for this material.
	 */
	private Texture texture;

	/**
	 * Create a default, non-reflective material with no texture.
	 */
	public Material() {
		this.ambientColor = Material.DEFAULT_COLOR;
		this.diffuseColor = Material.DEFAULT_COLOR;
		this.specularColor = Material.DEFAULT_COLOR;
		this.texture = null;
		this.reflectance = 0;
	}

	/**
	 * Create a material with all default colors, and no reflectance, but with a
	 * texture.
	 *
	 * @param texture The texture to use for the material.
	 */
	public Material(Texture texture) {
		this(Material.DEFAULT_COLOR, Material.DEFAULT_COLOR,
			Material.DEFAULT_COLOR, texture, 0);
	}

	/**
	 * Create a material with all default colors, but with reflectance and a
	 * texture.
	 *
	 * @param texture The texture to use for the material.
	 * @param reflectance The reflectance index.
	 */
	public Material(Texture texture, float reflectance) {
		this(Material.DEFAULT_COLOR, Material.DEFAULT_COLOR,
			Material.DEFAULT_COLOR, texture, reflectance);
	}

	/**
	 * Create a material with the given color for ambient, diffuse, and
	 * specular, with the given reflectance.
	 *
	 * @param color The color to use for the material.
	 * @param reflectance The reflectance index.
	 */
	public Material(Vector4f color, float reflectance) {
		this(color, color, color, null, reflectance);
	}

	/**
	 * Creates a material with all parameters.
	 *
	 * @param ambientColor The ambient color.
	 * @param diffuseColor The diffuse color.
	 * @param specularColor The specular color.
	 * @param texture The texture.
	 * @param reflectance The reflectance index.
	 */
	public Material(Vector4f ambientColor, Vector4f diffuseColor,
		Vector4f specularColor, Texture texture, float reflectance) {
		this.ambientColor = ambientColor;
		this.diffuseColor = diffuseColor;
		this.specularColor = specularColor;
		this.texture = texture;
		this.reflectance = reflectance;
	}

	/**
	 * Checks if there is a texture set. Equivalent to
	 * {@link #getTexture()}{@code != null}.
	 *
	 * @return True if texture is not null, false if it is null.
	 */
	public boolean isTextured() {
		return this.texture != null;
	}

}