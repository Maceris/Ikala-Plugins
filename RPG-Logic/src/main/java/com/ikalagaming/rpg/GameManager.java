package com.ikalagaming.rpg;

import com.ikalagaming.ecs.ECSManager;
import com.ikalagaming.rpg.inventory.Inventory;

import lombok.Getter;

import java.util.UUID;

/**
 * Manages general game things, like the player.
 *
 * @author Ches Burks
 *
 */
public class GameManager {

	@Getter
	private static UUID player;

	/**
	 * Create a player.
	 */
	public static void createPlayer() {
		GameManager.player = ECSManager.createEntity();
		Inventory inventory = new Inventory(100, true);
		ECSManager.addComponent(GameManager.player, inventory);
	}

	/**
	 * Private constructor so this is not instantiated.
	 */
	private GameManager() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
