package com.ikalagaming.language;

import com.ikalagaming.language.grammar.Material;
import com.ikalagaming.language.grammar.Noun;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Handles loading and tracking tags.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class TagManager {
	/**
	 * A list of tags that apply to nouns.
	 *
	 * @return The tags that apply to nouns.
	 */
	@Getter
	private static List<Tag> tags = new ArrayList<>();

	/**
	 * A list of all materials.
	 *
	 * @return The materials that exist.
	 */
	@Getter
	private static List<Material> materials = new ArrayList<>();

	/**
	 * A list of all nouns.
	 *
	 * @return The nouns that exist.
	 */
	@Getter
	private static List<Noun> nouns = new ArrayList<>();

	/**
	 * Remove any duplicate values in the list of tags. If there are both a
	 * generic tag and a more specific one that inherits from it, the generic
	 * tag will be removed.
	 *
	 * @param list The tags to de-duplicate. It is expected that the contents of
	 *            this list will be modified.
	 */
	private static void deduplicateTags(@NonNull List<Tag> list) {
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
	private static Material findMaterial(@NonNull String materialName) {
		for (Material material : TagManager.materials) {
			if (materialName.equals(material.name())) {
				return material;
			}
		}
		TagManager.log.warn(SafeResourceLoader.getString("MATERIAL_MISSING",
			LanguagePlugin.getResourceBundle()), materialName);
		throw new NullPointerException();
	}

	/**
	 * Look through the list of tags and search for one with the given name.
	 *
	 * @param tagName The name of the tag to look for.
	 * @return The tag with the given name.
	 * @throws NullPointerException If the tag is not found, or is null.
	 */
	private static Tag findTag(@NonNull String tagName) {
		for (Tag noun : TagManager.tags) {
			if (tagName.equals(noun.value())) {
				return noun;
			}
		}
		TagManager.log.warn(SafeResourceLoader.getString("TAG_MISSING",
			LanguagePlugin.getResourceBundle()), tagName);
		throw new NullPointerException();
	}

	/**
	 * Checks if the tag is in the provided list. If any tag matches the
	 * expected tag, or inherits from a tag that does, this will return true.
	 *
	 * @param tag The tag we are looking for.
	 * @param list The list of tags that might contain the desired tag.
	 * @return Whether the tag can be found in the list.
	 */
	public static boolean hasTag(@NonNull String tag, List<Tag> list) {
		for (Tag potential : list) {
			if (tag.equals(potential.value())) {
				return true;
			}
			// Check all parent tags too
			Tag parent = potential.parent();
			while (potential.parent() != null) {
				if (tag.equals(parent.value())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Load all the information from files.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	public static boolean load() {
		// Short-circuiting will stop execution if one fails
		if (!TagManager.loadTags() || !TagManager.loadMaterials()
			|| !TagManager.loadNouns()) {
			return false;
		}
		return true;
	}

	/**
	 * Load and process materials from disk.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	private static boolean loadMaterials() {
		PluginConfig config =
			ConfigManager.loadConfig(LanguagePlugin.PLUGIN_NAME);

		Map<String, Object> materialMap =
			TagManager.loadYaml(config.getString("materials"));
		if (materialMap.isEmpty()) {
			return false;
		}
		TagManager.processMaterials(materialMap, null);

		TagManager.log.debug(SafeResourceLoader.getString("LOADED_MATERIALS",
			LanguagePlugin.getResourceBundle()));
		return true;
	}

	/**
	 * Load and process nouns from disk.
	 *
	 * @return Whether we successfully loaded all information.
	 */
	private static boolean loadNouns() {
		PluginConfig config =
			ConfigManager.loadConfig(LanguagePlugin.PLUGIN_NAME);

		Map<String, Object> nounMap =
			TagManager.loadYaml(config.getString("nouns"));
		if (nounMap.isEmpty()) {
			return false;
		}

		TagManager.processNouns(nounMap);

		TagManager.log.debug(SafeResourceLoader.getString("LOADED_NOUNS",
			LanguagePlugin.getResourceBundle()));
		return true;
	}

	/**
	 * Load the tags from files.
	 *
	 * @return Whether we were successful.
	 */
	private static boolean loadTags() {
		PluginConfig config =
			ConfigManager.loadConfig(LanguagePlugin.PLUGIN_NAME);

		Map<String, Object> tagMap =
			TagManager.loadYaml(config.getString("tags"));
		if (tagMap.isEmpty()) {
			return false;
		}
		TagManager.processTags(tagMap, null);
		TagManager.log.debug(SafeResourceLoader.getString("LOADED_TAGS",
			LanguagePlugin.getResourceBundle()));
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
	private static Map<String, Object> loadYaml(String resourceName) {
		Yaml yaml = new Yaml();
		File file = PluginFolder.getResource(LanguagePlugin.PLUGIN_NAME,
			ResourceType.DATA, resourceName);

		try {
			InputStream nounStream = new FileInputStream(file);
			@SuppressWarnings("unchecked")
			Map<String, Object> map =
				(Map<String, Object>) yaml.load(nounStream);
			if (map == null) {
				TagManager.log.warn(
					SafeResourceLoader.getString("FILE_EMPTY",
						LanguagePlugin.getResourceBundle()),
					file.getAbsolutePath());
				return new HashMap<>();
			}
			return map;
		}
		catch (FileNotFoundException e) {
			TagManager.log.warn(
				SafeResourceLoader.getString("FILE_NOT_FOUND",
					LanguagePlugin.getResourceBundle()),
				file.getAbsolutePath(), e);
		}
		return new HashMap<>();
	}

	/**
	 * Process a map from snakeyaml and populate the material list with the
	 * results.
	 *
	 * @param map The nested map structure output by snakeyaml.
	 * @param parent The parent material, which is null for root materials.
	 */
	private static void processMaterials(@NonNull Map<String, Object> map,
		Material parent) {

		try {
			for (Entry<String, Object> entry : map.entrySet()) {
				Object value = entry.getValue();
				if (!(value instanceof Map<?, ?>)) {
					TagManager.log.warn(
						SafeResourceLoader.getString("MAT_INVALID_STRUCTURE",
							LanguagePlugin.getResourceBundle()));
					return;
				}

				@SuppressWarnings("unchecked")
				Map<String, Object> contents = (Map<String, Object>) value;

				@SuppressWarnings("unchecked")
				List<String> tagNames = (List<String>) contents.get("tags");

				List<Tag> tagValues = tagNames.stream().map(TagManager::findTag)
					.collect(Collectors.toCollection(ArrayList::new));

				if (parent != null) {
					// Inherit tags
					tagValues.addAll(parent.tags());
				}
				TagManager.deduplicateTags(tagValues);

				Material newMaterial =
					new Material(entry.getKey(), tagValues, parent);
				TagManager.materials.add(newMaterial);

				@SuppressWarnings("unchecked")
				Map<String, Object> children =
					(Map<String, Object>) contents.get("children");
				if (children != null) {
					TagManager.processMaterials(children, newMaterial);
				}
			}
		}
		catch (Exception e) {
			TagManager.log.warn(SafeResourceLoader.getString(
				"MAT_INVALID_STRUCTURE", LanguagePlugin.getResourceBundle()));
		}
	}

	/**
	 * Process a map from snakeyaml and populate the noun list with the results.
	 *
	 * @param map The nested map structure output by snakeyaml.
	 */
	private static void processNouns(@NonNull Map<String, Object> map) {
		try {
			for (Entry<String, Object> entry : map.entrySet()) {

				@SuppressWarnings("unchecked")
				Map<String, Object> contents =
					(Map<String, Object>) entry.getValue();

				@SuppressWarnings("unchecked")
				List<String> tagNames = (List<String>) contents.get("tags");

				List<Tag> tagValues;
				if (tagNames != null) {
					tagValues = tagNames.stream().map(TagManager::findTag)
						.collect(Collectors.toCollection(ArrayList::new));
				}
				else {
					tagValues = new ArrayList<>();
				}

				String materialName = (String) contents.get("material");

				Material material = null;
				if (materialName != null) {
					material = TagManager.findMaterial(materialName);
					List<Tag> materialTags = material.tags();
					tagValues.addAll(materialTags);
				}

				TagManager.deduplicateTags(tagValues);

				Noun noun = new Noun(entry.getKey(), material, tagValues);

				TagManager.nouns.add(noun);
			}
		}
		catch (Exception e) {
			TagManager.log.warn(SafeResourceLoader.getString(
				"NOUN_INVALID_STRUCTURE", LanguagePlugin.getResourceBundle()));
		}
	}

	/**
	 * Process a map from snakeyaml and populate the tag list with the results.
	 *
	 * @param map The nested map structure output by snakeyaml.
	 * @param parent The parent tag, which is null for root tags.
	 */
	private static void processTags(@NonNull Map<String, Object> map,
		Tag parent) {
		for (Entry<String, Object> entry : map.entrySet()) {
			Tag newTag = new Tag(entry.getKey(), parent);
			TagManager.tags.add(newTag);

			Object value = entry.getValue();
			if (value instanceof Map<?, ?> child) {
				@SuppressWarnings("unchecked")
				Map<String, Object> cast = (Map<String, Object>) child;
				TagManager.processTags(cast, newTag);
			}
		}
	}

	/**
	 * Private constructor so that this class is not instantiated.
	 */
	private TagManager() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
