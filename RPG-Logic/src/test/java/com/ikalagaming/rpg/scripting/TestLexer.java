package com.ikalagaming.rpg.scripting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the lexer.
 * 
 * @author Ches Burks
 *
 */
class TestLexer {

	/**
	 * Create a sample program to test all the token types get read correctly.
	 */
	@Test
	public void testTokenTypes() {
		StringBuilder programBuilder = new StringBuilder();

		List<TokenType> expected = new ArrayList<>();

		/**
		 * We slap a lot of these tokens right next to each other, to make sure
		 * that works, but be careful reordering things, or we might
		 * accidentally create the wrong operator.
		 */
		// simple operators
		programBuilder.append('*');
		expected.add(TokenType.ASTERISK);
		programBuilder.append(',');
		expected.add(TokenType.COMMA);
		programBuilder.append('=');
		expected.add(TokenType.EQUAL);
		programBuilder.append('/');
		expected.add(TokenType.FORWARD_SLASH);
		programBuilder.append('>');
		expected.add(TokenType.GREATER);
		programBuilder.append('{');
		expected.add(TokenType.LEFT_BRACE);
		programBuilder.append('[');
		expected.add(TokenType.LEFT_BRACKET);
		programBuilder.append('(');
		expected.add(TokenType.LEFT_PARENTHESIS);
		programBuilder.append('<');
		expected.add(TokenType.LESS);
		programBuilder.append('-');
		expected.add(TokenType.MINUS);
		programBuilder.append('!');
		expected.add(TokenType.NOT);
		programBuilder.append('.');
		expected.add(TokenType.PERIOD);
		programBuilder.append('+');
		expected.add(TokenType.PLUS);
		programBuilder.append('}');
		expected.add(TokenType.RIGHT_BRACE);
		programBuilder.append(']');
		expected.add(TokenType.RIGHT_BRACKET);
		programBuilder.append(')');
		expected.add(TokenType.RIGHT_PARENTHESIS);

		// multiple character tokens
		programBuilder.append("[]");
		expected.add(TokenType.ARRAY_OPERATOR);
		programBuilder.append("==");
		expected.add(TokenType.DOUBLE_EQUAL);
		programBuilder.append(">=");
		expected.add(TokenType.GREATER_EQUAL);
		programBuilder.append("<=");
		expected.add(TokenType.LESS_EQUAL);
		programBuilder.append("!=");
		expected.add(TokenType.NOT_EQUAL);

		// keywords, spaces added so they're not seen as one long variable
		programBuilder.append("and ");
		expected.add(TokenType.AND);
		programBuilder.append("call ");
		expected.add(TokenType.CALL);
		programBuilder.append("else ");
		expected.add(TokenType.ELSE);
		programBuilder.append("for ");
		expected.add(TokenType.FOR);
		programBuilder.append("goto ");
		expected.add(TokenType.GOTO);
		programBuilder.append("if ");
		expected.add(TokenType.IF);
		programBuilder.append("null ");
		expected.add(TokenType.NULL);
		programBuilder.append("or ");
		expected.add(TokenType.OR);
		programBuilder.append("return ");
		expected.add(TokenType.RETURN);
		programBuilder.append("while ");
		expected.add(TokenType.WHILE);

		// constants
		programBuilder.append("true ");
		expected.add(TokenType.CONSTANT_BOOL);
		programBuilder.append("false ");
		expected.add(TokenType.CONSTANT_BOOL);
		programBuilder.append("0 ");
		expected.add(TokenType.CONSTANT_INT);
		programBuilder.append("91234 ");
		expected.add(TokenType.CONSTANT_INT);
		programBuilder.append("1.0 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("113.342 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("0.1e4 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("1e12 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("1e-12 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("2.5e-4 ");
		expected.add(TokenType.CONSTANT_DOUBLE);
		programBuilder.append("\" test!@#$%^\n\r\n&*(\n)3';:{}[]\\\" ");
		expected.add(TokenType.CONSTANT_STRING);

		// identifiers
		programBuilder.append("foobar ");
		expected.add(TokenType.IDENTIFIER);
		programBuilder.append("System ");
		expected.add(TokenType.IDENTIFIER);
		programBuilder.append("_temp0 ");
		expected.add(TokenType.IDENTIFIER);

		expected.add(TokenType.EOF);

		String program = programBuilder.toString();
		Lexer lex = new Lexer(program);
		List<Token> actual = lex.scanTokens();

		Assertions.assertEquals(expected.size(), actual.size());

		for (int i = 0; i < actual.size(); ++i) {
			Assertions.assertEquals(expected.get(i), actual.get(i).type());
		}
	}

}
