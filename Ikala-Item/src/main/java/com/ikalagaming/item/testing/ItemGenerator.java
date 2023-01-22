package com.ikalagaming.item.testing;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Affix;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.AttributeModifier;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.DamageModifier;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.ItemCriteria;
import com.ikalagaming.item.ItemStats;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.AffixType;
import com.ikalagaming.item.enums.ArmorType;
import com.ikalagaming.item.enums.ComponentType;
import com.ikalagaming.item.enums.ConsumableType;
import com.ikalagaming.item.enums.ItemType;
import com.ikalagaming.item.enums.ModifierType;
import com.ikalagaming.item.enums.Quality;
import com.ikalagaming.item.enums.WeaponType;
import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;
import com.ikalagaming.item.template.ItemStatsTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.random.RandomGen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to generate random items and templates for testing.
 *
 * @author Ches Burks
 *
 */
public class ItemGenerator {
	private static Random rand = new Random();
	private static RandomGen random = new RandomGen();
	private static AtomicInteger nextID = new AtomicInteger();

	/**
	 * Generate a random accessory.
	 *
	 * @return The randomly generated accessory.
	 */
	public static Accessory getAccessory() {
		Accessory accessory = new Accessory();

		accessory.setItemType(ItemType.ACCESSORY);
		accessory.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		accessory
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		accessory.setAccessoryType(
			ItemGenerator.random.selectEnumValue(AccessoryType.class));
		accessory.setID(accessory.getItemType().getPrefix()
			+ accessory.getAccessoryType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipment(accessory);
		return accessory;
	}

	/**
	 * Generate a random accessory template.
	 *
	 * @return The randomly generated accessory template.
	 */
	public static AccessoryTemplate getAccessoryTemplate() {
		AccessoryTemplate accessory = new AccessoryTemplate();

		accessory.setItemType(ItemType.ACCESSORY);
		accessory.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		accessory
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		accessory.setAccessoryType(
			ItemGenerator.random.selectEnumValue(AccessoryType.class));
		accessory.setID(accessory.getItemType().getPrefix()
			+ accessory.getAccessoryType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipmentTemplate(accessory);
		return accessory;
	}

	/**
	 * Generate a random affix.
	 *
	 * @return The randomly generated affix.
	 */
	public static Affix getAffix() {
		Affix affix = new Affix();
		affix.setAffixType(
			ItemGenerator.random.selectEnumValue(AffixType.class));
		affix.setID("Affix_" + ItemGenerator.nextID.getAndIncrement());
		affix.setItemCriteria(ItemGenerator.getItemCriteria());
		affix.setItemStats(ItemGenerator.getItemStats());
		affix.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		affix.setLevelRequirement(ItemGenerator.rand.nextInt(100));
		return affix;
	}

	/**
	 * Generate a random armor.
	 *
	 * @return The randomly generated armor.
	 */
	public static Armor getArmor() {
		Armor armor = new Armor();

		armor.setItemType(ItemType.ARMOR);
		armor.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		armor.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		armor.setArmorType(
			ItemGenerator.random.selectEnumValue(ArmorType.class));
		armor.setID(
			armor.getItemType().getPrefix() + armor.getArmorType().getPrefix()
				+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipment(armor);
		return armor;
	}

	/**
	 * Generate a random armor template.
	 *
	 * @return The randomly generated armor template.
	 */
	public static ArmorTemplate getArmorTemplate() {
		ArmorTemplate armor = new ArmorTemplate();

		armor.setItemType(ItemType.ARMOR);
		armor.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		armor.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		armor.setArmorType(
			ItemGenerator.random.selectEnumValue(ArmorType.class));
		armor.setID(
			armor.getItemType().getPrefix() + armor.getArmorType().getPrefix()
				+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipmentTemplate(armor);
		return armor;
	}

	/**
	 * Generates a random attribute modifier for use in generating random items.
	 *
	 * @return The randomly generated attribute modifier.
	 */
	private static AttributeModifier getAttributeModifier() {
		AttributeModifier attribute = new AttributeModifier();
		attribute.setAttribute(
			ItemGenerator.random.selectEnumValue(Attribute.class));
		attribute
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		attribute.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		return attribute;
	}

	/**
	 * Generates a random attribute modifier template for use in generating
	 * random item templates.
	 *
	 * @return The randomly generated attribute modifier template.
	 */
	private static AttributeModifierTemplate getAttributeModifierTemplate() {
		AttributeModifierTemplate attribute = new AttributeModifierTemplate();
		attribute.setAttribute(
			ItemGenerator.random.selectEnumValue(Attribute.class));
		attribute
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		attribute.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		attribute
			.setVariance(ItemGenerator.rand.nextInt(attribute.getAmount()));
		return attribute;
	}

	/**
	 * Generate a random component.
	 *
	 * @return The randomly generated component.
	 */
	public static Component getComponent() {
		Component component = new Component();

		component.setItemType(ItemType.COMPONENT);
		component.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		component
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		component.setComponentType(
			ItemGenerator.random.selectEnumValue(ComponentType.class));
		component.setID(component.getItemType().getPrefix()
			+ component.getComponentType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		component.setItemStats(ItemGenerator.getItemStats());
		component.setItemCriteria(ItemGenerator.getItemCriteria());

		return component;
	}

	/**
	 * Generate a random consumable.
	 *
	 * @return The randomly generated consumable.
	 */
	public static Consumable getConsumable() {
		Consumable consumable = new Consumable();
		consumable.setItemType(ItemType.CONSUMABLE);
		consumable.setConsumableType(
			ItemGenerator.random.selectEnumValue(ConsumableType.class));
		consumable.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		consumable
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		consumable.setID(consumable.getItemType().getPrefix()
			+ consumable.getConsumableType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		return consumable;
	}

	/**
	 * Generate a random damage modifier for use in generating random items.
	 *
	 * @return The randomly generated damage modifier.
	 */
	private static DamageModifier getDamageModifier() {
		DamageModifier damage = new DamageModifier();
		damage.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		damage.setDamageType(
			ItemGenerator.random.selectEnumValue(DamageType.class));
		damage
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		return damage;
	}

	/**
	 * Generate a random damage modifier template for use in generating random
	 * item templates.
	 *
	 * @return The randomly generated damage modifier template.
	 */
	private static DamageModifierTemplate getDamageModifierTemplate() {
		DamageModifierTemplate damage = new DamageModifierTemplate();
		damage.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		damage.setDamageType(
			ItemGenerator.random.selectEnumValue(DamageType.class));
		damage
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		damage.setVariance(ItemGenerator.rand.nextInt(damage.getAmount()));
		return damage;
	}

	/**
	 * Generate random item criteria.
	 *
	 * @return The randomly generated item criteria.
	 */
	private static ItemCriteria getItemCriteria() {
		ItemCriteria itemCriteria = new ItemCriteria();

		List<ItemType> itemTypes = new ArrayList<>();
		switch (ItemGenerator.rand.nextInt(3)) {
			case 0:
				itemTypes.add(ItemType.ACCESSORY);
				itemCriteria.setItemTypes(itemTypes);
				List<AccessoryType> accessoryTypes = new ArrayList<>();
				accessoryTypes.add(
					ItemGenerator.random.selectEnumValue(AccessoryType.class));
				itemCriteria.setAccessoryTypes(accessoryTypes);
				break;
			case 1:
				itemTypes.add(ItemType.ARMOR);
				itemCriteria.setItemTypes(itemTypes);
				List<ArmorType> armorTypes = new ArrayList<>();
				armorTypes
					.add(ItemGenerator.random.selectEnumValue(ArmorType.class));
				itemCriteria.setArmorTypes(armorTypes);
				break;
			case 2:
			default:
				itemTypes.add(ItemType.WEAPON);
				itemCriteria.setItemTypes(itemTypes);
				List<WeaponType> weaponTypes = new ArrayList<>();
				weaponTypes.add(
					ItemGenerator.random.selectEnumValue(WeaponType.class));
				itemCriteria.setWeaponTypes(weaponTypes);
				break;
		}

		return itemCriteria;
	}

	/**
	 * Generate a random item stats for use in equipment.
	 *
	 * @return The randomly generated item stats.
	 */
	private static ItemStats getItemStats() {
		ItemStats template = new ItemStats();
		final int maxDamages = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxDamages; ++i) {
			template.getDamageBuffs().add(ItemGenerator.getDamageModifier());
		}
		final int maxBuffs = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxBuffs; ++i) {
			template.getResistanceBuffs()
				.add(ItemGenerator.getDamageModifier());
		}
		final int maxAttributes = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxAttributes; ++i) {
			template.getAttributeBuffs()
				.add(ItemGenerator.getAttributeModifier());
		}
		return template;
	}

	/**
	 * Generate a random item stats template for use in equipment templates.
	 *
	 * @return The randomly generated item stats template.
	 */
	private static ItemStatsTemplate getItemStatsTemplate() {
		ItemStatsTemplate template = new ItemStatsTemplate();
		final int maxDamages = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxDamages; ++i) {
			template.getDamageBuffs()
				.add(ItemGenerator.getDamageModifierTemplate());
		}
		final int maxBuffs = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxBuffs; ++i) {
			template.getResistanceBuffs()
				.add(ItemGenerator.getDamageModifierTemplate());
		}
		final int maxAttributes = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxAttributes; ++i) {
			template.getAttributeBuffs()
				.add(ItemGenerator.getAttributeModifierTemplate());
		}
		return template;
	}

	/**
	 * Generate random junk.
	 *
	 * @return The randomly generated junk.
	 */
	public static Junk getJunk() {
		Junk junk = new Junk();
		junk.setItemType(ItemType.JUNK);
		junk.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		junk.setQuality(Quality.TRASH);
		junk.setID(junk.getItemType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		return junk;
	}

	/**
	 * Generate random material.
	 *
	 * @return The randomly generated material.
	 */
	public static Material getMaterial() {
		Material material = new Material();
		material.setItemType(ItemType.MATERIAL);
		material.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		material.setQuality(Quality.TRASH);
		material.setID(material.getItemType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		return material;
	}

	/**
	 * Generate random quest item.
	 *
	 * @return The randomly generated quest item.
	 */
	public static Quest getQuest() {
		Quest quest = new Quest();
		quest.setItemType(ItemType.QUEST);
		quest.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		quest.setQuality(Quality.TRASH);
		quest.setID(quest.getItemType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		return quest;
	}

	/**
	 * Get a random item of any item type.
	 *
	 * @return The random item.
	 */
	public static Item getRandomItem() {
		switch (ItemGenerator.random.selectEnumValue(ItemType.class)) {
			case ACCESSORY:
				return ItemGenerator.getAccessory();
			case ARMOR:
				return ItemGenerator.getArmor();
			case COMPONENT:
				return ItemGenerator.getComponent();
			case CONSUMABLE:
				return ItemGenerator.getConsumable();
			case JUNK:
				return ItemGenerator.getJunk();
			case MATERIAL:
				return ItemGenerator.getMaterial();
			case QUEST:
				return ItemGenerator.getQuest();
			case WEAPON:
			default:
				return ItemGenerator.getWeapon();
		}
	}

	/**
	 * Generate a random weapon.
	 *
	 * @return A randomly generated weapon.
	 */
	public static Weapon getWeapon() {
		Weapon weapon = new Weapon();

		// Item traits, plus weapon type so we can name it properly
		weapon.setItemType(ItemType.WEAPON);
		weapon.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setWeaponType(
			ItemGenerator.random.selectEnumValue(WeaponType.class));
		weapon.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		weapon.setID(weapon.getItemType().getPrefix()
			+ weapon.getWeaponType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());

		ItemGenerator.populateEquipment(weapon);

		// weapon traits
		weapon.setMaxDamage(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setMinDamage(ItemGenerator.rand.nextInt(weapon.getMaxDamage()));

		return weapon;
	}

	/**
	 * Generate a random weapon template.
	 *
	 * @return A randomly generated weapon template.
	 */
	public static WeaponTemplate getWeaponTemplate() {
		WeaponTemplate weapon = new WeaponTemplate();

		// Item traits, plus weapon type so we can name it properly
		weapon.setItemType(ItemType.WEAPON);
		weapon.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setWeaponType(
			ItemGenerator.random.selectEnumValue(WeaponType.class));
		weapon.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		weapon.setID(weapon.getItemType().getPrefix()
			+ weapon.getWeaponType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());

		ItemGenerator.populateEquipmentTemplate(weapon);

		// weapon traits
		weapon.setMaxDamage(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setMinDamage(ItemGenerator.rand.nextInt(weapon.getMaxDamage()));

		return weapon;
	}

	/**
	 * Fill out fields for equipment.
	 *
	 * @param equipment The item to fill out fields for.
	 */
	private static void populateEquipment(Equipment equipment) {
		final int maxRequirements = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxRequirements; ++i) {
			equipment.getAttributeRequirements()
				.add(ItemGenerator.getAttributeModifier());
		}
		equipment
			.setLevelRequirement(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		equipment.setItemStats(ItemGenerator.getItemStats());
		Affix affix = ItemGenerator.getAffix();
		switch (affix.getAffixType()) {
			case PREFIX:
				equipment.setPrefix(affix);
				break;
			case SUFFIX:
				equipment.setSuffix(affix);
				break;
			default:
				break;
		}

		Component gem1 = ItemGenerator.getComponent();
		gem1.setComponentType(ComponentType.GEM);
		equipment.setGem1(gem1);

		Component gem2 = ItemGenerator.getComponent();
		gem2.setComponentType(ComponentType.GEM);
		equipment.setGem1(gem2);

		Component augment = ItemGenerator.getComponent();
		augment.setComponentType(ComponentType.AUGMENT);
		equipment.setGem1(augment);
	}

	/**
	 * Fill out fields for equipment templates.
	 *
	 * @param template The item template to fill out fields for.
	 */
	private static void populateEquipmentTemplate(EquipmentTemplate template) {
		final int maxRequirements = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxRequirements; ++i) {
			template.getAttributeRequirements()
				.add(ItemGenerator.getAttributeModifierTemplate());
		}
		template
			.setLevelRequirement(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		template.setItemStatsTemplate(ItemGenerator.getItemStatsTemplate());
	}
}
