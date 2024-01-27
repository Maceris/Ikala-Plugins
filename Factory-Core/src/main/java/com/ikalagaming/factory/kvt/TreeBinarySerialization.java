package com.ikalagaming.factory.kvt;

import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Utilities for converting a tree to and from a binary format.
 *
 * @author Ches Burks
 *
 */
public class TreeBinarySerialization {

	public static Optional<Node> read(InputStream input) {

		return Optional.empty();
	}

	public static void write(final @NonNull Node node, OutputStream stream) {

	}

	private static void write(final @NonNull ValueNode node,
		OutputStream stream) {

	}

	private static void writeByte(final byte value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[1];
		data[0] = value;

		stream.write(data, 0, data.length);
	}

	private static void writeInteger(final int value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[4];
		data[0] = (byte) (value >>> 24);
		data[1] = (byte) ((value >>> 16) & 0x0000_00FF);
		data[2] = (byte) ((value >>> 8) & 0x0000_00FF);
		data[3] = (byte) (value & 0x0000_00FF);

		stream.write(data, 0, data.length);
	}

	private static void writeLong(final long value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[8];
		data[0] = (byte) (value >>> 56);
		data[1] = (byte) ((value >>> 48) & 0x0000_0000_0000_00FF);
		data[2] = (byte) ((value >>> 40) & 0x0000_0000_0000_00FF);
		data[3] = (byte) ((value >>> 32) & 0x0000_0000_0000_00FF);
		data[4] = (byte) ((value >>> 24) & 0x0000_0000_0000_00FF);
		data[5] = (byte) ((value >>> 16) & 0x0000_0000_0000_00FF);
		data[6] = (byte) ((value >>> 8) & 0x0000_0000_0000_00FF);
		data[7] = (byte) (value & 0x0000_0000_0000_00FF);

		stream.write(data, 0, data.length);
	}

	private static void writeShort(final short value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[2];
		data[0] = (byte) (value >>> 8);
		data[1] = (byte) (value & 0x00FF);

		stream.write(data, 0, data.length);
	}

	private static void writeString(final String string, OutputStream stream)
		throws IOException {
		TreeBinarySerialization.writeInteger(string.length(), stream);
		stream.write(string.getBytes());
	}

}
