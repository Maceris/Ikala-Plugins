package com.ikalagaming.rpg.scripting;

/**
 * A token produced by the lexer.
 * 
 * @author Ches Burks
 *
 * @param type The type of token.
 * @param lexeme The string representation of the token in the source.
 * @param literal The literal value of the token.
 * @param line The line the token is found on.
 */
public record Token(TokenType type, String lexeme, Object literal, int line) {}
