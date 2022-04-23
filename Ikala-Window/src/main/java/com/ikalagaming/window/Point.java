package com.ikalagaming.window;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A 2d point.
 *
 * @author Ches Burks
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Point {

	/**
	 * The x coordinate.
	 *
	 * @param x The new x coordinate.
	 * @return The x coordinate.
	 */
	private float x = 0f;

	/**
	 * The y coordinate.
	 *
	 * @param y The new y coordinate.
	 * @return The y coordinate.
	 */
	private float y = 0f;

	/**
	 * Set this points values to the given ones.
	 *
	 * @param x The new x value to use.
	 * @param y The new y value to use.
	 */
	public void set(final float x, final float y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Set this points values to the same as the given point.
	 *
	 * @param other The point to copy values from.
	 */
	public void set(@NonNull Point other) {
		this.x = other.getX();
		this.y = other.getY();
	}
}
