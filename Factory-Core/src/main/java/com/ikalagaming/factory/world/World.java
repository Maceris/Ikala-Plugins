package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
	 * The number of blocks per side of a chunk.
	 */
	public static final int CHUNK_WIDTH = 16;

	/**
	 * The minimum y level of the world. Blocks can exist on this level, but
	 * nothing can be below it.
	 */
	public static final int WORLD_HEIGHT_MIN = -512;

	/**
	 * The maximum y level of the world. Blocks can exist on this level, but
	 * nothing can be above it.
	 */
	public static final int WORLD_HEIGHT_MAX = 512;

	/**
	 * The total height of the world in blocks.
	 */
	public static final int WORLD_HEIGHT_TOTAL =
		WORLD_HEIGHT_MAX - WORLD_HEIGHT_MIN;

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
	 * Used to store and look tags up by name.
	 */
	private Map<String, Tag> tags = new HashMap<>();

	/**
	 * Used to store and look materials up by name.
	 */
	private Map<String, Material> materials = new HashMap<>();

	/**
	 * Create a new material record without any tags or parent material.
	 *
	 * @param name The name of the material.
	 * @return Whether we successfully created a material.
	 */
	public boolean addMaterial(@NonNull String name) {
		return this.addMaterial(name, null, null);
	}

	/**
	 * Create a new material record without any parent material.
	 *
	 * @param name The name of the material.
	 * @param materialTags The names of all the tags that apply to this
	 *            material. If any of these do not already exist, creating the
	 *            material will fail.
	 * @return Whether we successfully created a material.
	 */
	public boolean addMaterial(@NonNull String name,
		@NonNull List<String> materialTags) {
		return this.addMaterial(name, materialTags, null);
	}

	/**
	 * Create a new material record.
	 *
	 * @param name The name of the material.
	 * @param materialTags The names of all the tags that apply to this
	 *            material. If any of these do not already exist, creating the
	 *            material will fail.
	 * @param parentName The name of the parent material, which must already
	 *            exist if not null.
	 * @return Whether we successfully created a material.
	 */
	public boolean addMaterial(@NonNull String name, List<String> materialTags,
		String parentName) {
		if (this.hasMaterial(name)) {
			World.log.warn(SafeResourceLoader.getStringFormatted(
				"MAT_DUPLICATE", FactoryPlugin.getResourceBundle(), name));
			return false;
		}
		if (parentName != null && !this.hasMaterial(parentName)) {
			World.log.warn(
				SafeResourceLoader.getStringFormatted("MAT_MISSING_PARENT",
					FactoryPlugin.getResourceBundle(), name, parentName));
			return false;
		}

		Material parent = null;
		if (parentName != null) {
			parent = this.findMaterial(parentName).orElse(null);
		}

		List<Tag> totalTags = new ArrayList<>();
		if (materialTags != null) {
			for (String tagName : materialTags) {
				Optional<Tag> maybeTag = this.findTag(tagName);
				if (maybeTag.isEmpty()) {
					World.log.warn(
						SafeResourceLoader.getStringFormatted("TAG_MISSING",
							FactoryPlugin.getResourceBundle(), tagName));
					return false;
				}
				totalTags.add(maybeTag.get());
			}
		}

		if (parent != null) {
			Material tempParent = parent;
			while (tempParent != null) {
				totalTags.addAll(tempParent.tags());
				tempParent = tempParent.parent();
			}
		}

		this.deduplicateTags(totalTags);

		this.materials.put(name, new Material(name, totalTags, parent));
		return true;
	}

	/**
	 * Create a new material record without any tags.
	 *
	 * @param name The name of the material.
	 * @param parentName The name of the parent material, which must already
	 *            exist if not null.
	 * @return Whether we successfully created a material.
	 */
	public boolean addMaterial(@NonNull String name,
		@NonNull String parentName) {
		return this.addMaterial(name, null, parentName);
	}

	/**
	 * Add a tag to the list of tags. If the tag already exists, this will fail.
	 *
	 * @param tag The name of the tag.
	 * @return Whether we successfully added the tag.
	 */
	public boolean addTag(@NonNull String tag) {
		return this.addTag(tag, null);
	}

	/**
	 * Add a tag to the list of tags. If the tag already exists, or the parent
	 * does not, this will fail.
	 *
	 * @param tag The name of the tag.
	 * @param parentName The name of the parent tag, or null if there is no
	 *            parent tag.
	 * @return Whether we successfully added the tag.
	 */
	public boolean addTag(@NonNull String tag, String parentName) {
		if (this.hasTag(tag)) {
			World.log.warn(SafeResourceLoader.getStringFormatted(
				"TAG_DUPLICATE", FactoryPlugin.getResourceBundle(), tag));
			return false;
		}
		if (parentName != null && !this.hasTag(parentName)) {
			World.log.warn(
				SafeResourceLoader.getStringFormatted("TAG_MISSING_PARENT",
					FactoryPlugin.getResourceBundle(), tag, parentName));
			return false;
		}

		Tag parent = null;

		if (parentName != null) {
			parent = this.findTag(parentName).orElse(null);
		}

		this.tags.put(tag, new Tag(tag, parent));

		return true;
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

			while (parent != null) {
				if (deduplicated.contains(parent)) {
					toRemove.add(parent);
				}
				parent = parent.parent();
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
	 * @throws NullPointerException If the material name is null.
	 */
	public Optional<Material> findMaterial(@NonNull String materialName) {
		if (this.materials.containsKey(materialName)) {
			return Optional.of(this.materials.get(materialName));
		}
		World.log.error(SafeResourceLoader.getString("MATERIAL_MISSING",
			FactoryPlugin.getResourceBundle()), materialName);
		return Optional.empty();
	}

	/**
	 * Look through the list of tags and search for one with the given name.
	 *
	 * @param tagName The name of the tag to look for.
	 * @return The tag with the given name.
	 * @throws NullPointerException If the tag name is null.
	 */
	public Optional<Tag> findTag(@NonNull String tagName) {
		if (this.tags.containsKey(tagName)) {
			return Optional.of(this.tags.get(tagName));
		}
		World.log.error(SafeResourceLoader.getString("TAG_MISSING",
			FactoryPlugin.getResourceBundle()), tagName);
		return Optional.empty();
	}

	/**
	 * Fetch an unmodifiable copy of the list of material names that currently
	 * exist.
	 *
	 * @return An unmodifiable copy of the material names.
	 */
	public List<String> getMaterialNames() {
		return List.copyOf(this.materials.keySet());
	}

	/**
	 * Fetch an unmodifiable copy of the list of materials that currently exist.
	 *
	 * @return An unmodifiable copy of the material values.
	 */
	public List<Material> getMaterials() {
		return List.copyOf(this.materials.values());
	}

	/**
	 * Fetch an unmodifiable copy of the list of tag names that currently exist.
	 *
	 * @return An unmodifiable copy of the tag names.
	 */
	public List<String> getTagNames() {
		return List.copyOf(this.tags.keySet());
	}

	/**
	 * Fetch an unmodifiable copy of the list of tags that currently exist.
	 *
	 * @return An unmodifiable copy of the tag values.
	 */
	public List<Tag> getTags() {
		return List.copyOf(this.tags.values());
	}

	/**
	 * Checks if the specified material exists.
	 *
	 * @param material The material we are looking for.
	 * @return Whether the material exists.
	 */
	public boolean hasMaterial(@NonNull String material) {
		return this.materials.containsKey(material);
	}

	/**
	 * Checks if the specified tag exists.
	 *
	 * @param tag The tag we are looking for.
	 * @return Whether the tag exists.
	 */
	public boolean hasTag(@NonNull String tag) {
		return this.tags.containsKey(tag);
	}

	/**
	 * Checks if the specified tag exists on the given material.
	 * 
	 * @param tag The name of the tag we are looking for.
	 * @param materialName The material name we are checking against.
	 * @return True if the material has the tag, false if it does not or either
	 *         tag or material don't exist.
	 */
	public boolean hasTagMaterial(@NonNull String tag,
		@NonNull String materialName) {
		if (!this.tags.containsKey(tag)) {
			return false;
		}
		if (!this.materials.containsKey(materialName)) {
			return false;
		}

		return containsTag(tag, this.materials.get(materialName).tags());
	}

	/**
	 * Load all the information from files.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	public boolean loadDefaultConfigurations() {
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
				String parent = (String) contents.get("parent");

				this.addMaterial(entry.getKey(), tagNames, parent);
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
	 * @param parent The name of the parent tag, which is null for root tags.
	 */
	private void processTags(@NonNull Map<String, Object> map, String parent) {
		for (Entry<String, Object> entry : map.entrySet()) {
			final String tagName = entry.getKey();
			this.addTag(tagName, parent);

			Object value = entry.getValue();
			if (value instanceof Map<?, ?> child) {
				@SuppressWarnings("unchecked")
				Map<String, Object> cast = (Map<String, Object>) child;
				this.processTags(cast, tagName);
			}
		}
	}
}
