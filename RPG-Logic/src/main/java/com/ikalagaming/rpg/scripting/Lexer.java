package com.ikalagaming.rpg.scripting;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits input into tokens that can be understood by the parser.
 *
 * @author Ches Burks
 *
 */
public class Lexer {

	private static final Map<String, TokenType> keywords = new HashMap<>();

	static {
		Lexer.keywords.put("and", TokenType.AND);
		Lexer.keywords.put("call", TokenType.CALL);
		Lexer.keywords.put("else", TokenType.ELSE);
		Lexer.keywords.put("for", TokenType.FOR);
		Lexer.keywords.put("goto", TokenType.GOTO);
		Lexer.keywords.put("if", TokenType.IF);
		Lexer.keywords.put("null", TokenType.NULL);
		Lexer.keywords.put("or", TokenType.OR);
		Lexer.keywords.put("return", TokenType.RETURN);
		Lexer.keywords.put("while", TokenType.WHILE);

		Lexer.keywords.put("true", TokenType.CONSTANT_BOOL);
		Lexer.keywords.put("false", TokenType.CONSTANT_BOOL);
	}

	/**
	 * Check if a character is in [a-zA-Z_].
	 *
	 * @param c The character.
	 * @return Whether the character is a letter or underscore.
	 */
	private static boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	/**
	 * Check if a character is in [a-zA-Z0-9_].
	 *
	 * @param c The character.
	 * @return Whether the character is a number, letter, or underscore.
	 */
	private static boolean isAlphaNumeric(char c) {
		return Lexer.isAlpha(c) || Lexer.isDigit(c);
	}

	/**
	 * Checks if a character is a numeric digit.
	 *
	 * @param c The character we are looking at.
	 * @return Whether that character is a digit.
	 */
	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private List<Token> tokens;
	private final String source;

	private int start;

	private int current;

	private int line;

	/**
	 * Create a new lexer for the given source string.
	 *
	 * @param source The source code to lex.
	 */
	public Lexer(@NonNull String source) {
		this.source = source;
		this.start = 0;
		this.current = 0;
		this.line = 1;
		this.tokens = new ArrayList<>();
	}

	/**
	 * Add a token to the list without any literal value.
	 *
	 * @param type The type of token.
	 */
	private void addToken(TokenType type) {
		this.addToken(type, null);
	}

	/**
	 * Add a token to the list with a literal value.
	 *
	 * @param type The type of token.
	 * @param literal The literal value of the token.
	 */
	private void addToken(TokenType type, Object literal) {
		final String text = this.source.substring(this.start, this.current);
		this.tokens.add(new Token(type, text, literal, this.line));
	}

	/**
	 * Return the character at the current position and then advance the
	 * position by one character.
	 *
	 * @return The character at the current position.
	 */
	private char advance() {
		return this.source.charAt(this.current++);
	}

	/**
	 * Read in an identifier. Reserved identifiers just have their type,
	 * booleans also have their parsed Boolean object value, and identifiers
	 * their string value.
	 */
	private void identifier() {
		while (Lexer.isAlphaNumeric(this.peek())) {
			this.advance();
		}
		String text = this.source.substring(this.start, this.current);
		TokenType type =
			Lexer.keywords.getOrDefault(text, TokenType.IDENTIFIER);

		if (type == TokenType.IDENTIFIER) {
			this.addToken(type, text);
		}
		else if (type == TokenType.CONSTANT_BOOL) {
			this.addToken(type, Boolean.parseBoolean(text));
		}
		else {
			this.addToken(type);
		}
	}

	/**
	 * If we are at the end of the source input.
	 *
	 * @return Whether we have consumed all the characters.
	 */
	private boolean isAtEnd() {
		return this.current >= this.source.length();
	}

	/**
	 * Check if the next character is the expected one, and consume it if so.
	 *
	 * @param expected The character we expect to see next.
	 * @return True if we matched the character, false if we did not find that
	 *         character next.
	 */
	private boolean match(char expected) {
		if (this.isAtEnd() || (this.source.charAt(this.current) != expected)) {
			return false;
		}
		++this.current;
		return true;
	}

	/**
	 * Read in numeric values, namely integers and doubles.
	 */
	private void number() {
		boolean isFloat = false;

		while (Lexer.isDigit(this.peek())) {
			this.advance();
		}
		// Consume the decimal part
		if (this.peek() == '.' && Lexer.isDigit(this.peekAhead(1))) {
			isFloat = true;
			// consume the '.'
			this.advance();
			while (Lexer.isDigit(this.peek())) {
				this.advance();
			}
		}
		// consume exponents
		if (this.peek() == 'e' || this.peek() == 'E') {
			isFloat = true;
			if (this.peekAhead(1) == '-' && Lexer.isDigit(this.peekAhead(2))) {
				// Consume the 'e', because we know it will parse as a number
				this.advance();
				/*
				 * we don't consume the '-' on purpose, because the digit check
				 * below assumes what is now a '-' is actually a 'e' before a
				 * number.
				 */
			}

			if (Lexer.isDigit(this.peekAhead(1))) {
				// consume the 'e' (or '-' in the case of "e-")
				this.advance();
				while (Lexer.isDigit(this.peek())) {
					this.advance();
				}
			}
		}

		if (isFloat) {
			// we hit something float-ish like decimals or exponents
			this.addToken(TokenType.CONSTANT_DOUBLE, Double
				.parseDouble(this.source.substring(this.start, this.current)));
		}
		else {
			this.addToken(TokenType.CONSTANT_INT, Integer
				.parseInt(this.source.substring(this.start, this.current)));
		}
	}

	/**
	 * Peek at the next character without consuming it, returning it or '\0' if
	 * are at the end of the file.
	 *
	 * @return The next character, if it exists. Otherwise, '\0' if we are at
	 *         the end.
	 */
	private char peek() {
		if (this.isAtEnd()) {
			return '\0';
		}
		return this.source.charAt(this.current);
	}

	/**
	 * Peek at the character n ahead without consuming it, returning it or '\0'
	 * if are at the end of the file.
	 *
	 * @param ahead How many characters ahead to look.
	 * @return The character n ahead of current, if it exists. Otherwise, '\0'
	 *         if we are at the end.
	 */
	private char peekAhead(int ahead) {
		if (this.current + ahead >= this.source.length()) {
			return '\0';
		}
		return this.source.charAt(this.current + ahead);
	}

	/**
	 * Scan a single token.
	 */
	private void scanToken() {
		char c = this.advance();
		switch (c) {
			case '(':
				this.addToken(TokenType.LEFT_PARENTHESIS);
				break;
			case ')':
				this.addToken(TokenType.RIGHT_PARENTHESIS);
				break;
			case '{':
				this.addToken(TokenType.LEFT_BRACE);
				break;
			case '}':
				this.addToken(TokenType.RIGHT_BRACE);
				break;
			case ',':
				this.addToken(TokenType.COMMA);
				break;
			case '.':
				this.addToken(TokenType.PERIOD);
				break;
			case '-':
				this.addToken(TokenType.MINUS);
				break;
			case '+':
				this.addToken(TokenType.PLUS);
				break;
			case '*':
				this.addToken(TokenType.ASTERISK);
				break;
			case ':':
				this.addToken(TokenType.COLON);
				break;
			case '!':
				this.addToken(
					this.match('=') ? TokenType.NOT_EQUAL : TokenType.NOT);
				break;
			case '=':
				this.addToken(
					this.match('=') ? TokenType.DOUBLE_EQUAL : TokenType.EQUAL);
				break;
			case '<':
				this.addToken(
					this.match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
				break;
			case '>':
				this.addToken(this.match('=') ? TokenType.GREATER_EQUAL
					: TokenType.GREATER);
				break;
			case '[':
				this.addToken(this.match(']') ? TokenType.ARRAY_OPERATOR
					: TokenType.LEFT_BRACKET);
				break;
			case ']':
				this.addToken(TokenType.RIGHT_BRACKET);
				break;
			case '/':
				if (this.match('/')) {
					// single line comment
					while (this.peek() != '\n' && !this.isAtEnd()) {
						this.advance();
					}
				}
				else if (this.match('*')) {
					// multi-line comment
					while (!this.isAtEnd()) {
						if (this.match('*') && this.match('/')) {
							// If we hit the end of the comment, bail
							break;
						}
						// Otherwise it was a random *, keep skipping
						this.advance();
					}
				}
				else {
					this.addToken(TokenType.FORWARD_SLASH);
				}
				break;
			case ' ':
			case '\t':
			case '\r':
				// Skip whitespace
				break;
			case '\n':
				++this.line;
				break;
			case '"':
				this.string();
				break;
			default:
				if (Lexer.isDigit(c)) {
					this.number();
				}
				else if (Lexer.isAlpha(c)) {
					this.identifier();
				}
				else {
					// TODO group multiple invalid characters into one error
					Parser.error(this.line,
						String.format("Unexpected character '%c'.", c));
				}
				break;
		}
	}

	/**
	 * Scan all the tokens and return the resulting list of token objects.
	 *
	 * @return The tokens resulting from lexing the code.
	 */
	public List<Token> scanTokens() {
		while (!this.isAtEnd()) {
			this.start = this.current;
			this.scanToken();
		}
		this.addToken(TokenType.EOF);
		return this.tokens;
	}

	/**
	 * Read in a (potentially multi-line) string.
	 */
	private void string() {
		while (this.peek() != '"' && !this.isAtEnd()) {
			if (this.peek() == '\n') {
				++this.line;
			}
			this.advance();
		}
		if (this.isAtEnd()) {
			Parser.error(this.line, "Unterminated string");
			return;
		}
		// The closing '"'
		this.advance();

		String value = this.source.substring(this.start + 1, this.current - 1);
		this.addToken(TokenType.CONSTANT_STRING, value);
	}

}
