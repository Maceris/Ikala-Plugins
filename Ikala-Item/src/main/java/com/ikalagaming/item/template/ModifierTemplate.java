package com.ikalagaming.item.template;

import com.ikalagaming.item.ModifierType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks modification of stats, damage, and similar attributes.
 * 
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class ModifierTemplate {
	/**
	 * The ID of the modifier this applies to, can be a damage type, character
	 * stat, etc.
	 * 
	 * @param modifierTargetID The unique ID of the type of thing this modifies.
	 * @return The unique ID of the type of thing this modifies.
	 */
	private Integer modifierTargetID;
	/**
	 * How this modifier affects the base value, such as a flat change,
	 * percentage difference, or a range of possible values.
	 * 
	 * @param type The way that this affects the base value of the stat/concept
	 *            it modifies.
	 * @return The way that this affects the base value of the stat/concept it
	 *         modifies.
	 */
	private ModifierType type;
	/**
	 * Used as the base amount for {@link ModifierType#FLAT} and
	 * {@link ModifierType#PERCENTAGE}, before variance is applied.
	 * 
	 * @param baseAmount The new base amount.
	 * @return The base amount.
	 */
	private Integer baseAmount;
	/**
	 * How far away from the base value we can go, as an integer percentage. For
	 * example, a value of 10 would mean we can generate values +/- 10% from the
	 * base.
	 * 
	 * @param variance The percentage variance.
	 * @return The percentage variance in the base amount.
	 */
	private Integer variance;

}
