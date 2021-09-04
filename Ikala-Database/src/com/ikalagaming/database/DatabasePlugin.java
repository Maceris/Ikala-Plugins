package com.ikalagaming.database;

import com.ikalagaming.plugins.Plugin;

/**
 * The plugin class for the database plugin.
 *
 * @author Ches Burks
 *
 */
public class DatabasePlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-Database";

	/**
	 * Our database connection.
	 */
	private Database database;

	@Override
	public boolean onDisable() {
		this.database.closeConnection();
		return true;
	}

	@Override
	public boolean onEnable() {
		this.database = new Database();
		this.database.createConnection();
		return true;
	}
}
