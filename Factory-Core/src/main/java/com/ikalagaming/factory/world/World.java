package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.Material;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Tracks the state of the world.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class World {
	/**
	 * Checks if the tag is in the provided list. If any tag matches the
	 * expected tag, or inherits from a tag that does, this will return true.
	 *
	 * @param tag The tag we are looking for.
	 * @param list The list of tags that might contain the desired tag.
	 * @return Whether the tag can be found in the list.
	 */
	public static boolean containsTag(@NonNull String tag, List<Tag> list) {
		for (Tag potential : list) {
			if (tag.equals(potential.name())) {
				return true;
			}
			// Check all parent tags too
			Tag parent = potential.parent();
			while (potential.parent() != null) {
				if (tag.equals(parent.name())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A list of tags that apply to nouns.
	 *
	 * @return The tags that apply to nouns.
	 */
	@Getter
	private List<Tag> tags = new ArrayList<>();

	/**
	 * A list of all materials.
	 *
	 * @return The materials that exist.
	 */
	@Getter
	private List<Material> materials = new ArrayList<>();

	/**
	 * Add a tag to the list of tags. If the tag already exists, this will fail.
	 * 
	 * @param tag The name of the tag.
	 * @return Whether we successfully added the tag.
	 */
	public boolean addTag(@NonNull String tag) {
		if (this.hasTag(tag)) {
			return false;
		}
		return this.tags.add(new Tag(tag, null));
	}

	/**
	 * Add a tag to the list of tags. If the tag already exists, or the parent
	 * does not, this will fail.
	 * 
	 * @param tag The name of the tag.
	 * @param parent The name of the parent tag.
	 * @return Whether we successfully added the tag.
	 */
	public boolean addTag(@NonNull String tag, @NonNull String parent) {
		if (this.hasTag(tag) || !this.hasTag(parent)) {
			return false;
		}
		return this.tags.add(new Tag(tag, this.findTag(parent)));
	}

	/**
	 * Remove any duplicate values in the list of tags. If there are both a
	 * generic tag and a more specific one that inherits from it, the generic
	 * tag will be removed.
	 *
	 * @param list The tags to de-duplicate. It is expected that the contents of
	 *            this list will be modified.
	 */
	private void deduplicateTags(@NonNull List<Tag> list) {
		if (list.isEmpty()) {
			// Well that was easy.
			return;
		}

		List<Tag> toRemove = new ArrayList<>();

		// It's easier to remove duplicates this way
		List<Tag> deduplicated = list.stream().distinct()
			.collect(Collectors.toCollection(ArrayList::new));

		for (Tag tag : deduplicated) {
			Tag parent = tag.parent();
			if (parent == null) {
				continue;
			}
			if (deduplicated.contains(parent)) {
				toRemove.add(parent);
			}
		}

		deduplicated.removeAll(toRemove);

		list.clear();
		list.addAll(deduplicated);
	}

	/**
	 * Look through the list of materials and search for one with the given
	 * name.
	 *
	 * @param materialName The name of the material to look for.
	 * @return The tag with the given name.
	 * @throws NullPointerException If the material is not found, or is null.
	 */
	public Material findMaterial(@NonNull String materialName) {
		for (Material material : this.materials) {
			if (materialName.equals(material.name())) {
				return material;
			}
		}
		World.log.error(SafeResourceLoader.getString("MATERIAL_MISSING",
			FactoryPlugin.getResourceBundle()), materialName);
		throw new NullPointerException();
	}

	/**
	 * Look through the list of tags and search for one with the given name.
	 *
	 * @param tagName The name of the tag to look for.
	 * @return The tag with the given name.
	 * @throws NullPointerException If the tag is not found, or is null.
	 */
	public Tag findTag(@NonNull String tagName) {
		for (Tag tag : this.tags) {
			if (tagName.equals(tag.name())) {
				return tag;
			}
		}
		World.log.error(SafeResourceLoader.getString("TAG_MISSING",
			FactoryPlugin.getResourceBundle()), tagName);
		throw new NullPointerException();
	}

	/**
	 * Checks if the specified material exists.
	 *
	 * @param tag The material we are looking for.
	 * @return Whether the material exists.
	 */
	public boolean hasMaterial(@NonNull String tag) {
		for (Material potential : this.materials) {
			if (potential.name().equals(tag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the specified tag exists.
	 *
	 * @param tag The tag we are looking for.
	 * @return Whether the tag exists.
	 */
	public boolean hasTag(@NonNull String tag) {
		for (Tag potential : this.tags) {
			if (potential.name().equals(tag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Load all the information from files.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	public boolean load() {
		// Short-circuiting will stop execution if one fails
		return this.loadTags() && this.loadMaterials();
	}

	/**
	 * Load and process materials from disk.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	private boolean loadMaterials() {
		Map<String, Object> materialMap = this.loadYaml("/materials.yml");
		if (materialMap.isEmpty()) {
			return false;
		}
		this.processMaterials(materialMap);

		World.log.debug(SafeResourceLoader.getString("LOADED_MATERIALS",
			FactoryPlugin.getResourceBundle()));
		return true;
	}

	/**
	 * Load the tags from files.
	 *
	 * @return Whether we were successful.
	 */
	private boolean loadTags() {
		Map<String, Object> tagMap = this.loadYaml("/tags.yml");
		if (tagMap.isEmpty()) {
			return false;
		}
		this.processTags(tagMap, null);
		World.log.debug(SafeResourceLoader.getString("LOADED_TAGS",
			FactoryPlugin.getResourceBundle()));
		return true;
	}

	/**
	 * Load a map structure based on the name of a data resource.
	 *
	 * @param resourceName The name of the resource, as a path from the data
	 *            directory.
	 * @return The contents of the yaml file, or an empty map in the case of an
	 *         error.
	 */
	private Map<String, Object> loadYaml(@NonNull String resourceName) {
		Yaml yaml = new Yaml();

		InputStream stream = this.getClass().getResourceAsStream(resourceName);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) yaml.load(stream);
		if (map == null) {
			World.log.warn(SafeResourceLoader.getString("FILE_EMPTY",
				FactoryPlugin.getResourceBundle()), resourceName);
			return new HashMap<>();
		}
		return map;
	}

	/**
	 * Process a map from snakeyaml and populate the material list with the
	 * results.
	 *
	 * @param map The nested map structure output by snakeyaml.
	 */
	private void processMaterials(@NonNull Map<String, Object> map) {

		try {
			for (Entry<String, Object> entry : map.entrySet()) {
				Object value = entry.getValue();
				if (!(value instanceof Map<?, ?>)) {
					World.log.warn(
						SafeResourceLoader.getString("MAT_INVALID_STRUCTURE",
							FactoryPlugin.getResourceBundle()));
					return;
				}

				@SuppressWarnings("unchecked")
				Map<String, Object> contents = (Map<String, Object>) value;

				@SuppressWarnings("unchecked")
				List<String> tagNames = (List<String>) contents.get("tags");

				List<Tag> tagValues = tagNames.stream().map(this::findTag)
					.collect(Collectors.toCollection(ArrayList::new));

				String parentName = (String) contents.get("parent");
				Material parent = null;
				if (parentName != null) {
					parent = findMaterial(parentName);
					// Inherit tags
					tagValues.addAll(parent.tags());
				}
				this.deduplicateTags(tagValues);

				Material newMaterial =
					new Material(entry.getKey(), tagValues, parent);
				this.materials.add(newMaterial);
			}
		}
		catch (Exception e) {
			World.log.warn(SafeResourceLoader.getString("MAT_INVALID_STRUCTURE",
				FactoryPlugin.getResourceBundle()));
		}
	}

	/**
	 * Process a map from snakeyaml and populate the tag list with the results.
	 *
	 * @param map The nested map structure output by snakeyaml.
	 * @param parent The parent tag, which is null for root tags.
	 */
	private void processTags(@NonNull Map<String, Object> map, Tag parent) {
		for (Entry<String, Object> entry : map.entrySet()) {
			Tag newTag = new Tag(entry.getKey(), parent);
			this.tags.add(newTag);

			Object value = entry.getValue();
			if (value instanceof Map<?, ?> child) {
				@SuppressWarnings("unchecked")
				Map<String, Object> cast = (Map<String, Object>) child;
				this.processTags(cast, newTag);
			}
		}
	}
}
