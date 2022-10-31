package com.ikalagaming.graphics;

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
public class Rect2D {

	/**
	 * Check if 2 rectangles intersect.
	 *
	 * @param a The first rectangle.
	 * @param b The second rectangle.
	 * @return true if they intersect, false if they do not.
	 */
	public static boolean intersects(@NonNull Rect2D a, @NonNull Rect2D b) {
		return (Math.abs((a.x + a.width / 2) - (b.x + b.width / 2))
			* 2 < (a.width + b.width))
			&& (Math.abs((a.y + a.height / 2) - (b.y + b.height / 2))
				* 2 < (a.height + b.height));
	}

	/**
	 * The smallest x coordinate.
	 *
	 * @param x The new x coordinate.
	 * @return The x coordinate.
	 */
	private float x = 0f;

	/**
	 * The smallest y coordinate.
	 *
	 * @param y The new y coordinate.
	 * @return The y coordinate.
	 */
	private float y = 0f;

	/**
	 * The width of the rectangle, along the x axis.
	 *
	 * @param width The new width.
	 * @return The width of the rectangle.
	 */
	private float width = 0f;

	/**
	 * The height of the rectangle, along the y axis.
	 *
	 * @param width The new height.
	 * @return The height of the rectangle.
	 */
	private float height = 0f;

	/**
	 * Check if this rectangle contains a point based on the x and y
	 * coordinates.
	 *
	 * @param x The x coordinate of the point.
	 * @param y The y coordinate of the point.
	 * @return true if the point is inside this rectangle, false otherwise.
	 */
	@SuppressWarnings("hiding")
	public boolean contains(final float x, final float y) {
		return (x >= this.x && x <= (this.x + this.width))
			&& (y >= this.y && y <= (this.y + this.height));
	}

	/**
	 * Check if this rectangle intersects the given rectangle.
	 *
	 * @param other The other rectangle.
	 * @return true if this rectangle and the given one intersect, false if they
	 *         do not.
	 */
	public boolean intersects(@NonNull Rect2D other) {
		return Rect2D.intersects(this, other);
	}

	/**
	 * Set this rectangles values to the given ones.
	 *
	 * @param x The new x value to use.
	 * @param y The new y value to use.
	 * @param width The new width to use.
	 * @param height The new height to use.
	 */
	public void set(final float x, final float y, final float width,
		final float height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	/**
	 * Set this rectangles values to the same as the given point.
	 *
	 * @param other The rectangle to copy values from.
	 */
	public void set(@NonNull Rect2D other) {
		this.x = other.getX();
		this.y = other.getY();
		this.width = other.getWidth();
		this.height = other.getHeight();
	}
}
