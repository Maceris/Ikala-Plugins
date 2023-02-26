package com.ikalagaming.database;

import com.ikalagaming.localization.Localization;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * A connection to the database.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Database {

	/**
	 * The current resource bundle for the plugin manager.
	 */
	private ResourceBundle resourceBundle;

	/**
	 * The connection string for the database.
	 * 
	 * @return The connection string for the database.
	 */
	@Getter
	private final String connectionString;

	/**
	 * The connection to our database.
	 */
	@Getter
	private Connection connection;

	@Getter
	private DSLContext context;

	/**
	 * Creates a new database handler for the database at the given path. If no
	 * database exists there, it's going to try and create one when a connection
	 * is established.
	 *
	 * @param location The location of the database.
	 * @see #createConnection()
	 */
	public Database(String location) {
		this.connectionString = "jdbc:h2:" + location;
		this.resourceBundle = ResourceBundle.getBundle(
			"com.ikalagaming.database.Database", Localization.getLocale());
	}

	/**
	 * Checks that the connection is open and throw an exception if not.
	 */
	private void checkConnection() {
		boolean invalidConnection = false;
		try {
			if (null == this.connection || this.connection.isClosed()) {
				invalidConnection = true;
			}
		}
		catch (SQLException e) {
			invalidConnection = true;
		}

		if (invalidConnection) {
			throw new InvalidConnectionException();
		}
	}

	/**
	 * Closes the connection to the database.
	 */
	@Synchronized
	public void closeConnection() {
		try {
			context = null;
			connection.close();
		}
		catch (SQLException e) {
			Database.log.warn(SafeResourceLoader
				.getString("ERROR_CLOSING_CONNECTION", resourceBundle));
		}
	}

	/**
	 * Create a connection to the database. Will create the database if none
	 * exists yet.
	 */
	@Synchronized
	public void createConnection() {
		if (connection != null) {
			log.warn(SafeResourceLoader.getString("DUPLICATE_CONNECTION",
				resourceBundle));
			return;
		}

		try {
			/*
			 * Not useless. Loads the driver in so that the connection can find
			 * it.
			 */
			Class.forName("org.h2.Driver"); // NOSONAR
		}
		catch (ClassNotFoundException e1) {
			Database.log.warn(SafeResourceLoader
				.getString("ERROR_SETTING_UP_DRIVER", resourceBundle));
		}
		
		try {
			DriverManager.setLoginTimeout(5);
			Properties props = new Properties();
			props.put("user", "sa");
			props.put("password", "");
			props.put("connectTimeout", "5000");
			props.put("socketTimeout", "5000");
			connection =
				DriverManager.getConnection(this.connectionString, props);
		}
		catch (SQLException e) {
			Database.log.warn(SafeResourceLoader.getString("ERROR_CONNECTING",
				resourceBundle), e);
		}
		log.debug("Created connection to database");
		context = DSL.using(connection, SQLDialect.H2);
	}

	/**
	 * Returns the a new {@link DSLContext} to start building a jOOQ query.
	 *
	 * @return The current context.
	 * @throws InvalidConnectionException If there is no connection open.
	 */
	@Synchronized
	public DSLContext startQuery() {
		checkConnection();
		return DSL.using(connection, SQLDialect.H2);
	}

}
