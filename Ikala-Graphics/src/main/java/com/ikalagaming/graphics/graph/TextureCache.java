package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A cache of textures so that we can reuse them.
 */
public class TextureCache {
	/**
	 * The default texture to use if nothing is provided.
	 */
	public static final String DEFAULT_TEXTURE_PATH =
		PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME, ResourceType.DATA,
			"models/default/default_texture.png").getAbsolutePath();

	/**
	 * The default texture so that we have an easy reference.
	 */
	private final Texture defaultTexture;

	/**
	 * A cache of textures by path so we don't have to keep loading duplicate
	 * textures.
	 */
	private Map<String, Texture> textureMap;

	/**
	 * Create a new texture cache.
	 */
	public TextureCache() {
		this.textureMap = new HashMap<>();
		this.defaultTexture = new Texture(TextureCache.DEFAULT_TEXTURE_PATH);
		this.textureMap.put(TextureCache.DEFAULT_TEXTURE_PATH,
			this.defaultTexture);
	}

	/**
	 * Clean up all the textures.
	 */
	public void cleanup() {
		this.textureMap.values().stream().forEach(Texture::cleanup);
		this.textureMap.clear();
	}

	/**
	 * Create a new texture if we don't already have one for the given path. If
	 * you pass in a null texture path, the default texture is returned.
	 *
	 * @param texturePath The name of the file for the texture, including full
	 *            path.
	 * @return The newly created texture.
	 */
	public Texture createTexture(String texturePath) {
		if (texturePath == null) {
			return this.defaultTexture;
		}
		return this.textureMap.computeIfAbsent(texturePath, Texture::new);
	}

	/**
	 * Return a collection of all textures in the cache.
	 *
	 * @return The actual textures in the cache.
	 */
	public Collection<Texture> getAll() {
		return this.textureMap.values();
	}

	/**
	 * Fetch the texture if it exists, if there is no texture for the given path
	 * then the default texture is used.
	 *
	 * @param texturePath The name of the file for the texture, including path
	 *            from resource directory.
	 * @return The texture for that path.
	 */
	public Texture getTexture(String texturePath) {
		if (texturePath == null) {
			return this.defaultTexture;
		}
		return this.textureMap.getOrDefault(texturePath, this.defaultTexture);
	}
}
