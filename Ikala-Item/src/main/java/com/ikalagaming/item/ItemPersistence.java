package com.ikalagaming.item;

import com.ikalagaming.database.Database;
import com.ikalagaming.database.DatabasePlugin;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.Optional;

/**
 * Tools for reading from and writing to the database.
 *
 * @author Ches Burks
 *
 */
public class ItemPersistence {

	private static final Table<?> AFFIX_TABLE =
		DSL.table(DSL.name(Affix.TABLE_NAME));

	private DSLContext context;

	/**
	 * Fetch the affixes from the affix table.
	 */
	public void fetchAffix() {
		Result<Record> affixes =
			this.context.select().from(ItemPersistence.AFFIX_TABLE).fetch();
		affixes.forEach(affix -> {
			System.out.println(affix.formatJSON());
		});
	}

	/**
	 * Load the database context from the database plugin, for use in the rest
	 * of the methods.
	 */
	public void loadContext() {
		Optional<Plugin> plugin =
			PluginManager.getInstance().getPlugin("Ikala-Database");
		Database database = ((DatabasePlugin) plugin.get()).getDatabase();
		this.context = database.getContext();
	}

}
