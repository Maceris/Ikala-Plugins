package com.ikalagaming.graphics.scene.debug;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A line, drawn between two points.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Line {
	/**
	 * The color of the line.
	 *
	 * @param color The new color.
	 * @return The color.
	 */
	@NonNull
	private Vector3f color;

	/**
	 * The first point on the line.
	 *
	 * @param position The first position.
	 * @return The first position.
	 */
	@NonNull
	private Vector3f position1;

	/**
	 * The second point on the line.
	 *
	 * @param position The second position.
	 * @return The second position.
	 */
	@NonNull
	private Vector3f position2;

	/**
	 * Text to show above the line, which will be the same color as the line.
	 * May be null if not applicable.
	 *
	 * @param The new text to show above the line.
	 * @return The text to show above the line.
	 */
	private String text;
}
