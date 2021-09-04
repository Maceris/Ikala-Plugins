package com.ikalagaming.database;

import com.ikalagaming.database.query.Column;
import com.ikalagaming.launcher.Constants;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;
import lombok.NonNull;
import lombok.Synchronized;
import org.jooq.Constraint;
import org.jooq.CreateTableColumnStep;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A connection to the database.
 *
 * @author Ches Burks
 *
 */
@CustomLog(topic = DatabasePlugin.PLUGIN_NAME)
public class Database {

	private static final String DB_LOCATION = "jdbc:h2:"
		+ System.getProperty("user.dir") + Constants.PLUGIN_FOLDER_PATH
		+ DatabasePlugin.PLUGIN_NAME + Constants.DATA_PATH + "database";

	/**
	 * The current resource bundle for the plugin manager.
	 */
	private static ResourceBundle resourceBundle =
		ResourceBundle.getBundle("com.ikalagaming.database.resources.Database",
			Localization.getLocale());

	/**
	 * The connection to our database.
	 */
	private static Connection connection;

	/**
	 * Checks that the connection is open and throw an exception if not.
	 */
	private static void checkConnection() {
		boolean invalidConnection = false;
		try {
			if (null == Database.connection || Database.connection.isClosed()) {
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
	public static void closeConnection() {
		try {
			connection.close();
		}
		catch (SQLException e) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_CLOSING_CONNECTION", resourceBundle));
		}
	}

	/**
	 * Create a connection to the database. Will create the database if none
	 * exists yet.
	 */
	@Synchronized
	public static void createConnection() {

		try {
			/*
			 * Not useless. Loads the driver in so that the connection can find
			 * it.
			 */
			Class.forName("org.h2.Driver"); // NOSONAR
		}
		catch (ClassNotFoundException e1) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_SETTING_UP_DRIVER", resourceBundle));
		}
		try {
			connection =
				DriverManager.getConnection(Database.DB_LOCATION, "sa", "");
		}
		catch (SQLException e) {
			Database.log.warning(SafeResourceLoader
				.getString("ERROR_CONNECTING", resourceBundle));
		}
	}

	/**
	 * Create a table in the database.
	 *
	 * @param name The name of the table.
	 * @param columns The columns to have in the table.
	 * @param constraints Constraints to apply in the table.
	 * @throws InvalidConnectionException If there is no connection open.
	 */
	@Synchronized
	public static void createTable(@NonNull String name, List<Column> columns,
		List<Constraint> constraints) {
		/*
		 * We end with an execute call but have no way to convince the linter
		 * that this won't go wrong. To the linters credit, something may go
		 * wrong.
		 */
		CreateTableColumnStep currentStep = startQuery().createTable(name);// NOSONAR
		for (Column c : columns) {
			currentStep = currentStep.column(c.getName(), c.getType());
		}
		currentStep.constraints(constraints);
		currentStep.execute();
	}

	/**
	 * Returns the a new {@link DSLContext} to start building a jOOQ query.
	 *
	 * @return The current context.
	 * @throws InvalidConnectionException If there is no connection open.
	 */
	@Synchronized
	public static DSLContext startQuery() {
		checkConnection();
		return DSL.using(connection, SQLDialect.H2);
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private Database() {}

}
