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
	 * provided map. It also stores the sizes of NODE_ARRAY and STRING_ARRAY.
	 *
	 * @param node The node we are calculating.
	 * @param sizes The sizes to cache this node, and any nested nodes, sizes
	 *            in.
	 * @return The total size for the node.
	 */
	@SuppressWarnings("unchecked")
	private static int calculateTotalSize(final @NonNull Node node,
		Map<NodeTree, Integer> sizes) {
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
					int arraySize = 0;
					for (int i = 0; i < arrayCount; ++i) {
						int childSize =
							TreeBinarySerialization.calculateTotalSize(
								((ArrayNode<Node>) entry.getValue()).getValues()
									.get(i),
								sizes);
						arraySize += childSize;
					}
					size += arraySize;
					sizes.put(entry.getValue(), arraySize);
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
					size += ((ValueNode<String>) entry.getValue()).getValue()
						.getBytes(StandardCharsets.UTF_8).length;
					break;
				case STRING_ARRAY:
					size += 8;
					List<String> strings =
						((ArrayNode<String>) entry.getValue()).getValues();
					int stringSize = 0;
					for (int i = 0; i < arrayCount; ++i) {
						size += 4;
						final int actualLength = strings.get(i)
							.getBytes(StandardCharsets.UTF_8).length;
						size += actualLength;
						stringSize += actualLength;
					}
					sizes.put(entry.getValue(), stringSize);
					break;
			}
		}
		sizes.put(node, size);
		return size;
	}

	public static Optional<Node> read(InputStream input) {

		return Optional.empty();
	}

	/**
	 * Log an error about a node being unexpected and throw an exception.
	 *
	 * @param node The node that was unexpected.
	 */
	private static void reportUnexpectedNode(final @NonNull NodeTree node) {
		TreeBinarySerialization.log.warn(SafeResourceLoader.getStringFormatted(
			"NODE_UNEXPECTED_TYPE", FactoryPlugin.getResourceBundle(),
			TreeBinarySerialization.class.getSimpleName(),
			node.getType().name()));
		throw new UnsupportedOperationException();
	}

	/**
	 * Serialize a node to the provided output stream as raw bytes.
	 *
	 * @param node The node to output to the stream as bytes.
	 * @param stream The stream to write data to.
	 * @return Whether we successfully wrote the node to the stream.
	 * @throws UnsupportedOperationException If there are nodes with
	 *             invalid/unsupported types.
	 */
	public static boolean write(final @NonNull Node node, OutputStream stream) {
		Map<NodeTree, Integer> sizes = new HashMap<>();
		TreeBinarySerialization.calculateTotalSize(node, sizes);
		try {
			TreeBinarySerialization.writeNode(node, stream, sizes);
			return true;
		}
		catch (IOException e) {
			TreeBinarySerialization.log.warn(SafeResourceLoader.getString(
				"NODE_SERIALIZATON_FAILED", FactoryPlugin.getResourceBundle()));
			return false;
		}
	}

	/**
	 * Write an array to the output stream.
	 *
	 * @param <T> The type of the array contents.
	 * @param node The array node to write.
	 * @param stream The stream we are writing to.
	 * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in
	 *            bytes.
	 * @throws IOException If there is a problem writing data.
	 */
	@SuppressWarnings("unchecked")
	private static <T> void writeArray(final @NonNull ArrayNode<T> node,
		OutputStream stream, final Map<NodeTree, Integer> sizes)
		throws IOException {

		switch (node.getType()) {
			case BOOLEAN_ARRAY:
				TreeBinarySerialization.writeBooleanArray(
					(List<Boolean>) node.getValues(), stream);
				break;
			case BYTE_ARRAY:
				TreeBinarySerialization
					.writeByteArray((List<Byte>) node.getValues(), stream);
				break;
			case DOUBLE_ARRAY:
				TreeBinarySerialization
					.writeDoubleArray((List<Double>) node.getValues(), stream);
				break;
			case FLOAT_ARRAY:
				TreeBinarySerialization
					.writeFloatArray((List<Float>) node.getValues(), stream);
				break;
			case INTEGER_ARRAY:
				TreeBinarySerialization.writeIntegerArray(
					(List<Integer>) node.getValues(), stream);
				break;
			case LONG_ARRAY:
				TreeBinarySerialization
					.writeLongArray((List<Long>) node.getValues(), stream);
				break;
			case NODE_ARRAY:
				TreeBinarySerialization.writeNodeArray(
					(List<Node>) node.getValues(), sizes.get(node), stream,
					sizes);
				break;
			case SHORT_ARRAY:
				TreeBinarySerialization
					.writeShortArray((List<Short>) node.getValues(), stream);
				break;
			case STRING_ARRAY:
				TreeBinarySerialization.writeStringArray(
					(List<String>) node.getValues(), sizes.get(node), stream);
				break;
			case BOOLEAN, BYTE, DOUBLE, FLOAT, INTEGER, LONG, NODE, SHORT,
				STRING:
				// fallthrough
			default:
				TreeBinarySerialization.reportUnexpectedNode(node);
		}
	}

	/**
	 * Write an array of boolean values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeBooleanArray(final @NonNull List<Boolean> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();

		int byteCount = (int) Math.ceil(arrayCount / 8.0f);
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < byteCount; ++i) {
			byte result = 0;
			for (int j = 0; j < 8; ++j) {
				final int index = i * 8 + j;
				if (index >= arrayCount) {
					break;
				}
				boolean value = values.get(index);

				if (value) {
					result |= 1 << 7 - j;
				}
			}
			TreeBinarySerialization.writeByte(result, stream);
		}
	}

	/**
	 * Write a byte value to the output stream.
	 *
	 * @param value The value to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeByte(final byte value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[1];
		data[0] = value;

		stream.write(data, 0, data.length);
	}

	/**
	 * Write an array of byte values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeByteArray(final @NonNull List<Byte> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeByte(values.get(i), stream);
		}
	}

	/**
	 * Write an array of double values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeDoubleArray(final @NonNull List<Double> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization
				.writeLong(Double.doubleToLongBits(values.get(i)), stream);
		}
	}

	/**
	 * Write an array of float values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeFloatArray(final @NonNull List<Float> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization
				.writeInteger(Float.floatToIntBits(values.get(i)), stream);
		}
	}

	/**
	 * Write an integer value to the output stream in big-endian.
	 *
	 * @param value The value to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeInteger(final int value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[4];
		data[0] = (byte) (value >>> 24);
		data[1] = (byte) ((value >>> 16) & 0x0000_00FF);
		data[2] = (byte) ((value >>> 8) & 0x0000_00FF);
		data[3] = (byte) (value & 0x0000_00FF);

		stream.write(data, 0, data.length);
	}

	/**
	 * Write an array of integer values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeIntegerArray(final @NonNull List<Integer> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeInteger(values.get(i), stream);
		}
	}

	/**
	 * Write a long value to the output stream in big-endian.
	 *
	 * @param value The value to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
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

	/**
	 * Write an array of long values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeLongArray(final @NonNull List<Long> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeLong(values.get(i), stream);
		}
	}

	/**
	 * Write a node to the output stream.
	 *
	 * @param node The node to write.
	 * @param stream The stream we are writing to.
	 * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in
	 *            bytes.
	 * @throws IOException If there is a problem writing data.
	 */
	private static void writeNode(final @NonNull Node node, OutputStream stream,
		Map<NodeTree, Integer> sizes) throws IOException {

		TreeBinarySerialization.writeLong(sizes.get(node), stream);
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
				TreeBinarySerialization.reportUnexpectedNode(node);
			}
		}
	}

	/**
	 * Write an array of byte values to the output stream.
	 *
	 * @param values The values to write.
	 * @param size The size of the node in bytes.
	 * @param stream The stream to write to.
	 * @param sizes The list of cached node sizes.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeNodeArray(final @NonNull List<Node> values,
		final int size, OutputStream stream, final Map<NodeTree, Integer> sizes)
		throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeLong(size, stream);
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeNode(values.get(i), stream, sizes);
		}
	}

	/**
	 * Write a short value to the output stream in big-endian.
	 *
	 * @param value The value to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeShort(final short value, OutputStream stream)
		throws IOException {
		byte[] data = new byte[2];
		data[0] = (byte) (value >>> 8);
		data[1] = (byte) (value & 0x00FF);

		stream.write(data, 0, data.length);
	}

	/**
	 * Write an array of short values to the output stream.
	 *
	 * @param values The values to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeShortArray(final @NonNull List<Short> values,
		OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeShort(values.get(i), stream);
		}
	}

	/**
	 * Write a boolean value to the output stream in as a byte.
	 *
	 * @param value The value to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeSingleBoolean(final boolean value,
		OutputStream stream) throws IOException {
		byte[] data = new byte[1];
		data[0] = value ? (byte) 1 : (byte) 0;

		stream.write(data, 0, data.length);
	}

	/**
	 * Write a string value to the output stream, prefixed by length.
	 *
	 * @param string The string to write.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeString(final String string, OutputStream stream)
		throws IOException {
		TreeBinarySerialization.writeInteger(string.length(), stream);
		stream.write(string.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Write an array of byte values to the output stream.
	 *
	 * @param values The values to write.
	 * @param size The size of the node in bytes.
	 * @param stream The stream to write to.
	 * @throws IOException If there is an error writing.
	 */
	private static void writeStringArray(final @NonNull List<String> values,
		final int size, OutputStream stream) throws IOException {

		final int arrayCount = values.size();
		TreeBinarySerialization.writeLong(size, stream);
		TreeBinarySerialization.writeInteger(arrayCount, stream);
		for (int i = 0; i < arrayCount; ++i) {
			TreeBinarySerialization.writeString(values.get(i), stream);
		}
	}

	/**
	 * Write a value node to the output stream.
	 *
	 * @param <T> The type of the value.
	 * @param node The array node to write.
	 * @param stream The stream we are writing to.
	 * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in
	 *            bytes.
	 * @throws IOException If there is a problem writing data.
	 */
	private static <T> void writeValue(final @NonNull ValueNode<T> node,
		OutputStream stream, Map<NodeTree, Integer> sizes) throws IOException {
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
				TreeBinarySerialization.reportUnexpectedNode(node);
		}
	}

	/**
	 * Private constructor so that this class is not instantiated.
	 */
	private TreeBinarySerialization() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
