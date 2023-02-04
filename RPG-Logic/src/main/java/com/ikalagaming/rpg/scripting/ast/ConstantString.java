package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.Getter;
import lombok.NonNull;

/**
 * A constant string.
 * 
 * @author Ches Burks
 *
 */
@Getter
public class ConstantString extends Constant {
	/**
	 * The value of the string.
	 * 
	 * @return the parsed value.
	 */
	private String value;

	/**
	 * Create a new constant string.
	 * 
	 * @param token The token that this represents.
	 * @param value The parsed value from the token.
	 */
	public ConstantString(@NonNull Token token, @NonNull String value) {
		super(token);
		this.value = value;
	}
}
