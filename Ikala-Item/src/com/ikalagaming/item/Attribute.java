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
}
