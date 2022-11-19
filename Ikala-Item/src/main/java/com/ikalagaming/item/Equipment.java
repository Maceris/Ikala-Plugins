package com.ikalagaming.item;

import com.ikalagaming.item.persistence.AffixListConverter;
import com.ikalagaming.item.persistence.AttributeModifierListConverter;
import com.ikalagaming.item.persistence.ItemStatsConverter;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

/**
 * An item that has some stats and requirements.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@MappedSuperclass
public class Equipment extends Item {
	/**
	 * The level requirement required for using this equipment.
	 *
	 * @param levelRequirement The level that is required in order to equip this
	 *            item.
	 * @return The level that is required in order to equip this item.
	 */
	@Column(name = "LEVEL_REQUIREMENT")
	private Integer levelRequirement;

	/**
	 * The minimum attribute values that are required for using this equipment.
	 *
	 * @param attributeRequirements The minimum attribute values that are
	 *            required for using this equipment.
	 * @return The minimum attribute values that are required for using this
	 *         equipment.
	 */
	@Column(name = "ATTRIBUTE_REQUIREMENTS")
	@Convert(converter = AttributeModifierListConverter.class)
	private List<AttributeModifier> attributeRequirements = new ArrayList<>();

	/**
	 * Stat bonuses provided by the item.
	 *
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	@Column(name = "ITEM_STATS")
	@Convert(converter = ItemStatsConverter.class)
	private ItemStats itemStats = new ItemStats();

	/**
	 * The affixes for this equipment.
	 *
	 * @param affixes The affixes for this item.
	 * @return The affixes for this item.
	 */
	@Column(name = "AFFIXES")
	@Convert(converter = AffixListConverter.class)
	private List<Affix> affixes = new ArrayList<>();

	@Transient
	@EqualsAndHashCode.Exclude
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	private transient ItemStats combinedStats;

	/**
	 * Return the combined stats that include all base item stats and affixes.
	 * Calculates it if it has not been calculated yet, but caches the result.
	 * 
	 * @return The total item stats.
	 */
	public ItemStats getCombinedStats() {
		if (combinedStats != null) {
			return combinedStats;
		}
		combinedStats = new ItemStats();
		List<AttributeModifier> attributeBuffs =
			combinedStats.getAttributeBuffs();
		List<DamageModifier> damageBuffs = combinedStats.getDamageBuffs();
		List<DamageModifier> resistanceBuffs =
			combinedStats.getResistanceBuffs();

		for (AttributeModifier modifier : this.getItemStats()
			.getAttributeBuffs()) {
			attributeBuffs.add(modifier.copy());
		}
		for (DamageModifier modifier : this.getItemStats().getDamageBuffs()) {
			damageBuffs.add(modifier.copy());
		}
		for (DamageModifier modifier : this.getItemStats()
			.getResistanceBuffs()) {
			resistanceBuffs.add(modifier.copy());
		}

		for (Affix affix : this.getAffixes()) {
			ItemStats affixStats = affix.getItemStats();

			for (AttributeModifier newBuff : affixStats.getAttributeBuffs()) {
				boolean matched = false;
				for (AttributeModifier existingBuff : attributeBuffs) {
					if (existingBuff.getAttribute()
						.equals(newBuff.getAttribute())
						&& existingBuff.getType().equals(newBuff.getType())) {
						existingBuff.setAmount(
							existingBuff.getAmount() + newBuff.getAmount());
						matched = true;
						break;
					}
				}
				if (!matched) {
					attributeBuffs.add(newBuff.copy());
				}
			}

			for (DamageModifier newBuff : affixStats.getDamageBuffs()) {
				boolean matched = false;
				for (DamageModifier existingBuff : damageBuffs) {
					if (existingBuff.getDamageType()
						.equals(newBuff.getDamageType())
						&& existingBuff.getType().equals(newBuff.getType())) {
						existingBuff.setAmount(
							existingBuff.getAmount() + newBuff.getAmount());
						matched = true;
						break;
					}
				}
				if (!matched) {
					damageBuffs.add(newBuff.copy());
				}
			}

			for (DamageModifier newBuff : affixStats.getResistanceBuffs()) {
				boolean matched = false;
				for (DamageModifier existingBuff : resistanceBuffs) {
					if (!(existingBuff.getDamageType()
						.equals(newBuff.getDamageType())
						&& existingBuff.getType().equals(newBuff.getType()))) {
						continue;
					}
					existingBuff.setAmount(
						existingBuff.getAmount() + newBuff.getAmount());
					matched = true;
				}
				if (!matched) {
					resistanceBuffs.add(newBuff.copy());
				}
			}
		}
		return combinedStats;
	}

	/**
	 * Construct a new equipment item.
	 */
	public Equipment() {}

}
