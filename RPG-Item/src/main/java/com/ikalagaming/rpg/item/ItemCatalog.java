package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.template.AccessoryTemplate;
import com.ikalagaming.rpg.item.template.ArmorTemplate;
import com.ikalagaming.rpg.item.template.WeaponTemplate;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A catalog of items and templates.
 *
 * @author Ches Burks
 *
 */
@Getter
public class ItemCatalog {
	@Getter(value = AccessLevel.NONE)
	private static ItemCatalog instance;

	/**
	 * Destroy the current instance if there is on.
	 */
	public static void destroyInstance() {
		if (ItemCatalog.instance == null) {
			return;
		}
		ItemCatalog.instance.accessories.clear();
		ItemCatalog.instance.accessoryTemplates.clear();
		ItemCatalog.instance.affixes.clear();
		ItemCatalog.instance.armors.clear();
		ItemCatalog.instance.armorTemplates.clear();
		ItemCatalog.instance.components.clear();
		ItemCatalog.instance.consumables.clear();
		ItemCatalog.instance.junk.clear();
		ItemCatalog.instance.materials.clear();
		ItemCatalog.instance.quests.clear();
		ItemCatalog.instance.weapons.clear();
		ItemCatalog.instance.weaponTemplates.clear();
		ItemCatalog.instance = null;
	}

	/**
	 * Get an instance of the item catalog. The first time this is called, it
	 * will load up all the items from the database.
	 *
	 * @return The instance of the item catalog.
	 */
	public static ItemCatalog getInstance() {
		if (ItemCatalog.instance == null) {
			ItemCatalog.instance = new ItemCatalog();
		}
		return ItemCatalog.instance;
	}

	private List<Accessory> accessories;
	private List<AccessoryTemplate> accessoryTemplates;
	private List<Affix> affixes;
	private List<Armor> armors;
	private List<ArmorTemplate> armorTemplates;
	private List<Component> components;
	private List<Consumable> consumables;
	private List<Junk> junk;
	private List<Material> materials;
	private List<Quest> quests;
	private List<WeaponTemplate> weapons;
	private List<WeaponTemplate> weaponTemplates;

	/**
	 * Private constructor so we control it.
	 */
	private ItemCatalog() {
		this.accessories = new ArrayList<>();
		this.accessoryTemplates = new ArrayList<>();
		this.affixes = new ArrayList<>();
		this.armors = new ArrayList<>();
		this.armorTemplates = new ArrayList<>();
		this.components = new ArrayList<>();
		this.consumables = new ArrayList<>();
		this.junk = new ArrayList<>();
		this.materials = new ArrayList<>();
		this.quests = new ArrayList<>();
		this.weapons = new ArrayList<>();
		this.weaponTemplates = new ArrayList<>();
	}

}
