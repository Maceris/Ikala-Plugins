package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utilities for converting a tree to and from a binary format.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class TreeBinarySerialization {

	/**
	 * Calculate the total size for a node. Since this recursively calculates
	 * the sizes of sub-nodes, any child Node types are also cached in the
	 * provided map.
	 * 
	 * @param node The node we are calculating.
	 * @param sizes The sizes to cache this node, and any nested nodes, sizes
	 *            in.
	 * @return The total size for the node.
	 */
	@SuppressWarnings("unchecked")
	private static int calculateTotalSize(final @NonNull Node node,
		Map<Node, Integer> sizes) {
		int size = 0;
		size += 8;
		for (var entry : node.getValues().entrySet()) {
			size += 4;
			size += entry.getKey().getBytes(StandardCharsets.UTF_8).length;

			int arrayCount = 0;
			if (entry.getValue() instanceof ArrayNode<?> array) {
				arrayCount = array.getValues().size();
			}

			switch (entry.getValue().getType()) {
				case BOOLEAN:
					size += 1;
					break;
				case BOOLEAN_ARRAY:
					size += 4;
					int byteCount = (int) Math.ceil(arrayCount / 8.0f);
					size += byteCount;
					break;
				case BYTE:
					size += 1;
					break;
				case BYTE_ARRAY:
					size += 4;
					size += arrayCount;
					break;
				case DOUBLE, LONG:
					size += 8;
					break;
				case DOUBLE_ARRAY, LONG_ARRAY:
					size += 4;
					size += 8 * arrayCount;
					break;
				case FLOAT, INTEGER:
					size += 4;
					break;
				case FLOAT_ARRAY, INTEGER_ARRAY:
					size += 4;
					size += 4 * arrayCount;
					break;
				case NODE:
					size += TreeBinarySerialization
						.calculateTotalSize((Node) entry.getValue(), sizes);
					break;
				case NODE_ARRAY:
					size += 8;
					size += 4;
					for (int i = 0; i < arrayCount; ++i) {
						size += TreeBinarySerialization.calculateTotalSize(
							((ArrayNode<Node>) entry).getValues().get(i),
							sizes);
					}
					break;
				case SHORT:
					size += 2;
					break;
				case SHORT_ARRAY:
					size += 4;
					size += 2 * arrayCount;
					break;
				case STRING:
					size += 4;
					size += ((ValueNode<String>) entry).getValue()
						.getBytes(StandardCharsets.UTF_8).length;
					break;
				case STRING_ARRAY:
					size += 8;
					List<String> strings =
						((ArrayNode<String>) entry).getValues();
					for (int i = 0; i < arrayCount; ++i) {
						size += 4;
						size += strings.get(i)
							.getBytes(StandardCharsets.UTF_8).length;
					}
					break;
			}
		}
		sizes.put(node, size);
		return size;
	}

	public static Optional<Node> read(InputStream input) {

		return Optional.empty();
	}

	private static <T> void writeArray(final @NonNull ArrayNode<T> node,
		OutputStream stream, Map<Node, Integer> sizes) throws IOException {

	}

	public static void write(final @NonNull Node node, OutputStream stream)
		throws IOException {
		Map<Node, Integer> sizes = new HashMap<>();
		TreeBinarySerialization.calculateTotalSize(node, sizes);
		writeNode(node, stream, sizes);
	}

	private static void writeNode(final @NonNull Node node, OutputStream stream,
		Map<Node, Integer> sizes) throws IOException {

		TreeBinarySerialization.writeInteger(sizes.get(node), stream);
		for (var entry : node.getValues().entrySet()) {
			TreeBinarySerialization
				.writeByte(entry.getValue().getType().getBinaryID(), stream);
			TreeBinarySerialization.writeString(entry.getKey(), stream);

			if (entry.getValue() instanceof ArrayNode<?> array) {
				TreeBinarySerialization.writeArray(array, stream, sizes);
			}
			else if (entry.getValue() instanceof ValueNode<?> value) {
				TreeBinarySerialization.writeValue(value, stream, sizes);
			}
			else if (entry.getValue() instanceof Node subNode) {
				TreeBinarySerialization.writeNode(subNode, stream, sizes);
			}
			else {
				TreeBinarySerialization.log.warn(SafeResourceLoader
					.getStringFormatted("NODE_UNEXPECTED_TYPE",
						FactoryPlugin.getResourceBundle(),
						TreeBinarySerialization.class.getSimpleName(),
						node.getType().name()));
				throw new UnsupportedOperationException();
			}
		}
	}

	private static <T> void writeValue(final @NonNull ValueNode<T> node,
		OutputStream stream, Map<Node, Integer> sizes) throws IOException {
		switch (node.getType()) {
			case BOOLEAN:
				TreeBinarySerialization
					.writeSingleBoolean((Boolean) node.getValue(), stream);
				break;
			case BYTE:
				TreeBinarySerialization.writeByte((Byte) node.getValue(),
					stream);
				break;
			case DOUBLE:
				TreeBinarySerialization.writeLong(
					Double.doubleToLongBits((Double) node.getValue()), stream);
				break;
			case FLOAT:
				TreeBinarySerialization.writeInteger(
					Float.floatToIntBits((Float) node.getValue()), stream);
				break;
			case INTEGER:
				TreeBinarySerialization.writeInteger((Integer) node.getValue(),
					stream);
				break;
			case LONG:
				TreeBinarySerialization.writeLong((Long) node.getValue(),
					stream);
				break;
			case NODE:
				TreeBinarySerialization.writeNode((Node) node.getValue(),
					stream, sizes);
				break;
			case SHORT:
				TreeBinarySerialization.writeShort((Short) node.getValue(),
					stream);
				break;
			case STRING:
				TreeBinarySerialization.writeString((String) node.getValue(),
					stream);
				break;
			case BOOLEAN_ARRAY, BYTE_ARRAY, DOUBLE_ARRAY, FLOAT_ARRAY,
				INTEGER_ARRAY, LONG_ARRAY, NODE_ARRAY, SHORT_ARRAY,
				STRING_ARRAY:
				// fallthrough
			default:
				TreeBinarySerialization.log.warn(SafeResourceLoader
					.getStringFormatted("NODE_UNEXPECTED_TYPE",
						FactoryPlugin.getResourceBundle(),
						TreeBinarySerialization.class.getSimpleName(),
						node.getType().name()));
				throw new UnsupportedOperationException();
		}
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

	private static void writeSingleBoolean(final boolean value,
		OutputStream stream) throws IOException {
		byte[] data = new byte[1];
		data[0] = value ? (byte) 1 : (byte) 0;

		stream.write(data, 0, data.length);
	}

	private static void writeString(final String string, OutputStream stream)
		throws IOException {
		TreeBinarySerialization.writeInteger(string.length(), stream);
		stream.write(string.getBytes(StandardCharsets.UTF_8));
	}

}
