package com.ikalagaming.window;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Contains information needed to draw a window.
 *
 * @author Ches Burks
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowStyle {

	/**
	 * Defines the different borders a window can have.
	 * <ul>
	 * <li><b>LINE</b> - A simple line</li>
	 * <li><b>BORDERLESS</b> - No border, just the background</li>
	 * </ul>
	 */
	public enum Border {
		/**
		 * Just a plain line.
		 */
		LINE,
		/**
		 * No border at all, just the background
		 */
		BORDERLESS;
	}

	/**
	 * The colors of a window and its border. Borders are a darker shade of the
	 * color.
	 * <ul>
	 * <li><b>LIGHT</b> (white)</li>
	 * <li><b>RED</b></li>
	 * <li><b>BLUE</b></li>
	 * <li><b>GREEN</b></li>
	 * <li><b>DARK</b> (dark grey)</li>
	 * <li><b>YELLOW</b></li>
	 * </ul>
	 *
	 */
	public enum ColorScheme {
		/**
		 * White with grey borders
		 */
		LIGHT,
		/**
		 * Red with darker red borders
		 */
		RED,
		/**
		 * Blue with darker blue borders
		 */
		BLUE,
		/**
		 * Green with darker green borders
		 */
		GREEN,
		/**
		 * Grey with dark grey borders
		 */
		DARK,
		/**
		 * Yellow with dark yellow borders
		 */
		YELLOW;
	}

	/**
	 * Defines the different styles of the box.
	 * <ul>
	 * <li><b>SQUARE</b> - A regular box</li>
	 * <li><b>ROUNDED</b> - A rounded rectangle</li>
	 * </ul>
	 */
	public enum WindowShape {
		/**
		 * A square box
		 */
		SQUARE,
		/**
		 * A rectangle but with rounded corners
		 */
		ROUNDED
	}

	/**
	 * The {@link WindowStyle style} of the window.
	 *
	 * @param style The style to use.
	 * @return The current style.
	 */
	@NonNull
	private WindowShape style = WindowShape.SQUARE;
	/**
	 * The {@link Border border} of the window.
	 *
	 * @param border The border to use,
	 * @return The border style.
	 */
	@NonNull
	private Border border = Border.LINE;
	/**
	 * The {@link ColorScheme color scheme} of the window.
	 *
	 * @param colorScheme The color scheme to use.
	 * @return The current color scheme.
	 */
	@NonNull
	private ColorScheme colorScheme = ColorScheme.LIGHT;
}
