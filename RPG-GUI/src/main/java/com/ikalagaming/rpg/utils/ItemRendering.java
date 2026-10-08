package com.ikalagaming.rpg.utils;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.rpg.item.Accessory;
import com.ikalagaming.rpg.item.Affix;
import com.ikalagaming.rpg.item.Armor;
import com.ikalagaming.rpg.item.AttributeModifier;
import com.ikalagaming.rpg.item.Component;
import com.ikalagaming.rpg.item.Consumable;
import com.ikalagaming.rpg.item.DamageModifier;
import com.ikalagaming.rpg.item.Equipment;
import com.ikalagaming.rpg.item.Item;
import com.ikalagaming.rpg.item.ItemCriteria;
import com.ikalagaming.rpg.item.ItemStats;
import com.ikalagaming.rpg.item.Junk;
import com.ikalagaming.rpg.item.Material;
import com.ikalagaming.rpg.item.Quest;
import com.ikalagaming.rpg.item.Weapon;
import com.ikalagaming.rpg.item.enums.AccessoryType;
import com.ikalagaming.rpg.item.enums.ArmorType;
import com.ikalagaming.rpg.item.enums.ItemType;
import com.ikalagaming.rpg.item.enums.ModifierType;
import com.ikalagaming.rpg.item.enums.Quality;
import com.ikalagaming.rpg.item.enums.WeaponType;
import com.ikalagaming.rpg.item.template.AccessoryTemplate;
import com.ikalagaming.rpg.item.template.ArmorTemplate;
import com.ikalagaming.rpg.item.template.AttributeModifierTemplate;
import com.ikalagaming.rpg.item.template.DamageModifierTemplate;
import com.ikalagaming.rpg.item.template.EquipmentTemplate;
import com.ikalagaming.rpg.item.template.ItemStatsTemplate;
import com.ikalagaming.rpg.item.template.WeaponTemplate;

/**
 * Utilities for drawing item information on a GUI.
 *
 * @author Ches Burks
 */
public class ItemRendering {
    /** The color to use for common items names or borders. */
    private static final int COLOR_COMMON = IkGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);

    /** The color to use for epic items names or borders. */
    private static final int COLOR_EPIC = IkGui.colorConvertFloat4ToU32(1f, 0.6f, 0f, 1f);

    /** The color to use for legendary items names or borders. */
    private static final int COLOR_LEGENDARY = IkGui.colorConvertFloat4ToU32(1f, 0f, 1f, 1f);

    /** The color to use for magic items names or borders. */
    private static final int COLOR_MAGIC = IkGui.colorConvertFloat4ToU32(1f, 0.9f, 0f, 1f);

    /** The color to use for rare items names or borders. */
    private static final int COLOR_RARE = IkGui.colorConvertFloat4ToU32(0.3f, 0.3f, 1f, 1f);

    /** The color to use for trash items names or borders. */
    private static final int COLOR_TRASH = IkGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);

    /**
     * Draw details for an accessory.
     *
     * @param accessory The accessory to draw details for.
     */
    public static void drawAccessoryInfo(Accessory accessory) {
        ItemRendering.drawEquipmentName(accessory);
        IkGui.text("Accessory Type: " + accessory.getAccessoryType().toString());
        ItemRendering.drawEquipmentInfo(accessory);
    }

    /**
     * Draw details for an accessory Template.
     *
     * @param accessory The accessory to draw details for.
     */
    public static void drawAccessoryTemplateInfo(AccessoryTemplate accessory) {
        ItemRendering.drawEquipmentTemplateName(accessory);
        IkGui.text("Accessory Type: " + accessory.getAccessoryType().toString());
        ItemRendering.drawEquipmentTemplateInfo(accessory);
    }

    /**
     * Draw details for an affix by itself.
     *
     * @param affix The affix to draw details for.
     */
    public static void drawAffix(Affix affix) {
        IkGui.text("Name: ");
        IkGui.sameLine();
        IkGui.textColored(ItemRendering.getQualityColor(affix.getQuality()), affix.getID());
        IkGui.text("Quality: ");
        IkGui.sameLine();
        if (affix.getQuality() != null) {
            IkGui.textColored(
                    ItemRendering.getQualityColor(affix.getQuality()),
                    affix.getQuality().toString());
        } else {
            IkGui.text("(missing)");
        }
        IkGui.text("Affix Type: " + affix.getAffixType().toString());
        IkGui.text("Level Requirement: " + affix.getLevelRequirement());
        if (affix.getItemStats() != null) {
            ItemRendering.drawItemStats(affix.getItemStats());
        }
        if (affix.getItemCriteria() != null) {
            ItemRendering.drawItemCriteria(affix.getItemCriteria());
        }
    }

    /**
     * Draw details for armor.
     *
     * @param armor The armor to draw details for.
     */
    public static void drawArmorInfo(Armor armor) {
        ItemRendering.drawEquipmentName(armor);
        IkGui.text("Armor Type: " + armor.getArmorType().toString());
        ItemRendering.drawEquipmentInfo(armor);
    }

    /**
     * Draw details for armor template.
     *
     * @param armor The armor template to draw details for.
     */
    public static void drawArmorTemplateInfo(ArmorTemplate armor) {
        ItemRendering.drawEquipmentTemplateName(armor);
        IkGui.text("Armor Type: " + armor.getArmorType().toString());
        ItemRendering.drawEquipmentTemplateInfo(armor);
    }

    /**
     * Draw component information.
     *
     * @param component The component to draw details for.
     */
    public static void drawComponentInfo(Component component) {
        ItemRendering.drawName(component);
        IkGui.text("Component Type: " + component.getComponentType().toString());
        ItemRendering.drawItemStats(component.getItemStats());
        IkGui.text("Can be applied to: ");
        for (ItemType type : component.getItemCriteria().getItemTypes()) {
            IkGui.bulletText(type.toString());
            switch (type) {
                case ACCESSORY:
                    for (AccessoryType subType : component.getItemCriteria().getAccessoryTypes()) {
                        IkGui.setCursorPosX(
                                IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
                        IkGui.bulletText(subType.toString());
                    }
                    break;
                case ARMOR:
                    for (ArmorType subType : component.getItemCriteria().getArmorTypes()) {
                        IkGui.setCursorPosX(
                                IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
                        IkGui.bulletText(subType.toString());
                    }
                    break;
                case WEAPON:
                    for (WeaponType subType : component.getItemCriteria().getWeaponTypes()) {
                        IkGui.setCursorPosX(
                                IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
                        IkGui.bulletText(subType.toString());
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
        IkGui.text("Consumable type: " + consumable.getConsumableType().toString());
    }

    /**
     * Draw details for equipment.
     *
     * @param equipment The equipment to draw details for.
     */
    public static void drawEquipmentInfo(Equipment equipment) {
        IkGui.text("Item Level: " + equipment.getItemLevel());
        IkGui.text("Quality: ");
        IkGui.sameLine();
        IkGui.textColored(
                ItemRendering.getQualityColor(equipment.getQuality()),
                equipment.getQuality().toString());
        IkGui.text("Level Requirement: " + equipment.getLevelRequirement());
        IkGui.text("Attribute Requirements:");
        for (AttributeModifier mod : equipment.getAttributeRequirements()) {
            IkGui.bulletText(ItemRendering.modifierText(mod));
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
        IkGui.text("Name: ");
        Affix prefix = equipment.getPrefix();
        if (prefix != null) {
            IkGui.sameLine();
            IkGui.textColored(
                    ItemRendering.getQualityColor(prefix.getQuality()), prefix.getID() + " ");
        }
        IkGui.sameLine();
        IkGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()), equipment.getID());
        Affix suffix = equipment.getSuffix();
        if (suffix != null) {
            IkGui.sameLine();
            IkGui.textColored(
                    ItemRendering.getQualityColor(suffix.getQuality()), " " + suffix.getID());
        }
    }

    /**
     * Draw details for equipment template.
     *
     * @param equipment The equipment to draw details for.
     */
    public static void drawEquipmentTemplateInfo(EquipmentTemplate equipment) {
        IkGui.text("Item Level: " + equipment.getItemLevel());
        IkGui.text("Quality: ");
        IkGui.sameLine();
        IkGui.textColored(
                ItemRendering.getQualityColor(equipment.getQuality()),
                equipment.getQuality().toString());
        IkGui.text("Level Requirement: " + equipment.getLevelRequirement());
        IkGui.text("Attribute Requirements:");
        for (AttributeModifierTemplate mod : equipment.getAttributeRequirements()) {
            IkGui.bulletText(ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
        }
        ItemRendering.drawItemStatsTemplate(equipment.getItemStatsTemplate());
    }

    /**
     * Draw a name including prefixes and affixes.
     *
     * @param equipment The equipment template to draw a name for.
     */
    private static void drawEquipmentTemplateName(EquipmentTemplate equipment) {
        IkGui.text("Name: ");
        IkGui.textColored(ItemRendering.getQualityColor(equipment.getQuality()), equipment.getID());
    }

    /**
     * Draw item criteria, used as part of components/affixes.
     *
     * @param itemCriteria The item criteria to draw.
     */
    private static void drawItemCriteria(ItemCriteria itemCriteria) {
        IkGui.text("Item Criteria");

        IkGui.bulletText("Item Types");
        for (ItemType type : itemCriteria.getItemTypes()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(type.toString());

            if (type == ItemType.ACCESSORY) {
                for (AccessoryType accessory : itemCriteria.getAccessoryTypes()) {
                    IkGui.setCursorPosX(
                            IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing() * 2);
                    IkGui.bulletText(accessory.toString());
                }
            } else if (type == ItemType.ARMOR) {
                for (ArmorType armor : itemCriteria.getArmorTypes()) {
                    IkGui.setCursorPosX(
                            IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing() * 2);
                    IkGui.bulletText(armor.toString());
                }
            } else if (type == ItemType.WEAPON) {
                for (WeaponType weapon : itemCriteria.getWeaponTypes()) {
                    IkGui.setCursorPosX(
                            IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing() * 2);
                    IkGui.bulletText(weapon.toString());
                }
            }
        }
    }

    /**
     * Draw item stats, used as part of drawing item details.
     *
     * @param itemStats The item stats to draw.
     */
    public static void drawItemStats(ItemStats itemStats) {
        IkGui.text("Item Stats");
        IkGui.bulletText("Damage Buffs");
        for (DamageModifier mod : itemStats.getDamageBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod));
        }
        IkGui.bulletText("Resistance Buffs");
        for (DamageModifier mod : itemStats.getResistanceBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod));
        }
        IkGui.bulletText("Attribute Buffs");
        for (AttributeModifier mod : itemStats.getAttributeBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod));
        }
    }

    /**
     * Draw item stats template, used as part of drawing item details.
     *
     * @param itemStats The item stats template to draw.
     */
    public static void drawItemStatsTemplate(ItemStatsTemplate itemStats) {
        IkGui.text("Item Stats");
        IkGui.bulletText("Damage Buffs");
        for (DamageModifierTemplate mod : itemStats.getDamageBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
        }
        IkGui.bulletText("Resistance Buffs");
        for (DamageModifierTemplate mod : itemStats.getResistanceBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
        }
        IkGui.bulletText("Attribute Buffs");
        for (AttributeModifierTemplate mod : itemStats.getAttributeBuffs()) {
            IkGui.setCursorPosX(IkGui.getCursorPosX() + IkGui.getTreeNodeToLabelSpacing());
            IkGui.bulletText(ItemRendering.modifierText(mod) + " +/- " + mod.getVariance());
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
        IkGui.text("Name: ");
        IkGui.sameLine();
        IkGui.textColored(ItemRendering.getQualityColor(item.getQuality()), item.getID());
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
        IkGui.text("Weapon Type: " + weapon.getWeaponType().toString());
        IkGui.text("Damage: " + weapon.getMinDamage() + "-" + weapon.getMaxDamage());
        ItemRendering.drawEquipmentInfo(weapon);
    }

    /**
     * Draw weapon info for the provided weapon template.
     *
     * @param weapon The weapon template to draw.
     */
    public static void drawWeaponTemplateInfo(WeaponTemplate weapon) {
        ItemRendering.drawEquipmentTemplateName(weapon);
        IkGui.text("Weapon Type: " + weapon.getWeaponType().toString());
        IkGui.text("Damage: " + weapon.getMinDamage() + "-" + weapon.getMaxDamage());
        ItemRendering.drawEquipmentTemplateInfo(weapon);
    }

    /**
     * Return an integer color for the given quality.
     *
     * @param quality The quality.
     * @return The associated color for names or borders.
     */
    public static int getQualityColor(Quality quality) {
        if (quality == null) {
            return ItemRendering.COLOR_COMMON;
        }
        switch (quality) {
            case COMMON:
                return ItemRendering.COLOR_COMMON;
            case EPIC:
                return ItemRendering.COLOR_EPIC;
            case LEGENDARY:
                return ItemRendering.COLOR_LEGENDARY;
            case MAGIC:
                return ItemRendering.COLOR_MAGIC;
            case RARE:
                return ItemRendering.COLOR_RARE;
            case TRASH:
            default:
                return ItemRendering.COLOR_TRASH;
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
