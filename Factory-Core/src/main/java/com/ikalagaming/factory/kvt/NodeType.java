package com.ikalagaming.factory.kvt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The types of tags that we can have.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum NodeType {
	/**
	 * Byte value of 0 or 1 that represents true or false.
	 */
	BOOLEAN('\0', (byte) 0),
	/**
	 * Ordered list of booleans.
	 */
	BOOLEAN_ARRAY('Z', (byte) 1),
	/**
	 * A signed 8-bit integer, ranging between -128 to 127 (inclusive).
	 */
	BYTE('\0', (byte) 2),
	/**
	 * Ordered list of signed 8-bit integers.
	 */
	BYTE_ARRAY('B', (byte) 3),
	/**
	 * A 64-bit, double-precision floating-point, ranging between -1.7E+308 to
	 * +1.7E+308.
	 */
	DOUBLE('\0', (byte) 4),
	/**
	 * Ordered list of doubles.
	 */
	DOUBLE_ARRAY('D', (byte) 5),
	/**
	 * A 32-bit, single-precision floating-point number, ranging between
	 * -3.4E+38 to +3.4E+38.
	 */
	FLOAT('\0', (byte) 6),
	/**
	 * Ordered list of floats.
	 */
	FLOAT_ARRAY('F', (byte) 7),
	/**
	 * A signed 32-bit integer, ranging from -2,147,483,648 and 2,147,483,647
	 * (inclusive).
	 */
	INTEGER('\0', (byte) 8),
	/**
	 * Ordered list of integers.
	 */
	INTEGER_ARRAY('I', (byte) 9),
	/**
	 * A signed 64-bit integer, ranging from -9,223,372,036,854,775,808 to
	 * 9,223,372,036,854,775,807 (inclusive).
	 */
	LONG('\0', (byte) 10),
	/**
	 * Ordered list of longs.
	 */
	LONG_ARRAY('L', (byte) 11),
	/**
	 * A list of key-value pairs, ordered by key. Each value can be of any type.
	 */
	NODE('\0', (byte) 12),
	/**
	 * Ordered list of nodes.
	 */
	NODE_ARRAY('N', (byte) 13),
	/**
	 * A signed 16-bit integer, ranging from -32,768 to 32,767 (inclusive).
	 */
	SHORT('\0', (byte) 14),
	/**
	 * Ordered list of shorts.
	 */
	SHORT_ARRAY('S', (byte) 15),
	/**
	 * A sequence of characters surrounded by quotes.
	 */
	STRING('\0', (byte) 16),
	/**
	 * Ordered list of strings.
	 */
	STRING_ARRAY('T', (byte) 17);

	/**
	 * Convert the the letter prefix used in the string form of the arrays to
	 * type of node.
	 *
	 * @param letter The letter used to prefix the array to indicate the values.
	 * @return The corresponding type of array.
	 * @throws IllegalArgumentException If the provided letter is not valid.
	 */
	public static NodeType fromArrayLietter(final char letter) {
		if (letter != '\0') {
			for (NodeType type : NodeType.values()) {
				if (type.arrayLetter == letter) {
					return type;
				}
			}
		}
		throw new IllegalArgumentException("Unexpected value: " + letter);
	}

	/**
	 * Convert the byte used in the binary form of nodes to type of node.
	 *
	 * @param ID The byte used to indicate the type of node in binary format.
	 * @return The corresponding type of array.
	 * @throws IllegalArgumentException If the provided byte is not valid.
	 */
	public static NodeType fromBinaryID(final byte ID) {
		for (NodeType type : NodeType.values()) {
			if (type.binaryID == ID) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unexpected value: " + ID);
	}

	/**
	 * The letter used to indicate the type of an array in text format. Not
	 * applicable for non-array node types.
	 */
	private final char arrayLetter;

	/**
	 * The byte used to indicate the type of node in binary format.
	 */
	private final byte binaryID;

}