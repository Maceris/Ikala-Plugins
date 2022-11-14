package com.ikalagaming.graphics.graph;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A cache of materials so that we can reuse them.
 */
public class MaterialCache {
	/**
	 * The default index to use for materials.
	 */
	public static final int DEFAULT_MATERIAL_INDEX = 0;

	/**
	 * The actual cache of materials.
	 * 
	 * @return The list of materials in the cache.
	 */
	@Getter
	private List<Material> materialsList;

	/**
	 * Set up a new cache with a default material.
	 */
	public MaterialCache() {
		materialsList = new ArrayList<>();
		Material defaultMaterial = new Material();
		materialsList.add(defaultMaterial);
	}

	/**
	 * Add a new material to the cache and give a
	 * 
	 * @param material The material to add to the cache.
	 * @return The index of the newly added material, which is only valid within
	 *         this cache instance.
	 */
	public int addMaterial(Material material) {
		materialsList.add(material);
		final int index = materialsList.size() - 1;
		material.setMaterialIndex(index);
		return index;
	}

	/**
	 * Fetch the material at a given index. If an invalid index is provided, the
	 * default material is returned.
	 * 
	 * @param idx The index to look up.
	 * @return The material at that index.
	 */
	public Material getMaterial(int idx) {
		if (idx < 0 || idx > materialsList.size()) {
			return materialsList.get(DEFAULT_MATERIAL_INDEX);
		}
		return materialsList.get(idx);
	}

}
