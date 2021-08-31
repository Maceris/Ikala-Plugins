package com.ikalagaming.ecs;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.ParameterizedType;
import java.util.UUID;

/**
 * A component that stores state information.
 *
 * @author Ches Burks
 *
 * @param <T> The actual class of this component, stored as a type parameter.
 */
public abstract class Component<T extends Component<?>>
	implements Comparable<Component<Component<?>>> {

	/**
	 * The unique ID for a component instance.
	 *
	 * @return The unique ID for the component instance.
	 */
	@Getter
	private final UUID uniqueID = UUID.randomUUID();

	/**
	 * Reference counting in the case of shared components. Tracks the number of
	 * entities that currently contain this component.
	 */
	int referenceCount = 0;

	/**
	 * The ID of the Entity that this component belongs to.
	 *
	 * @return The unique ID of the entity that contains this component.
	 * @param parentID the new ID of the entity this component belongs to.
	 */
	@Getter
	@Setter
	private UUID parentID;

	/**
	 * Casts this component to its actual class.
	 *
	 * @return This object as its original class.
	 * @throws ClassCastException If this component is not the type we expect.
	 */
	@SuppressWarnings("unchecked")
	public T asType() {
		return (T) this;
	}

	@Override
	public int compareTo(Component<Component<?>> o) {
		return this.getUniqueID().compareTo(o.getUniqueID());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Component<?>)) {
			return false;
		}
		return this.getUniqueID().equals(((Component<?>) obj).getUniqueID());
	}

	/**
	 * Return the actual component class that this component was when created.
	 *
	 * @return The Component class it should be.
	 */
	public Class<T> getOriginalClass() {
		@SuppressWarnings("unchecked")
		Class<T> persistentClass = (Class<T>) ((ParameterizedType) this
			.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
		return persistentClass;
	}

	@Override
	public int hashCode() {
		return this.getUniqueID().hashCode();
	}
}
