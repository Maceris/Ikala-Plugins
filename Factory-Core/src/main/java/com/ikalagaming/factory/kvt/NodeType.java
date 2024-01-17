package com.ikalagaming.factory.kvt;

/**
 * The types of tags that we can have.
 *
 * @author Ches Burks
 *
 */
public enum NodeType {
	/**
	 * Byte value of 0 or 1 that represents true or false.
	 */
	BOOLEAN,
	/**
	 * A signed 8-bit integer, ranging between -128 to 127 (inclusive).
	 */
	BYTE,
	/**
	 * Ordered list of signed 8-bit integers.
	 */
	BYTE_ARRAY,
	/**
	 * A 64-bit, double-precision floating-point, ranging between -1.7E+308 to
	 * +1.7E+308.
	 */
	DOUBLE,
	/**
	 * Ordered list of doubles.
	 */
	DOUBLE_ARRAY,
	/**
	 * A 32-bit, single-precision floating-point number, ranging between
	 * -3.4E+38 to +3.4E+38.
	 */
	FLOAT,
	/**
	 * Ordered list of floats.
	 */
	FLOAT_ARRAY,
	/**
	 * A signed 32-bit integer, ranging from -2,147,483,648 and 2,147,483,647
	 * (inclusive).
	 */
	INTEGER,
	/**
	 * Ordered list of integers.
	 */
	INTEGER_ARRAY,
	/**
	 * A signed 64-bit integer, ranging from -9,223,372,036,854,775,808 to
	 * 9,223,372,036,854,775,807 (inclusive).
	 */
	LONG,
	/**
	 * Ordered list of longs.
	 */
	LONG_ARRAY,
	/**
	 * A list of key-value pairs, ordered by key. Each value can be of any type.
	 */
	NODE,
	/**
	 * A signed 16-bit integer, ranging from -32,768 to 32,767 (inclusive).
	 */
	SHORT,
	/**
	 * Ordered list of shorts.
	 */
	SHORT_ARRAY,
	/**
	 * A sequence of characters surrounded by quotes.
	 */
	STRING,
	/**
	 * Ordered list of strings.
	 */
	STRING_ARRAY
}