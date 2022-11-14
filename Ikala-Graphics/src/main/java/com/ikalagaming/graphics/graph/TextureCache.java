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
		textureMap = new HashMap<>();
		defaultTexture = new Texture(DEFAULT_TEXTURE_PATH);
		textureMap.put(DEFAULT_TEXTURE_PATH, defaultTexture);
	}

	/**
	 * Clean up all the textures.
	 */
	public void cleanup() {
		textureMap.values().stream().forEach(Texture::cleanup);
		textureMap.clear();
	}

	/**
	 * Create a new texture if we don't already have one for the given path.
	 * 
	 * @param texturePath The name of the file for the texture, including full
	 *            path.
	 * @return The newly created texture.
	 */
	public Texture createTexture(String texturePath) {
		return textureMap.computeIfAbsent(texturePath, Texture::new);
	}

	/**
	 * Return a collection of all textures in the cache.
	 * 
	 * @return The actual textures in the cache.
	 */
	public Collection<Texture> getAll() {
		return textureMap.values();
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
			return defaultTexture;
		}
		return textureMap.getOrDefault(texturePath, defaultTexture);
	}
}
