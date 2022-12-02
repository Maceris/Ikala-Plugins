package com.ikalagaming.world.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A level has been loaded.
 * 
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public class LevelLoaded {
	/**
	 * The name of the level that was loaded.
	 * 
	 * @param levelName The name of the level that was loaded.
	 * @return The name of the level that was loaded.
	 */
	private final String levelName;
}
