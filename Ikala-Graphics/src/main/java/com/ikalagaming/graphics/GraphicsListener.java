package com.ikalagaming.graphics;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.world.Level;
import com.ikalagaming.world.WorldManager;
import com.ikalagaming.world.events.LevelLoaded;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.mapeditor.core.CustomClass;
import org.mapeditor.core.CustomClass.Member;
import org.mapeditor.core.Map;
import org.mapeditor.core.Tile;
import org.mapeditor.core.TileLayer;

import java.awt.Color;
import java.util.Optional;

/**
 * Event listeners for the graphics plugin.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsListener implements Listener {

	/**
	 * Load the directional light from properties.
	 * 
	 * @param map The map to load from.
	 */
	private static void loadDirectionalLight(Map map) {
		String dirLightText =
			map.getProperties().getProperty("Directional Light");

		Gson gson = new Gson();
		CustomClass dirLightTemp =
			gson.fromJson(dirLightText, CustomClass.class);

		Vector3f color =
			GraphicsListener.toVector3f(dirLightTemp.getColor("Color"));

		Vector3f direction =
			GraphicsListener.toVector3f(dirLightTemp.getMember("Direction"));

		float intensity = dirLightTemp.getFloat("Intensity");

		DirectionalLight dirLight =
			new DirectionalLight(color, direction, intensity);
		GraphicsManager.getScene().getSceneLights().setDirLight(dirLight);
	}

	/**
	 * Convert a color to a vector3f.
	 *
	 * @param color Optionally, a member containing the color.
	 * @return A default color, or what parts we could decode which deviated
	 *         from the defaults.
	 */
	private static Vector3f toVector3f(Color color) {
		Vector3f result = new Vector3f(1, 1, 1);
		result.x = color.getRed() / 255f;
		result.y = color.getGreen() / 255f;
		result.z = color.getBlue() / 255f;
		return result;
	}

	/**
	 * Convert a Vector3f class to an actual Vector3f.
	 *
	 * @param vector The hypothetical vector.
	 * @return A default vector, or what parts we could decode that deviated
	 *         from the defaults.
	 */
	private static Vector3f toVector3f(Optional<Member> vector) {
		Vector3f result = new Vector3f();
		if (vector.isEmpty()) {
			return result;
		}
		CustomClass vectorDetails = vector.get().getChild();

		Optional<Member> x = vectorDetails.getMember("X");
		Optional<Member> y = vectorDetails.getMember("Y");
		Optional<Member> z = vectorDetails.getMember("Z");

		if (x.isPresent()) {
			result.x = Float.parseFloat(x.get().getValue());
		}
		if (y.isPresent()) {
			result.y = Float.parseFloat(y.get().getValue());
		}
		if (z.isPresent()) {
			result.z = Float.parseFloat(z.get().getValue());
		}
		return result;
	}

	/**
	 * Load models for a layer.
	 *
	 * @param event The event.
	 */
	@EventHandler
	public void onLevelLoaded(LevelLoaded event) {
		if (!GraphicsManager.initialized.get()) {
			GraphicsListener.log
				.warn("Loading level before initializing graphics!");
			return;
		}

		Level level = WorldManager.getInstance().getCurrentLevel().get();

		Map map = level.getMap();

		GraphicsListener.log.debug("Map is {}x{} and has {} layers",
			map.getWidth(), map.getHeight(), map.getLayerCount());

		GraphicsListener.loadDirectionalLight(map);

		for (int z = 0; z < map.getLayerCount(); ++z) {
			if (!(map.getLayer(z) instanceof TileLayer)) {
				continue;
			}
			TileLayer layer = (TileLayer) map.getLayer(z);

			for (int y = 0; y < map.getHeight(); ++y) {
				for (int x = 0; x < map.getWidth(); ++x) {
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
