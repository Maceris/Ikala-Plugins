package com.ikalagaming.rpg.scripting.ast;

import com.ikalagaming.rpg.scripting.Token;

import lombok.NonNull;

/**
 * A null value, which is technically constant but has no type.
 * 
 * @author Ches Burks
 *
 */
public class ConstantNull extends Constant {
	/**
	 * Create a new null constant, which has no value as the type is enough to
	 * know it is null.
	 * 
	 * @param token The token this represents.
	 */
	public ConstantNull(@NonNull Token token) {
		super(token);
	}
}
