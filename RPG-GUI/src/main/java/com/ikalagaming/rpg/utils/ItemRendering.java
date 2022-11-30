package com.ikalagaming.rpg.utils;

import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Affix;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.AttributeModifier;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.DamageModifier;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.ItemStats;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.ArmorType;
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

import imgui.ImGui;

/**
 * Utilities for drawing item information on a GUI.
 *
 * @author Ches Burks
 *
 */
public class ItemRendering {
	/**
	 * Draw details for an accessory.
	 *
	 * @param accessory The accessory to draw details for.
	 */
	public static void drawAccessoryInfo(Accessory accessory) {
		ItemRendering.drawEquipmentName(accessory);
		ImGui
			.text("Accessory Type: " + accessory.getAccessoryType().toString());
		ItemRendering.drawEquipmentInfo(accessory);
	}

	/**
	 * Draw details for an accessory.
	 *
	 * @param accessory The accessory to draw details for.
	 */
	public static void drawAccessoryTemplateInfo(AccessoryTemplate accessory) {
		ItemRendering.drawEquipmentTemplateName(accessory);
		ImGui
			.text("Accessory Type: " + accessory.getAccessoryType().toString());
		ItemRendering.drawEquipmentTemplateInfo(accessory);
	}

	/**
	 * Draw details for armor.
	 *
	 * @param armor The armor to draw details for.
	 */
	public static void drawArmorInfo(Armor armor) {
		ItemRendering.drawEquipmentName(armor);
		ImGui.text("Armor Type: " + armor.getArmorType().toString());
		ItemRendering.drawEquipmentInfo(armor);
	}

	/**
	 * Draw details for armor template.
	 *
	 * @param armor The armor template to draw details for.
	 */
	public static void drawArmorTemplateInfo(ArmorTemplate armor) {
		ItemRendering.drawEquipmentTemplateName(armor);
		ImGui.text("Armor Type: " + armor.getArmorType().toString());
		ItemRendering.drawEquipmentTemplateInfo(armor);
	}

	/**
	 * Draw component information.
	 *
	 * @param component The component to draw details for.
	 */
	public static void drawComponentInfo(Component component) {
		ItemRendering.drawName(component);
		ImGui
			.text("Component Type: " + component.getComponentType().toString());
		ItemRendering.drawItemStats(component.getItemStats());
		ImGui.text("Can be applied to: ");
		for (ItemType type : component.getItemCriteria().getItemTypes()) {
			ImGui.bulletText(type.toString());
			switch (type) {
				case ACCESSORY:
					for (AccessoryType subType : component.getItemCriteria()
						.getAccessoryTypes()) {
						ImGui.setCursorPosX(ImGui.getCursorPosX()
							+ ImGui.getTreeNodeToLabelSpacing());
						ImGui.bulletText(subType.toString());
					}
					break;
				case ARMOR:
					for (ArmorType subType : component.getItemCriteria()
						.getArmorTypes()) {
						ImGui.setCursorPosX(ImGui.getCursorPosX()
							+ ImGui.getTreeNodeToLabelSpacing());
						ImGui.bulletText(subType.toString());
					}
					break;
				case WEAPON:
					for (WeaponType subType : component.getItemCriteria()
						.getWeaponTypes()) {
						ImGui.setCursorPosX(ImGui.getCursorPosX()
							+ ImGui.getTreeNodeToLabelSpacing());
						ImGui.bulletText(subType.toString());
					}
					break;
				case COMPONENT:
				case CONSUMABLE:
				case JUNK:
				case MATERIAL:
				case QUEST:
				default:
					// Just list the type
					break;
			}
		}
	}

	/**
	 * Draw details for a consumable.
	 *
	 * @param consumable The consumable.
	 */
	public static void drawConsumableInfo(Consumable consumable) {
		ItemRendering.drawName(consumable);
		ImGui.text(
			"Consumable type: " + consumable.getConsumableType().toString());
	}

	/**
	 * Draw details for equipment.
	 *
	 * @param equipment The equipment to draw details for.
	 */
	public static void drawEquipmentInfo(Equipment equipment) {
		ImGui.text("Item Level: " + equipment.getItemLevel());
		ImGui.text("Quality: ");
		ImGui.sameLine();
		ImGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()),
			equipment.getQuality().toString());
		ImGui.text("Level Requirement: " + equipment.getLevelRequirement());
		ImGui.text("Attribute Requirements:");
		for (AttributeModifier mod : equipment.getAttributeRequirements()) {
			ImGui.bulletText(ItemRendering.modifierText(mod));
		}
		ItemStats combinedStats = equipment.getCombinedStats();
		ItemRendering.drawItemStats(combinedStats);
	}

	/**
	 * Draw a name including prefixes and affixes.
	 *
	 * @param equipment The equipment to draw a name for.
	 */
	private static void drawEquipmentName(Equipment equipment) {
		ImGui.text("Name: ");
		Affix prefix = equipment.getPrefix();
		if (prefix != null) {
			ImGui.sameLine();
			ImGui.textColored(
				ItemRendering.getQualityColor(prefix.getQuality()),
				prefix.getID() + " ");
		}
		ImGui.sameLine();
		ImGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()),
			equipment.getID());
		Affix suffix = equipment.getSuffix();
		if (suffix != null) {
			ImGui.sameLine();
			ImGui.textColored(
				ItemRendering.getQualityColor(suffix.getQuality()),
				" " + suffix.getID());
		}
	}

	/**
	 * Draw details for equipment.
	 *
	 * @param equipment The equipment to draw details for.
	 */
	public static void drawEquipmentTemplateInfo(EquipmentTemplate equipment) {
		ImGui.text("Item Level: " + equipment.getItemLevel());
		ImGui.text("Quality: ");
		ImGui.sameLine();
		ImGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()),
			equipment.getQuality().toString());
		ImGui.text("Level Requirement: " + equipment.getLevelRequirement());
		ImGui.text("Attribute Requirements:");
		for (AttributeModifierTemplate mod : equipment
			.getAttributeRequirements()) {
			ImGui.bulletText(
				ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
		}
		ItemRendering.drawItemStatsTemplate(equipment.getItemStatsTemplate());
	}

	/**
	 * Draw a name including prefixes and affixes.
	 *
	 * @param equipment The equipment template to draw a name for.
	 */
	private static void drawEquipmentTemplateName(EquipmentTemplate equipment) {
		ImGui.text("Name: ");
		ImGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()),
			equipment.getID());
	}

	/**
	 * Draw item stats, used as part of drawing item details.
	 *
	 * @param itemStats The item stats to draw.
	 */
	public static void drawItemStats(ItemStats itemStats) {
		ImGui.text("Item Stats");
		ImGui.bulletText("Damage Buffs");
		for (DamageModifier mod : itemStats.getDamageBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(ItemRendering.modifierText(mod));
		}
		ImGui.bulletText("Resistance Buffs");
		for (DamageModifier mod : itemStats.getResistanceBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(ItemRendering.modifierText(mod));
		}
		ImGui.bulletText("Attribute Buffs");
		for (AttributeModifier mod : itemStats.getAttributeBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(ItemRendering.modifierText(mod));
		}
	}

	/**
	 * Draw item stats template, used as part of drawing item details.
	 *
	 * @param itemStats The item stats template to draw.
	 */
	public static void drawItemStatsTemplate(ItemStatsTemplate itemStats) {
		ImGui.text("Item Stats");
		ImGui.bulletText("Damage Buffs");
		for (DamageModifierTemplate mod : itemStats.getDamageBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(
				ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
		}
		ImGui.bulletText("Resistance Buffs");
		for (DamageModifierTemplate mod : itemStats.getResistanceBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(
				ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
		}
		ImGui.bulletText("Attribute Buffs");
		for (AttributeModifierTemplate mod : itemStats.getAttributeBuffs()) {
			ImGui.setCursorPosX(
				ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing());
			ImGui.bulletText(
				ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
		}
	}

	/**
	 * Draw details for junk.
	 *
	 * @param junk The junk.
	 */
	public static void drawJunkInfo(Junk junk) {
		ItemRendering.drawName(junk);
	}

	/**
	 * Draw details for a material.
	 *
	 * @param material The material.
	 */
	public static void drawMaterialInfo(Material material) {
		ItemRendering.drawName(material);
	}

	/**
	 * Draw an item name, colored based on quality.
	 *
	 * @param item The item to draw a name for.
	 */
	private static void drawName(Item item) {
		ImGui.text("Name: ");
		ImGui.sameLine();
		ImGui.textColored(ItemRendering.getQualityColor(item.getQuality()),
			item.getID());
	}

	/**
	 * DRaw details for a quest item.
	 *
	 * @param quest The quest item.
	 */
	public static void drawQuestInfo(Quest quest) {
		ItemRendering.drawName(quest);
	}

	/**
	 * Draw weapon info for the provided weapon.
	 *
	 * @param weapon The weapon to draw.
	 */
	public static void drawWeaponInfo(Weapon weapon) {
		ItemRendering.drawEquipmentName(weapon);
		ImGui.text("Weapon Type: " + weapon.getWeaponType().toString());
		ImGui.text(
			"Damage: " + weapon.getMinDamage() + "-" + weapon.getMaxDamage());
		ItemRendering.drawEquipmentInfo(weapon);
	}

	/**
	 * Draw weapon info for the provided weapon template.
	 *
	 * @param weapon The weapon template to draw.
	 */
	public static void drawWeaponTemplateInfo(WeaponTemplate weapon) {
		ItemRendering.drawEquipmentTemplateName(weapon);
		ImGui.text("Weapon Type: " + weapon.getWeaponType().toString());
		ImGui.text(
			"Damage: " + weapon.getMinDamage() + "-" + weapon.getMaxDamage());
		ItemRendering.drawEquipmentTemplateInfo(weapon);
	}

	/**
	 * Return an integer color for the given quality.
	 *
	 * @param quality The quality.
	 * @return The associated color for names or borders.
	 */
	public static int getQualityColor(Quality quality) {
		switch (quality) {
			case COMMON:
				return ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
			case EPIC:
				return ImGui.colorConvertFloat4ToU32(1f, 0.6f, 0f, 1f);
			case LEGENDARY:
				return ImGui.colorConvertFloat4ToU32(1f, 0f, 1f, 1f);
			case MAGIC:
				return ImGui.colorConvertFloat4ToU32(1f, 0.9f, 0f, 1f);
			case RARE:
				return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 1f, 1f);
			case TRASH:
			default:
				return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);
		}
	}

	/**
	 * Convert an attribute modifier to a string format.
	 *
	 * @param modifier The modifier.
	 * @return The string version of the modifier.
	 */
	private static String modifierText(AttributeModifier modifier) {
		StringBuilder result = new StringBuilder();
		result.append(modifier.getAmount());
		if (ModifierType.PERCENTAGE.equals(modifier.getType())) {
			result.append("%%");
		}
		result.append(' ');
		result.append(modifier.getAttribute().toString());
		return result.toString();
	}

	/**
	 * Convert a damage modifier to a string format.
	 *
	 * @param modifier The modifier.
	 * @return The string version of the modifier.
	 */
	private static String modifierText(DamageModifier modifier) {
		StringBuilder result = new StringBuilder();
		result.append(modifier.getAmount());
		if (ModifierType.PERCENTAGE.equals(modifier.getType())) {
			result.append("%%");
		}
		result.append(' ');
		result.append(modifier.getDamageType().toString());
		return result.toString();
	}
}
