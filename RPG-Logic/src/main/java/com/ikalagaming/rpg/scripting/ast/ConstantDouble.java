package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.Getter;
import lombok.NonNull;

/**
 * A constant double.
 * 
 * @author Ches Burks
 *
 */
@Getter
public class ConstantDouble extends Constant {
	/**
	 * The value of the double.
	 * 
	 * @return The parsed value.
	 */
	private double value;

	/**
	 * Create a new constant double.
	 * 
	 * @param token The token that this represents.
	 * @param value The parsed value from the token.
	 */
	public ConstantDouble(@NonNull Token token, double value) {
		super(token);
		this.value = value;
	}
}
