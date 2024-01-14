package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.persistence.AttributeModifierListConverter;
import com.ikalagaming.rpg.item.persistence.ItemStatsConverter;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

/**
 * An item that has some stats and requirements.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@MappedSuperclass
public class Equipment extends Item {
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
	 * The augment used on the equipment.
	 *
	 * @param augment The augment on the equipment.
	 * @return The augment on the equipment.
	 */
	@ManyToOne
	@JoinColumn(name = "AUGMENT")
	private Component augment;

	@Transient
	@EqualsAndHashCode.Exclude
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	private transient ItemStats combinedStats;

	/**
	 * The first gem slot for the equipment.
	 *
	 * @param gem1 The first gem attached to the equipment.
	 * @return The first gem attached to the equipment.
	 */
	@ManyToOne
	@JoinColumn(name = "GEM1")
	private Component gem1;

	/**
	 * The second gem slot for the equipment.
	 *
	 * @param gem2 The second gem attached to the equipment.
	 * @return The second gem attached to the equipment.
	 */
	@ManyToOne
	@JoinColumn(name = "GEM2")
	private Component gem2;

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
	 * The level requirement required for using this equipment.
	 *
	 * @param levelRequirement The level that is required in order to equip this
	 *            item.
	 * @return The level that is required in order to equip this item.
	 */
	@Column(name = "LEVEL_REQUIREMENT")
	private Integer levelRequirement;

	/**
	 * The prefix for this equipment.
	 *
	 * @param prefix The prefix for this item.
	 * @return The prefix for this item.
	 */
	@ManyToOne
	@JoinColumn(name = "PREFIX")
	private Affix prefix;

	/**
	 * The suffix for this equipment.
	 *
	 * @param suffix The prefix for this item.
	 * @return The suffix for this item.
	 */
	@ManyToOne
	@JoinColumn(name = "SUFFIX")
	private Affix suffix;

	/**
	 * A unique identifier to refer to a specific piece of equipment, since they
	 * are always unique and never stack.
	 */
	@Column(name = "UNIQUE_ID")
	@Id
	private UUID uniqueID = UUID.randomUUID();

	/**
	 * Go through a new set of stats and add them to the combined stats
	 * attribute buffs list.
	 *
	 * @param newStats The stats we are adding to this item.
	 */
	private void addAttributeBuffs(ItemStats newStats) {
		for (AttributeModifier newBuff : newStats.getAttributeBuffs()) {
			boolean matched = false;
			for (AttributeModifier existingBuff : this.combinedStats
				.getAttributeBuffs()) {
				if (existingBuff.getAttribute().equals(newBuff.getAttribute())
					&& existingBuff.getType().equals(newBuff.getType())) {
					existingBuff.setAmount(
						existingBuff.getAmount() + newBuff.getAmount());
					matched = true;
					break;
				}
			}
			if (!matched) {
				this.combinedStats.getAttributeBuffs().add(newBuff.copy());
			}
		}
	}

	/**
	 * Go through a new set of stats and add them to the combined stats damage
	 * buffs list.
	 *
	 * @param newStats The stats we are adding to this item.
	 */
	private void addDamageBuffs(ItemStats newStats) {
		for (DamageModifier newBuff : newStats.getDamageBuffs()) {
			boolean matched = false;
			for (DamageModifier existingBuff : this.combinedStats
				.getDamageBuffs()) {
				if (existingBuff.getDamageType().equals(newBuff.getDamageType())
					&& existingBuff.getType().equals(newBuff.getType())) {
					existingBuff.setAmount(
						existingBuff.getAmount() + newBuff.getAmount());
					matched = true;
					break;
				}
			}
			if (!matched) {
				this.combinedStats.getDamageBuffs().add(newBuff.copy());
			}
		}
	}

	/**
	 * Go through a new set of stats and add them to the combined stats
	 * resistance buffs list.
	 *
	 * @param newStats The stats we are adding to this item.
	 */
	private void addResistanceBuffs(ItemStats newStats) {
		for (DamageModifier newBuff : newStats.getResistanceBuffs()) {
			boolean matched = false;
			for (DamageModifier existingBuff : this.combinedStats
				.getResistanceBuffs()) {
				if (!(existingBuff.getDamageType()
					.equals(newBuff.getDamageType())
					&& existingBuff.getType().equals(newBuff.getType()))) {
					continue;
				}
				existingBuff
					.setAmount(existingBuff.getAmount() + newBuff.getAmount());
				matched = true;
			}
			if (!matched) {
				this.combinedStats.getResistanceBuffs().add(newBuff.copy());
			}
		}
	}

	/**
	 * Add the stats from the given affix to the combined stats.
	 *
	 * @param newStats The stats to add.
	 */
	private void addStats(@NonNull ItemStats newStats) {

		this.addAttributeBuffs(newStats);
		this.addDamageBuffs(newStats);
		this.addResistanceBuffs(newStats);
	}

	/**
	 * Return the combined stats that include all base item stats and affixes.
	 * Calculates it if it has not been calculated yet, but caches the result.
	 *
	 * @return The total item stats.
	 */
	public ItemStats getCombinedStats() {
		if (this.combinedStats != null) {
			return this.combinedStats;
		}
		this.combinedStats = new ItemStats();
		List<AttributeModifier> attributeBuffs =
			this.combinedStats.getAttributeBuffs();
		List<DamageModifier> damageBuffs = this.combinedStats.getDamageBuffs();
		List<DamageModifier> resistanceBuffs =
			this.combinedStats.getResistanceBuffs();

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

		if (this.prefix != null) {
			this.addStats(this.prefix.getItemStats());
		}
		if (this.suffix != null) {
			this.addStats(this.suffix.getItemStats());
		}
		if (this.gem1 != null) {
			this.addStats(this.gem1.getItemStats());
		}
		if (this.gem2 != null) {
			this.addStats(this.gem2.getItemStats());
		}
		if (this.augment != null) {
			this.addStats(this.augment.getItemStats());
		}

		return this.combinedStats;
	}

}
