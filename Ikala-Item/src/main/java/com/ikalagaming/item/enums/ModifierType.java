package com.ikalagaming.item.enums;

/**
 * What kind of modifier we have, whether it is a {@link #FLAT}
 * increase/decrease of the number by a certain amount, or a
 * {@link ModifierType#PERCENTAGE} change of the value.
 *
 * @author Ches Burks
 */
public enum ModifierType {
	/**
	 * A flat number increase or decrease of the base, like +3 or -1.
	 */
	FLAT,
	/**
	 * A percentage change of the base, like +10% or -50%.
	 */
	PERCENTAGE;
}
