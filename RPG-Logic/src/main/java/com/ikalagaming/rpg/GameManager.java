package com.ikalagaming.rpg;

import com.ikalagaming.ecs.ECSManager;
import com.ikalagaming.inventory.Inventory;

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
		player = ECSManager.createEntity();
		Inventory inventory = new Inventory(100);
		ECSManager.addComponent(player, inventory);
	}
}
