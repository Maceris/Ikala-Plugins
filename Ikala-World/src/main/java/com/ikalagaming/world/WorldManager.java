package com.ikalagaming.world;

import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.world.events.LevelLoaded;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.mapeditor.core.Map;
import org.mapeditor.io.TMXMapReader;

import java.io.File;
import java.util.Optional;

/**
 * 
 * Handles the world.
 * 
 * @author Ches Burks
 *
 */
@Slf4j
public class WorldManager {

	/**
	 * Our singleton instance so we have better control over the life-cycle.
	 */
	private static WorldManager instance;

	/**
	 * Destroy the current instance.
	 */
	public static void destroyInstance() {
		WorldManager.instance = null;
	}

	/**
	 * Get the current world manager instance.
	 *
	 * @return The world manager instance.
	 */
	public static WorldManager getInstance() {
		if (WorldManager.instance == null) {
			WorldManager.instance = new WorldManager();
		}
		return WorldManager.instance;
	}

	private Level currentLevel;

	/**
	 * Load a level, unloading the current one if there is one already loaded.
	 * 
	 * @param levelName The name of the level to load.
	 * @return Whether we successfully loaded the level.
	 */
	public boolean loadLevel(@NonNull String levelName) {
		File mapFile = PluginFolder.getResource(WorldPlugin.PLUGIN_NAME,
			ResourceType.DATA, levelName + ".tmx");
		if (!mapFile.exists()) {
			return false;
		}
		if (isLevelLoaded()) {
			unloadLevel();
		}
		
		try {
			TMXMapReader reader = new TMXMapReader();
			Map map = reader.readMap(mapFile.getAbsolutePath());
			this.currentLevel = new Level(levelName);
			this.currentLevel.setMap(map);
		}
		catch (Exception e) {
			// TODO Localize
			log.warn("Failed to load map", e);
		}
		new LevelLoaded(levelName).fire();
		return true;
	}

	/**
	 * Unload the current level.
	 */
	public void unloadLevel() {
		this.currentLevel = null;
	}

	/**
	 * Whether there is currently a level loaded.
	 * 
	 * @return Whether there is a level loaded.
	 */
	public boolean isLevelLoaded() {
		return currentLevel != null;
	}

	/**
	 * Return the current level, which will be an empty optional if no level is
	 * loaded.
	 * 
	 * @return The current level, or an empty optional if there is none.
	 */
	public Optional<Level> getCurrentLevel() {
		return Optional.ofNullable(currentLevel);
	}

	/**
	 * We want to construct this ourselves.
	 */
	private WorldManager() {}
}
