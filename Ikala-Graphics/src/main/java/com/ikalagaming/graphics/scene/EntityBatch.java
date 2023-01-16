package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A batch of entities to render.
 *
 * @author Ches Burks
 *
 */
@Getter
public class EntityBatch {
	/**
	 * The map of models and associated list of entities. While models have
	 * their own list of entities, we might only want to draw some of them in
	 * this batch, so we store our own list.
	 *
	 * @return The list of models and which entities we want to draw for each.
	 */
	private Map<Model, List<Entity>> entites;

	/**
	 * Create a new batch.
	 */
	public EntityBatch() {
		this.entites = new HashMap<>();
	}

	/**
	 * Convenience method for accessing the map.
	 *
	 * @param model The model to fetch.
	 * @return The results of {@link #getEntites()}.{@link Map#get(Object)
	 *         get(model)}.
	 */
	public List<Entity> get(Model model) {
		return this.entites.get(model);
	}

	/**
	 * Return the list of models, a convenience method for accessing the map.
	 *
	 * @return The models in the list of entities.
	 */
	public Set<Model> getModels() {
		return this.entites.keySet();
	}
}
