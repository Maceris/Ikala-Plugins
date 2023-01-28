package com.ikalagaming.rpg;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.plugins.events.AllPluginsEnabled;
import com.ikalagaming.world.Level;
import com.ikalagaming.world.WorldManager;
import com.ikalagaming.world.events.LevelLoaded;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.mapeditor.core.CustomClass;
import org.mapeditor.core.CustomClass.Member;
import org.mapeditor.core.Map;
import org.mapeditor.core.MapLayer;
import org.mapeditor.core.MapObject;
import org.mapeditor.core.ObjectGroup;
import org.mapeditor.core.Properties;
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
public class GeneralListener implements Listener {

	private static Gson gson = new Gson();

	/**
	 * Get the rotation in radians for a tile at the specified coordinates.
	 *
	 * @param layer The tile layer we are looking at.
	 * @param x The x coordinate on the given layer.
	 * @param y The y coordinate on the given layer.
	 * @return The rotation of that tile in radians.
	 */
	private static float getRotation(@NonNull TileLayer layer, int x, int y) {
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
		return rotation;
	}

	/**
	 * Load the directional light from properties.
	 *
	 * @param map The map to load from.
	 */
	private static void loadDirectionalLight(Map map) {
		String dirLightText =
			map.getProperties().getProperty("Directional Light");

		CustomClass dirLightTemp =
			GeneralListener.gson.fromJson(dirLightText, CustomClass.class);

		Vector3f color =
			GeneralListener.toVector3f(dirLightTemp.getColor("Color"));

		Vector3f direction =
			GeneralListener.toVector3f(dirLightTemp.getMember("Direction"));

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

	private boolean modelsLoaded = false;

	/**
	 * Create an entity corresponding to a tile.
	 *
	 * @param id The ID of the tile.
	 * @param x The x position on the map.
	 * @param y The y position on the map.
	 * @param z The z position on the map.
	 * @param rotation The rotation of the model.
	 */
	private void addTile(int id, int x, int y, int z, float rotation) {
		if (id == 0) {
			Entity tile = new Entity(
				"floor_001_" + Entity.NEXT_ID.getAndIncrement(), "floor_001");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}
		else if (id == 1) {
			Entity tile = new Entity(
				"brick_wall_" + Entity.NEXT_ID.getAndIncrement(), "brick_wall");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}
		else if (id == 2) {
			Entity tile = new Entity(
				"brick_wall_corner_" + Entity.NEXT_ID.getAndIncrement(),
				"brick_wall_corner");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}
		else if (id == 3) {
			Entity tile = new Entity(
				"brick_wall_three_sides_" + Entity.NEXT_ID.getAndIncrement(),
				"brick_wall_three_sides");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}
		else if (id == 4) {
			Entity tile = new Entity(
				"brick_wall_opposite_sides_" + Entity.NEXT_ID.getAndIncrement(),
				"brick_wall_opposite_sides");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}
		else if (id == 5) {
			Entity tile = new Entity(
				"brick_wall_all_sides_" + Entity.NEXT_ID.getAndIncrement(),
				"brick_wall_all_sides");
			this.updateDungeonTileEntity(tile, x, y, z, rotation);
		}

	}

	private void createLight(MapObject object) {
		Properties props = object.getProperties();

		String colorString = props.getProperty("Color");
		Color color;
		if (colorString == null) {
			color = new Color(0, 0, 0);
		}
		else {
			color = Member.stringToColor(colorString);
		}
		float intensity = 1f;

		String intensityString = props.getProperty("Intensity");
		if (intensityString != null) {
			intensity = Float.parseFloat(intensityString);
		}

		Vector3f position = this.extractPosition(props);

		PointLight light = new PointLight(GeneralListener.toVector3f(color),
			position, intensity);
		GeneralListener.log.debug(
			"Created a light at ({}, {}, {}), with intensity {}", position.x,
			position.y, position.z, intensity);
		GraphicsManager.getScene().getSceneLights().getPointLights().add(light);
	}

	/**
	 * Extract the position from the properties of an object on the map.
	 *
	 * @param props The properties of the item.
	 * @return The position in world coordinates.
	 */
	private Vector3f extractPosition(Properties props) {
		String posString = props.getProperty("Position");

		Optional<Member> posMember;
		if (posString == null) {
			posMember = Optional.empty();
		}
		else {
			CustomClass pos =
				GeneralListener.gson.fromJson(posString, CustomClass.class);
			Member fakeParent = new Member(pos.getName(), "class", "Position");
			fakeParent.setChild(pos);
			posMember = Optional.of(fakeParent);
		}
		Vector3f position = GeneralListener.toVector3f(posMember);
		position.x *= 2f;
		position.y *= 2f;
		position.z *= 2f;

		// Swap z and y coordinates because opengl is weird
		float temp = position.z;
		position.z = position.y;
		position.y = temp;
		return position;
	}

	/**
	 * Load all the appropriate models to the scene.
	 */
	private void loadModels() {
		if (this.modelsLoaded) {
			return;
		}

		Model model =
			ModelLoader.loadModel(new ModelLoader.ModelLoadRequest("floor_001",
				RPGPlugin.PLUGIN_NAME, "models/dungeon/floor_001.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model =
			ModelLoader.loadModel(new ModelLoader.ModelLoadRequest("brick_wall",
				RPGPlugin.PLUGIN_NAME, "models/dungeon/brick_wall.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model = ModelLoader
			.loadModel(new ModelLoader.ModelLoadRequest("brick_wall_corner",
				RPGPlugin.PLUGIN_NAME, "models/dungeon/brick_wall_corner.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model = ModelLoader.loadModel(new ModelLoader.ModelLoadRequest(
			"brick_wall_all_sides", RPGPlugin.PLUGIN_NAME,
			"models/dungeon/brick_wall_all_sides.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model = ModelLoader.loadModel(new ModelLoader.ModelLoadRequest(
			"brick_wall_opposite_sides", RPGPlugin.PLUGIN_NAME,
			"models/dungeon/brick_wall_opposite_sides.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model = ModelLoader.loadModel(new ModelLoader.ModelLoadRequest(
			"brick_wall_three_sides", RPGPlugin.PLUGIN_NAME,
			"models/dungeon/brick_wall_three_sides.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), false));
		GraphicsManager.getScene().addModel(model);

		model = ModelLoader.loadModel(new ModelLoader.ModelLoadRequest("player",
			RPGPlugin.PLUGIN_NAME, "models/characters/human_male.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), true));
		GraphicsManager.getScene().addModel(model);

		this.modelsLoaded = true;

		GraphicsManager.refreshRenderData();
	}

	/**
	 * Load a tile layer for the map.
	 *
	 * @param map The map we are loading.
	 * @param layer The tile layer we are interested in.
	 * @param z The z coordinate of the layer.
	 */
	private void loadTileLayer(Map map, TileLayer layer, int z) {
		for (int y = 0; y < map.getHeight(); ++y) {
			for (int x = 0; x < map.getWidth(); ++x) {
				Tile tile = layer.getTileAt(x, y);

				if (tile == null) {
					continue;
				}
				float rotation = GeneralListener.getRotation(layer, x, y);

				int id = tile.getId();
				this.addTile(id, x, y, z, rotation);
			}
		}
	}

	/**
	 * Kick off things once the game has actually finished booting up.
	 *
	 * @param event The event indicating all plugins are enabled.
	 */
	@EventHandler
	public void onGameStarting(AllPluginsEnabled event) {
		WorldManager.getInstance().loadLevel("default");
	}

	/**
	 * Load models for a layer.
	 *
	 * @param event The event.
	 */
	@EventHandler
	public void onLevelLoaded(LevelLoaded event) {
		if (!GraphicsManager.isInitialized()) {
			GeneralListener.log
				.warn("Loading level before initializing graphics!");
			return;
		}
		this.loadModels();

		Optional<Level> maybeLevel =
			WorldManager.getInstance().getCurrentLevel();

		if (maybeLevel.isEmpty()) {
			GeneralListener.log.warn("Loading invalid level!");
			return;
		}
		Level level = WorldManager.getInstance().getCurrentLevel().get();

		Map map = level.getMap();

		GeneralListener.log.debug("Map is {}x{} and has {} layers",
			map.getWidth(), map.getHeight(), map.getLayerCount());

		GeneralListener.loadDirectionalLight(map);

		for (int z = 0; z < map.getLayerCount(); ++z) {
			MapLayer genericLayer = map.getLayer(z);

			if (genericLayer instanceof ObjectGroup) {
				GeneralListener.log.debug("We have an object group.");
				ObjectGroup group = (ObjectGroup) genericLayer;

				for (MapObject object : group.getObjects()) {
					if ("Point Light".equals(object.getClassValue())) {
						this.createLight(object);
					}
					else if ("Spawn".equals(object.getName())) {
						Vector3f position =
							this.extractPosition(object.getProperties());
						Entity player = new Entity(
							"player_" + Entity.NEXT_ID.getAndIncrement(),
							"player");
						player.setScale(0.6f);
						player.setRotation(0f, 1f, 0f, 0f);
						player.setPosition(position.x, position.y, position.z);
						player.updateModelMatrix();
						GraphicsManager.getScene().addEntity(player);

						GeneralListener.log.debug(
							"Created a player at ({}, {}, {})", position.x,
							position.y, position.z);
					}
				}
				continue;
			}
			if (genericLayer instanceof TileLayer) {
				this.loadTileLayer(map, (TileLayer) genericLayer, z);
				continue;
			}
		}
		GraphicsManager.refreshRenderData();
	}

	/**
	 * Update the model matrix of the tile, then add it to the scene.
	 *
	 * @param tile The tile entity we are adding.
	 * @param x The x position on the map.
	 * @param y The y position on the map.
	 * @param z The z position on the map.
	 * @param rotation The rotation of the model.
	 */
	private void updateDungeonTileEntity(Entity tile, int x, int y, int z,
		float rotation) {
		tile.setScale(1f);
		tile.setRotation(0f, 1f, 0f, rotation);
		tile.setPosition(x * 2f, z * 2f, y * 2f);
		tile.updateModelMatrix();
		GraphicsManager.getScene().addEntity(tile);
	}

}
