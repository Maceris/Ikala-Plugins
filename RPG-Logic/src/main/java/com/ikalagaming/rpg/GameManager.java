package com.ikalagaming.rpg;

import com.ikalagaming.ecs.World;
import com.ikalagaming.rpg.inventory.Inventory;

import lombok.Getter;

/**
 * Manages general game things, like the player.
 *
 * @author Ches Burks
 */
public class GameManager {

    /**
     * The entities.
     *
     * @return The world.
     */
    @Getter private static final World world = new World();

    /**
     * The player's entity.
     *
     * @return The entity, or null before the player is made.
     */
    @Getter private static Long player;

    /** Create a player. */
    public static void createPlayer() {
        GameManager.player = world.create();
        Inventory inventory = new Inventory(100, true);
        world.set(Inventory.TYPE, GameManager.player, inventory);
    }

    /** Private constructor so this is not instantiated. */
    private GameManager() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
