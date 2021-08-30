package com.ikalagaming.ecs;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles tracking and management of Entities, Components, and Systems.
 *
 * @author Ches Burks
 *
 */
public class ECSManager {

	/**
	 * Maps the unique component class ID to a list of components of that type.
	 */
	private static Map<Class<? extends Component<?>>, List<Component<?>>> componentsMap =
		new HashMap<>();

	private static Map<UUID, Map<Class<? extends Component<?>>, Component<?>>> entityMap =
		new HashMap<>();

	/**
	 * Add a component to an entity.
	 *
	 * @param <T> The type of component we are adding
	 * @param entityID The unique ID of the entity we are adding a component to.
	 * @param component The component to add.
	 */
	public static <T extends Component<?>> void
		addComponent(@NonNull UUID entityID, @NonNull Component<T> component) {

		List<Component<?>> components =
			ECSManager.componentsMap.computeIfAbsent(component.getActualClass(),
				clazz -> new ArrayList<>());

		if (!components.contains(component)) {
			components.add(component);
		}
		component.referenceCount++;

		ECSManager.componentsMap.get(component.getActualClass()).add(component);

		Map<Class<? extends Component<?>>, Component<?>> entity =
			ECSManager.entityMap.computeIfAbsent(entityID,
				id -> new HashMap<>());

		component.setParentID(entityID);
		entity.put(component.getActualClass(), component);
	}

	/**
	 * Create a new entity, return the unique ID for that entity.
	 *
	 * @return The entity's unique ID.
	 */
	public static UUID createEntity() {
		UUID uniqueID = UUID.randomUUID();
		ECSManager.entityMap.put(uniqueID, new HashMap<>());
		return uniqueID;
	}

	/**
	 * Removes a component from the components map, and if it's the last
	 * component in it's given list, deletes the list.
	 *
	 * @param type The type of component to remove.
	 * @param component The actual component to delete.
	 */
	private static void deleteFromComponents(Class<?> type,
		Component<?> component) {
		List<Component<?>> list = ECSManager.componentsMap.get(type);
		if (list != null) {
			list.remove(component);
			if (list.isEmpty()) {
				ECSManager.componentsMap.remove(type);
			}
		}
	}

	/**
	 * Remove an entity and clean up it's components.
	 *
	 * @param entityID The unique ID of the entity to delete.
	 */
	public static void destroyEntity(UUID entityID) {
		Map<Class<? extends Component<?>>, Component<?>> entity =
			ECSManager.entityMap.get(entityID);
		if (entity != null) {
			entity.forEach((clazz, component) -> {
				component.referenceCount--;
				if (component.referenceCount <= 0) {
					ECSManager.deleteFromComponents(clazz, component);
				}
			});
			entity.clear();
		}
		ECSManager.entityMap.remove(entityID);

	}

	/**
	 * Fetches a component from an entity.
	 *
	 * @param <T> The type of the component to fetch.
	 * @param entityID The unique entity ID.
	 * @param type The class of the component we are looking for.
	 * @return The component of that class, or an empty optional if the entity
	 *         entity does not exist or does not have that component.
	 */
	public static <T extends Component<?>> Optional<T>
		getComponent(@NonNull UUID entityID, @NonNull Class<T> type) {
		Map<Class<? extends Component<?>>, Component<?>> components =
			ECSManager.entityMap.get(entityID);
		if (components == null) {
			return Optional.empty();
		}
		// we know the type, it's stored by that type
		@SuppressWarnings("unchecked")
		Component<T> component = (Component<T>) components.get(type);
		if (component == null) {
			return Optional.empty();
		}

		return Optional.of(component.asType());
	}

	/**
	 * Removes a component from an entity.
	 *
	 * @param <T> The type of the component to fetch.
	 * @param entityID The unique entity ID.
	 * @param type The class of the component we are looking for.
	 */
	public static <T extends Component<?>> void
		removeComponent(@NonNull UUID entityID, @NonNull Class<T> type) {
		Map<Class<? extends Component<?>>, Component<?>> components =
			ECSManager.entityMap.get(entityID);
		if (components == null) {
			return;
		}
		// we know the type, it's stored by that type
		@SuppressWarnings("unchecked")
		Component<T> component = (Component<T>) components.get(type);
		if (component == null) {
			return;
		}
		components.remove(type);
		component.referenceCount--;
		if (component.referenceCount <= 0) {
			ECSManager.deleteFromComponents(type, component);
		}
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private ECSManager() {}

}
