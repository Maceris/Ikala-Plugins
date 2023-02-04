package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A constant value.
 * 
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public class Constant {
	/**
	 * Int, boolean, double, or string constant.
	 */
	protected Token token;

	/**
	 * Factory method for creating the appropriate constant sub-type based on
	 * the type of the token.
	 * 
	 * @param token The token we want to represent.
	 * @return The appropriate constant class.
	 * @throws IllegalArgumentException If the token is not a valid constant
	 *             type.
	 */
	public static Constant from(Token token) {
		switch (token.type()) {
			case CONSTANT_BOOL:
				return new ConstantBoolean(token, (Boolean) token.literal());
			case CONSTANT_DOUBLE:
				return new ConstantDouble(token, (Double) token.literal());
			case CONSTANT_INT:
				return new ConstantInt(token, (Integer) token.literal());
			case CONSTANT_STRING:
				return new ConstantString(token, (String) token.literal());
			case NULL:
				return new ConstantNull(token);
			default:
				throw new IllegalArgumentException(
					"Unknown constant type " + token.type());
		}
	}
}
