package com.ikalagaming.rpg.scripting;

/**
 * The types of tokens that can be output by the lexer.
 *
 * @author Ches Burks
 */
public enum TokenType {
	// Single character tokens
	/**
	 * "*"
	 */
	ASTERISK,
	/**
	 * ":" for labels.
	 */
	COLON,
	/**
	 * ","
	 */
	COMMA,
	/**
	 * "=" for assignment.
	 *
	 * @see #DOUBLE_EQUAL
	 */
	EQUAL,
	/**
	 * "/"
	 */
	FORWARD_SLASH,
	/**
	 * ">"
	 */
	GREATER,
	/**
	 * "{"
	 */
	LEFT_BRACE,
	/**
	 * "["
	 */
	LEFT_BRACKET,
	/**
	 * "("
	 */
	LEFT_PARENTHESIS,
	/**
	 * "<"
	 */
	LESS,
	/**
	 * "-"
	 */
	MINUS,
	/**
	 * "!"
	 */
	NOT,
	/**
	 * "."
	 */
	PERIOD,
	/**
	 * "+"
	 */
	PLUS,
	/**
	 * "}"
	 */
	RIGHT_BRACE,
	/**
	 * "]"
	 */
	RIGHT_BRACKET,
	/**
	 * ")"
	 */
	RIGHT_PARENTHESIS,
	// Multiple character tokens
	/**
	 * "[]" for declaring an array.
	 */
	ARRAY_OPERATOR,
	/**
	 * "=="
	 *
	 * @see #EQUAL
	 */
	DOUBLE_EQUAL,
	/**
	 * ">="
	 */
	GREATER_EQUAL,
	/**
	 * "<="
	 */
	LESS_EQUAL,
	/**
	 * "!="
	 */
	NOT_EQUAL,
	// Keywords
	/**
	 * "and"
	 */
	AND,
	/**
	 * "call"
	 */
	CALL,
	/**
	 * "else"
	 */
	ELSE,
	/**
	 * "for"
	 */
	FOR,
	/**
	 * "goto"
	 */
	GOTO,
	/**
	 * "if"
	 */
	IF,
	/**
	 * "null"
	 */
	NULL,
	/**
	 * "or"
	 */
	OR,
	/**
	 * "return"
	 */
	RETURN,
	/**
	 * "while"
	 */
	WHILE,
	// Literals
	/**
	 * A Boolean value.
	 */
	CONSTANT_BOOL,
	/**
	 * A double-precision floating point value.
	 */
	CONSTANT_DOUBLE,
	/**
	 * An integral value.
	 */
	CONSTANT_INT,
	/**
	 * A string value.
	 */
	CONSTANT_STRING,
	// Identifiers
	/**
	 * A named identifier.
	 */
	IDENTIFIER,
	/**
	 * The end of a file.
	 */
	EOF
}
