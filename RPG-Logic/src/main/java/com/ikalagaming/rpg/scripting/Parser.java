package com.ikalagaming.rpg.scripting;

import com.ikalagaming.rpg.scripting.ast.Constant;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A parser for our scripting language.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Parser {

	/**
	 * Whether we saw an error while parsing the latest file.
	 *
	 * @return If there have been errors while lexing or parsing.
	 */
	@Getter
	private static boolean errorSeen;

	/**
	 * Report an error message.
	 *
	 * @param line The line the error was found on.
	 * @param message The error message to report.
	 */
	static void error(int line, String message) {
		Parser.reportError(line, message);
	}

	/**
	 * Report an error and set a flag indicating we had an error.
	 *
	 * @param line The line the error was found on.
	 * @param message The error message to report.
	 */
	private static void reportError(int line, String message) {
		Parser.log.warn("[line {}] Error: {}", line, message);
		Parser.errorSeen = true;
	}

	private final List<Token> input;

	private int currentPosition;

	private Token current;

	/**
	 * Create a parser for the given tokens.
	 *
	 * @param tokens The tokens to parse into an abstract syntax tree.
	 */
	public Parser(List<Token> tokens) {
		this.input = tokens;
	}

	/**
	 * Read in a constant.
	 *
	 * @return The constant node.
	 * @throws ParseException If the token is not a constant type.
	 */
	public Constant constant() {
		if (this.isType(TokenType.CONSTANT_INT)
			|| this.isType(TokenType.CONSTANT_BOOL)
			|| this.isType(TokenType.CONSTANT_DOUBLE)
			|| this.isType(TokenType.CONSTANT_STRING)
			|| this.isType(TokenType.NULL)) {
			return Constant.from(this.current);
		}
		Parser.reportError(this.current.line(), String
			.format("Expected a constant but found %s", this.current.type()));
		throw new ParseException();
	}

	/**
	 * Look for the given token type and throw an error if it was not found.
	 *
	 * @param t The type to look for.
	 * @return True if it was found, false if not.
	 */
	private boolean expect(TokenType t) {
		if (this.isType(t)) {
			return true;
		}
		Parser.reportError(this.current.line(),
			String.format("Expected %s but found %s", t, this.current.type()));
		return false;
	}

	/**
	 * Check if the current token is the next type.
	 *
	 * @param t The type we are looking for.
	 * @return True if the current type is as we expect.
	 */
	private boolean isType(TokenType t) {
		if (this.current.type() == t) {
			return true;
		}
		return false;
	}

	private boolean nextSymbol() {
		if (this.currentPosition >= this.input.size() - 1) {
			return false;
		}

		this.current = this.input.get(this.currentPosition++);
		return true;
	}

	/**
	 * Parse the tokens.
	 */
	public void parse() {
		this.nextSymbol();

		this.expect(TokenType.EOF);
	}
}
