package com.ikalagaming.ecs;

import lombok.NonNull;
import lombok.Synchronized;

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
 */
public class ECSManager {

    /** Maps the unique component class ID to a list of components of that type. */
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
    @Synchronized
    public static <T extends Component<?>> void addComponent(
            @NonNull UUID entityID, @NonNull Component<T> component) {

        List<Component<?>> components =
                componentsMap.computeIfAbsent(
                        component.getOriginalClass(), clazz -> new ArrayList<>());

        if (!components.contains(component)) {
            components.add(component);
        }
        component.referenceCount++;

        Map<Class<? extends Component<?>>, Component<?>> entity =
                entityMap.computeIfAbsent(entityID, id -> new HashMap<>());

        component.getParents().add(entityID);
        entity.put(component.getOriginalClass(), component);
    }

    /**
     * Delete everything, all entities and components tracked by the system. Does not do anything to
     * the actual contents of components, only dereferences the component.
     */
    @Synchronized
    public static void clear() {
        componentsMap.forEach((clazz, list) -> list.clear());
        componentsMap.clear();
        entityMap.forEach((id, map) -> map.clear());
        entityMap.clear();
    }

    /**
     * Checks if an entity contains a specific component.
     *
     * @param <T> The type of the component to fetch.
     * @param entityID The unique entity ID.
     * @param type The class of the component we are looking for.
     * @return True if the entity has a component of the given type, false if the entity is not
     *     found or does not have the component.
     */
    @Synchronized
    public static <T extends Component<?>> boolean containsComponent(
            @NonNull UUID entityID, @NonNull Class<T> type) {
        Map<Class<? extends Component<?>>, Component<?>> components = entityMap.get(entityID);
        if (components == null) {
            return false;
        }
        return components.containsKey(type);
    }

    /**
     * Create a new entity, return the unique ID for that entity.
     *
     * @return The entity's unique ID.
     */
    @Synchronized
    public static UUID createEntity() {
        UUID uniqueID = UUID.randomUUID();
        entityMap.put(uniqueID, new HashMap<>());
        return uniqueID;
    }

    /**
     * Removes a component from the components map, and if it's the last component in it's given
     * list, deletes the list.
     *
     * @param type The type of component to remove.
     * @param component The actual component to delete.
     */
    @Synchronized
    private static void deleteFromComponents(Class<?> type, Component<?> component) {
        List<Component<?>> list = componentsMap.get(type);
        if (list != null) {
            list.remove(component);
            if (list.isEmpty()) {
                componentsMap.remove(type);
            }
        }
    }

    /**
     * Remove an entity and clean up it's components.
     *
     * @param entityID The unique ID of the entity to delete.
     */
    @Synchronized
    public static void destroyEntity(UUID entityID) {
        Map<Class<? extends Component<?>>, Component<?>> entity = entityMap.get(entityID);
        if (entity != null) {
            entity.forEach(
                    (clazz, component) -> {
                        component.referenceCount--;
                        if (component.referenceCount <= 0) {
                            deleteFromComponents(clazz, component);
                        }
                    });
            entity.clear();
        }
        entityMap.remove(entityID);
    }

    /**
     * Return a list of all of the components of a given type.
     *
     * @param <T> The type of component we are looking for.
     * @param type The class of components we are interested in.
     * @return A list of all components with that given type, which may be empty.
     */
    @Synchronized
    public static <T extends Component<?>> List<T> getAllComponents(@NonNull Class<T> type) {
        List<?> output = componentsMap.get(type);
        if (output == null) {
            return new ArrayList<>();
        }
        // we know the type, that's how it's stored.
        @SuppressWarnings("unchecked")
        List<T> cast = (List<T>) output;
        return cast;
    }

    /**
     * Generates a list of all entities that currently exist.
     *
     * @return A list of all unique IDs for current entities.
     */
    @Synchronized
    public static List<UUID> getAllEntities() {
        return new ArrayList<>(entityMap.keySet());
    }

    /**
     * Return a list of all entity ID's that contain all the given types of components.
     *
     * @param types The types of component we are looking for.
     * @return The list of unique ID's for entities that contain the given component. May be empty.
     */
    @SafeVarargs
    @Synchronized
    public static List<UUID> getAllEntitiesWithComponent(
            final @NonNull Class<? extends Component<?>>... types) {
        for (Class<?> type : types) {
            if (type == null) {
                throw new NullPointerException("null parameter passed in");
            }
        }
        if (types.length == 0) {
            return new ArrayList<>();
        }

        Class<?> firstType = types[0];
        List<? extends Component<?>> components = componentsMap.get(firstType);
        if (components == null) {
            // we don't have anything with that component
            return new ArrayList<>();
        }

        List<UUID> values = new ArrayList<>();
        components.stream().forEach(component -> values.addAll(component.getParents()));

        if (types.length == 1) {
            return values;
        }
        boolean skipOne = true;
        for (Class<? extends Component<?>> type : types) {
            if (skipOne) {
                // we already looked at the first list, skip one item
                skipOne = false;
                continue;
            }
            List<? extends Component<?>> tempComponents = componentsMap.get(type);

            if (tempComponents == null) {
                // we don't have anything with that component
                return new ArrayList<>();
            }
            List<UUID> tempIDs = new ArrayList<>();
            tempComponents.stream().forEach(component -> tempIDs.addAll(component.getParents()));
            values.removeIf(uuid -> !tempIDs.contains(uuid));
        }
        return values;
    }

    /**
     * Fetches a component from an entity.
     *
     * @param <T> The type of the component to fetch.
     * @param entityID The unique entity ID.
     * @param type The class of the component we are looking for.
     * @return The component of that class, or an empty optional if the entity entity does not exist
     *     or does not have that component.
     */
    @Synchronized
    public static <T extends Component<?>> Optional<T> getComponent(
            @NonNull UUID entityID, @NonNull Class<T> type) {
        Map<Class<? extends Component<?>>, Component<?>> components = entityMap.get(entityID);
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
    @Synchronized
    public static <T extends Component<?>> void removeComponent(
            @NonNull UUID entityID, @NonNull Class<T> type) {
        Map<Class<? extends Component<?>>, Component<?>> components = entityMap.get(entityID);
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
        component.getParents().remove(entityID);
        if (component.referenceCount <= 0) {
            deleteFromComponents(type, component);
        }
    }

    /** Private constructor so this class is not instantiated. */
    private ECSManager() {}
}
