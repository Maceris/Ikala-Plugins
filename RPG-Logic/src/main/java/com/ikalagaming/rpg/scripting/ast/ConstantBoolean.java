package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.Getter;
import lombok.NonNull;

/**
 * A constant boolean.
 * 
 * @author Ches Burks
 *
 */
@Getter
public class ConstantBoolean extends Constant {
	/**
	 * The value of the boolean.
	 * 
	 * @return the parsed value.
	 */
	private boolean value;

	/**
	 * Create a new constant boolean.
	 * 
	 * @param token The token that this represents.
	 * @param value The parsed value from the token.
	 */
	public ConstantBoolean(@NonNull Token token, boolean value) {
		super(token);
		this.value = value;
	}
}
