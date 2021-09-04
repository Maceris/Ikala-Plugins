package com.ikalagaming.database;

import com.ikalagaming.launcher.Constants;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * A connection to the database.
 *
 * @author Ches Burks
 *
 */
@CustomLog(topic = DatabasePlugin.PLUGIN_NAME)
public class Database {

	private final String dbLocation = "jdbc:h2:"
		+ System.getProperty("user.dir") + Constants.PLUGIN_FOLDER_PATH
		+ DatabasePlugin.PLUGIN_NAME + Constants.DATA_PATH + "database";

	/**
	 * The current resource bundle for the plugin manager.
	 */
	private ResourceBundle resourceBundle;

	private Connection connection;

	/**
	 * Sets up a resource bundle.
	 */
	public Database() {
		this.resourceBundle = ResourceBundle.getBundle(
			"com.ikalagaming.database.resources.Database",
			Localization.getLocale());
	}

	/**
	 * Closes the connection to the database.
	 */
	public void closeConnection() {
		try {
			this.connection.close();
		}
		catch (SQLException e) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_CLOSING_CONNECTION", this.resourceBundle));
		}
	}

	/**
	 * Create a connection to the database. Will create the database if none
	 * exists yet.
	 */
	public void createConnection() {
		try {
			/*
			 * Not useless. Loads the driver in so that the connection can find
			 * it.
			 */
			Class.forName("org.h2.Driver"); // NOSONAR
		}
		catch (ClassNotFoundException e1) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_SETTING_UP_DRIVER", this.resourceBundle));
		}
		try {
			this.connection =
				DriverManager.getConnection(this.dbLocation, "sa", "");
		}
		catch (SQLException e) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_CONNECTING", this.resourceBundle));
		}
	}

}
