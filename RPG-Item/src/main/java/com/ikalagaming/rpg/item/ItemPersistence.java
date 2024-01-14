package com.ikalagaming.rpg.item;

import com.ikalagaming.database.Database;
import com.ikalagaming.database.DatabasePlugin;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.rpg.item.enums.AffixType;
import com.ikalagaming.rpg.item.enums.Quality;
import com.ikalagaming.rpg.item.persistence.ItemCriteriaConverter;
import com.ikalagaming.rpg.item.persistence.ItemStatsConverter;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;

/**
 * Tools for reading from and writing to the database.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class ItemPersistence {

	private static final Table<?> AFFIX_TABLE =
		DSL.table(DSL.name(Affix.TABLE_NAME));

	private static final ItemCriteriaConverter itemCriteriaConverter =
		new ItemCriteriaConverter();

	private static final ItemStatsConverter itemStatsConverter =
		new ItemStatsConverter();
	private DSLContext context;

	/**
	 * Fetch the affixes from the affix table.
	 */
	public void fetchAffix() {
		ItemCatalog catalog = ItemCatalog.getInstance();

		List<org.jooq.Record> affixes =
			this.context.select().from(ItemPersistence.AFFIX_TABLE).fetch();

		affixes.forEach(entry -> {
			Affix affix = new Affix();

			affix.setID((String) entry.get("ID"));
			affix.setLevelRequirement((Integer) entry.get("LEVEL_REQUIREMENT"));
			affix.setAffixType(
				AffixType.valueOf((String) entry.get("AFFIX_TYPE")));
			affix.setItemCriteria(ItemPersistence.itemCriteriaConverter
				.convertToEntityAttribute((String) entry.get("ITEM_CRITERIA")));
			affix.setQuality(Quality.valueOf((String) entry.get("QUALITY")));
			affix.setItemStats(ItemPersistence.itemStatsConverter
				.convertToEntityAttribute((String) entry.get("ITEM_STATS")));

			catalog.getAffixes().add(affix);
		});
	}

	/**
	 * Load the database context from the database plugin, for use in the rest
	 * of the methods.
	 */
	public void loadContext() {
		Optional<Plugin> plugin =
			PluginManager.getInstance().getPlugin("Ikala-Database");
		if (plugin.isEmpty()) {
			// TODO localize
			ItemPersistence.log.warn("Database not loaded!");
			return;
		}
		Database database = ((DatabasePlugin) plugin.get()).getDatabase();
		this.context = database.getContext();
	}

}
