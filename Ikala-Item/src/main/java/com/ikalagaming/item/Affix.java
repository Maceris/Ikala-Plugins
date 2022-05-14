package com.ikalagaming.item;

import com.ikalagaming.item.enums.AffixType;
import com.ikalagaming.item.enums.Quality;

import lombok.Getter;
import lombok.Setter;

/**
 * A modifier for a weapon that grants stats and is shown as a prefix or suffix.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class Affix {
	/**
	 * The unique name of the affix, which should follow a standard format and
	 * be human readable but unlocalized.
	 *
	 * @param ID The name of the affix in the database/code level.
	 * @return The name of the affix in the database/code level.
	 */
	private String ID;

	/**
	 * What kind of affix this is, like a prefix or suffix.
	 *
	 * @param affixType The type of affix.
	 * @return The type of affix.
	 */
	private AffixType affixType;

	/**
	 * The types of items this affix can be applied to.
	 *
	 * @param itemCriteria The types of items this affix can be applied to.
	 * @return The types of items this affix can be applied to.
	 */
	private ItemCriteria itemCriteria = new ItemCriteria();

	/**
	 * The minimum quality of items this can appear on.
	 *
	 * @param quality The minimum quality of items this can appear on.
	 * @return The minimum quality of items this can appear on.
	 */
	private Quality quality;

	/**
	 * Stat bonuses provided by the affix.
	 *
	 * @param itemStats The stat bonuses provided by the affix.
	 * @return The stat bonuses provided by the affix.
	 */
	private ItemStats itemStats = new ItemStats();
}
