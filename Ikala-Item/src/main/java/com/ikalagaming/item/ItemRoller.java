package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.ItemStatsTemplate;

import java.util.SplittableRandom;

/**
 * Used to generate random items based on a template.
 *
 * @author Ches Burks
 *
 */
public class ItemRoller {
	private static SplittableRandom rand = new SplittableRandom();

	/**
	 * Roll an accessory based on an accessory template.
	 *
	 * @param template The template we are generating an accessory from.
	 * @return The newly generated accessory.
	 */
	public Accessory rollAccessory(AccessoryTemplate template) {
		Accessory accessory = new Accessory();
		accessory.setAccessoryType(template.getAccessoryType());
		template.getAttributeRequirements()
			.forEach(modifier -> accessory.getAttributeRequirements()
				.add(this.rollAttributeModifier(modifier)));
		accessory.setID(template.getID());
		accessory.setItemLevel(template.getItemLevel());
		accessory
			.setItemStats(this.rollItemStats(template.getItemStatsTemplate()));
		accessory.setItemType(template.getItemType());
		accessory.setLevelRequirement(template.getLevelRequirement());
		accessory.setQuality(template.getQuality());
		return accessory;
	}

	/**
	 * Generate an amount +/- some variance. Variance should be between 0 and
	 * amount, inclusive, if you want it to behave as expected.
	 *
	 * @param amount The amount.
	 * @param variance The distance from the amount we can generate values
	 *            within.
	 * @return The random amount given the base amount and variance.
	 */
	private int rollAmount(final int amount, final int variance) {
		return amount + ItemRoller.rand.nextInt(2 * variance) - variance;
	}

	/**
	 * Roll an attribute modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated attribute modifier.
	 */
	private AttributeModifier
		rollAttributeModifier(AttributeModifierTemplate template) {

		AttributeModifier modifier = new AttributeModifier();
		modifier.setAmount(
			this.rollAmount(template.getAmount(), template.getVariance()));
		modifier.setAttribute(template.getAttribute());
		modifier.setType(template.getType());
		return modifier;
	}

	/**
	 * Roll a damage modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated damage modifier.
	 */
	private DamageModifier rollDamageModifier(DamageModifierTemplate template) {
		DamageModifier modifier = new DamageModifier();
		modifier.setAmount(
			this.rollAmount(template.getAmount(), template.getVariance()));
		modifier.setDamageType(template.getDamageType());
		modifier.setType(template.getType());
		return template;
	}

	/**
	 * Roll item stats based on an item stats template.
	 *
	 * @param template The template we are generating item stats from.
	 * @return The newly generated item stats block.
	 */
	private ItemStats rollItemStats(ItemStatsTemplate template) {
		ItemStats stats = new ItemStats();
		template.getAttributeBuffs().forEach(modifier -> stats
			.getAttributeBuffs().add(this.rollAttributeModifier(modifier)));
		template.getDamageBuffs().forEach(modifier -> stats.getDamageBuffs()
			.add(this.rollDamageModifier(modifier)));
		template.getResistanceBuffs().forEach(modifier -> stats
			.getResistanceBuffs().add(this.rollDamageModifier(modifier)));
		return stats;
	}

}
