package com.ikalagaming.world;

import lombok.Getter;
import lombok.Setter;
import org.mapeditor.core.Map;

/**
 * A game level, a map and its contents.
 * 
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class Level {
	/**
	 * The unique name of the level.
	 * 
	 * @param levelName The new name.
	 * @return The level name.
	 */
	private final String levelName;

	/**
	 * The map that the level consists of.
	 * 
	 * @param map The new map.
	 * @return The current map.
	 */
	private Map map;

	/**
	 * Create a new level.
	 * 
	 * @param name The name of the level.
	 */
	public Level(String name) {
		this.levelName = name;
	}

}
