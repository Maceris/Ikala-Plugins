package com.ikalagaming.rpg.scripting;

import lombok.extern.slf4j.Slf4j;

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
	 */
	private static boolean errorSeen;

	/**
	 * Report an error message.
	 * 
	 * @param line The line the error was found on.
	 * @param message The error message to report.
	 */
	static void error(int line, String message) {
		reportError(line, message);
	}

	/**
	 * Report an error and set a flag indicating we had an error.
	 * 
	 * @param line The line the error was found on.
	 * @param message The error message to report.
	 */
	private static void reportError(int line, String message) {
		log.warn("[line {}] Error: {}", line, message);
		errorSeen = true;
	}
}
