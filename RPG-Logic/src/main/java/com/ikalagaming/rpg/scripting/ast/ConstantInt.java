package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.Getter;
import lombok.NonNull;

/**
 * A constant int.
 * 
 * @author Ches Burks
 *
 */
@Getter
public class ConstantInt extends Constant {
	/**
	 * The value of the int.
	 * 
	 * @return The parsed value.
	 */
	private int value;

	/**
	 * Create a new constant int.
	 * 
	 * @param token The token that this represents.
	 * @param value The parsed value from the token.
	 */
	public ConstantInt(@NonNull Token token, int value) {
		super(token);
		this.value = value;
	}
}
