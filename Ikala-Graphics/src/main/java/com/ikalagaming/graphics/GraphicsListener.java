package com.ikalagaming.graphics;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.world.Level;
import com.ikalagaming.world.WorldManager;
import com.ikalagaming.world.events.LevelLoaded;

import lombok.extern.slf4j.Slf4j;
import org.mapeditor.core.Map;
import org.mapeditor.core.Tile;
import org.mapeditor.core.TileLayer;

/**
 * Event listeners for the graphics plugin.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsListener implements Listener {

	/**
	 * Load models for a layer.
	 * 
	 * @param event The event.
	 */
	@EventHandler
	public void onLevelLoaded(LevelLoaded event) {
		if (!GraphicsManager.initialized.get()) {
			log.warn("Loading level before initializing graphics!");
			return;
		}

		Level level = WorldManager.getInstance().getCurrentLevel().get();

		Map map = level.getMap();

		for (int z = 0; z < map.getLayerCount(); ++z) {
			if (!(map.getLayer(z) instanceof TileLayer)) {
				continue;
			}
			TileLayer layer = (TileLayer) map.getLayer(z);

			for (int y = 0; y < map.getWidth(); ++y) {
				for (int x = 0; x < map.getHeight(); ++x) {
					Tile tile = layer.getTileAt(x, y);

					if (tile == null) {
						continue;
					}

					boolean flipHorizontal = layer.isFlippedHorizontally(x, y);
					boolean flipVertical = layer.isFlippedVertically(x, y);
					boolean flipDiagonal = layer.isFlippedDiagonally(x, y);

					float rotation = (float) Math.toRadians(0);
					if (flipVertical && flipDiagonal) {
						rotation = (float) Math.toRadians(90);
					}
					else if (flipHorizontal && flipVertical) {
						rotation = (float) Math.toRadians(180);
					}
					else if (flipHorizontal && flipDiagonal) {
						rotation = (float) Math.toRadians(270);
					}

					int id = tile.getId();
					if (id == 0) {
						Entity newEntity = new Entity(
							"floor_001_" + Entity.NEXT_ID.getAndIncrement(),
							"floor_001");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
					if (id == 1) {
						Entity newEntity = new Entity(
							"brick_wall_" + Entity.NEXT_ID.getAndIncrement(),
							"brick_wall");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
					if (id == 2) {
						Entity newEntity = new Entity(
							"brick_wall_corner_"
								+ Entity.NEXT_ID.getAndIncrement(),
							"brick_wall_corner");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
					if (id == 3) {
						Entity newEntity = new Entity(
							"brick_wall_three_sides_"
								+ Entity.NEXT_ID.getAndIncrement(),
							"brick_wall_three_sides");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
					if (id == 4) {
						Entity newEntity = new Entity(
							"brick_wall_opposite_sides_"
								+ Entity.NEXT_ID.getAndIncrement(),
							"brick_wall_opposite_sides");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
					if (id == 5) {
						Entity newEntity = new Entity(
							"brick_wall_all_sides_"
								+ Entity.NEXT_ID.getAndIncrement(),
							"brick_wall_all_sides");
						newEntity.setScale(1f);
						newEntity.setRotation(0f, 1f, 0f, rotation);
						newEntity.setPosition(x * 2f, z * 2f, y * 2f);
						newEntity.updateModelMatrix();
						GraphicsManager.getScene().addEntity(newEntity);
						continue;
					}
				}
			}
		}
		GraphicsManager.refreshRenderData();
	}
}
