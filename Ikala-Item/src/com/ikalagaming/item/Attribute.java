package com.ikalagaming.item;

/**
 * A named attribute with an associated value.
 *
 * @author Ches Burks
 *
 * @param <T> The type of the value.
 */
public interface Attribute<T> {
	/**
	 * The fully qualified class name of the component that the attribute was
	 * derived from.
	 *
	 * @return The fully qualified class name of the component.
	 */
	String getComponentType();

	/**
	 * Return the name of the attribute.
	 *
	 * @return The attributes name.
	 */
	String getName();

	/**
	 * Get the value of the attribute.
	 *
	 * @return The attributes value.
	 */
	T getValue();

	/**
	 * The fully qualified class name of the component that the attribute was
	 * derived from.
	 *
	 * @param componentType The fully qualified class name of the component.
	 */
	void setComponentType(String componentType);

	/**
	 * Sets the name of the attribute.
	 *
	 * @param name The new name.
	 */
	void setName(String name);

	/**
	 * Sets the value of the attribute.
	 *
	 * @param value The new value.
	 */
	void setValue(T value);

}
