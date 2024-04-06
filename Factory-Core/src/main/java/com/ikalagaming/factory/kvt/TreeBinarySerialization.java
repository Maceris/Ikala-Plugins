package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utilities for converting a tree to and from a binary format.
 *
 * @author Ches Burks
 */
@Slf4j
public class TreeBinarySerialization {

    /**
     * Used to track how many bytes we have read so far, and how many in total we expect there to
     * be.
     *
     * @author Ches Burks
     */
    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class ReadCount {
        /** The total number of bytes. */
        private final long total;

        /** The number of bytes we have read so far. Should be <= total. */
        private long read = 0;

        /**
         * The parent count, if we are reading in a sub-node. This is used so that when bytes are
         * read in, every node up the stack tracks that information.
         */
        private ReadCount parent;

        /**
         * Return the number of remaining bytes we expect to read.
         *
         * @return The total bytes left to be read.
         */
        public long getRemaining() {
            return Math.max(0, total - read);
        }

        /**
         * If we have read all the input.
         *
         * @return Whether we have read the total amount of bytes.
         */
        public boolean isDone() {
            return read >= total;
        }

        /**
         * Track that we have read count more bytes.
         *
         * @param count The number of bytes to add to the total.
         */
        public void recordRead(int count) {
            read += count;
            if (parent != null) {
                parent.recordRead(count);
            }
        }
    }

    /**
     * Calculate the total size for a node, should it be converted to binary. This is somewhat
     * expensive, and intended to only be used for things like testing.
     *
     * @param node The node that we want to calculate the size of.
     * @return The number of bytes that this node will correspond to in binary, before any kind of
     *     added compression.
     */
    static int calculateTotalSize(final @NonNull Node node) {
        Map<KVT, Integer> sizes = new HashMap<>();
        return TreeBinarySerialization.calculateTotalSize(node, sizes);
    }

    /**
     * Calculate the total size for a node. Since this recursively calculates the sizes of
     * sub-nodes, any child Node types are also cached in the provided map. It also stores the sizes
     * of NODE_ARRAY and STRING_ARRAY.
     *
     * @param node The node we are calculating.
     * @param sizes The sizes to cache this node, and any nested nodes, sizes in.
     * @return The total size for the node.
     */
    @SuppressWarnings("unchecked")
    private static int calculateTotalSize(final @NonNull Node node, Map<KVT, Integer> sizes) {
        int size = 0;
        size += 8; // size in bytes
        for (var entry : node.getValues().entrySet()) {
            size += 1; // Node type
            size += 4; // key string size
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
                    size +=
                            TreeBinarySerialization.calculateTotalSize(
                                    (Node) entry.getValue(), sizes);
                    break;
                case NODE_ARRAY:
                    int arraySize = 8 + 4;
                    for (int i = 0; i < arrayCount; ++i) {
                        int childSize =
                                TreeBinarySerialization.calculateTotalSize(
                                        ((ArrayNode<Node>) entry.getValue()).getValues().get(i),
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
                    size +=
                            ((ValueNode<String>) entry.getValue())
                                    .getValue()
                                    .getBytes(StandardCharsets.UTF_8)
                                    .length;
                    break;
                case STRING_ARRAY:
                    int stringSize = 8 + 4;
                    List<String> strings = ((ArrayNode<String>) entry.getValue()).getValues();
                    for (int i = 0; i < arrayCount; ++i) {
                        stringSize += 4;
                        final int actualLength =
                                strings.get(i).getBytes(StandardCharsets.UTF_8).length;
                        stringSize += actualLength;
                    }
                    size += stringSize;
                    sizes.put(entry.getValue(), stringSize);
                    break;
            }
        }
        sizes.put(node, size);
        return size;
    }

    /**
     * Read an entire node from the input stream.
     *
     * @param input The stream to read data from.
     * @return A full tree, if nothing went wrong.
     */
    public static Optional<Node> read(@NonNull InputStream input) {
        long totalSize = 0;
        try {
            totalSize = TreeBinarySerialization.readLongDirectly(input);
            ReadCount count = new ReadCount(totalSize);
            // NOTE(ches) The size that we just read.
            count.recordRead(8);

            return Optional.of(TreeBinarySerialization.readNode(input, count, totalSize));
        } catch (IOException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "NODE_SERIALIZATION_FAILED", FactoryPlugin.getResourceBundle()),
                    e);
            return Optional.empty();
        }
    }

    /**
     * Read a list of booleans.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Boolean> readBooleanArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Boolean> result = new ArrayList<>();

        final int booleanCount = TreeBinarySerialization.readInteger(input, readCount);
        final int byteCount = (int) Math.ceil(booleanCount / 8.0f);

        byte[] data = TreeBinarySerialization.readBytes(input, byteCount, readCount);

        for (int i = 0; i < byteCount; ++i) {
            byte raw = data[i];

            for (int j = 0; j < 8; ++j) {
                final int index = i * 8 + j;
                if (index >= booleanCount) {
                    break;
                }
                int current = (raw >>> 7 - j) & 1;
                result.add(current != 0);
            }
        }

        return result;
    }

    /**
     * Read in a byte.
     *
     * @param input The stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The value.
     * @throws IOException If something goes wrong reading bytes.
     */
    private static byte readByte(InputStream input, ReadCount readCount) throws IOException {

        byte[] data = TreeBinarySerialization.readBytes(input, 1, readCount);

        return data[0];
    }

    /**
     * Read a list of bytes.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Byte> readByteArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Byte> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readByte(input, readCount));
        }

        return result;
    }

    /**
     * Read bytes from an input stream. Records how many we actually read. If count is more than the
     * number we expect to be remaining, only reads as many bytes as are remaining.
     *
     * @param input The stream to read from.
     * @param count The number of bytes we want to read.
     * @param readCount Tracking for how much we expect to read.
     * @return The bytes we read.
     * @throws IOException If we failed to read, or couldn't read enough data.
     */
    private static byte[] readBytes(InputStream input, int count, ReadCount readCount)
            throws IOException {

        final int toRead = (int) Math.min(count, readCount.getRemaining());

        byte[] output = new byte[toRead];
        int actuallyRead = input.read(output);
        readCount.recordRead(actuallyRead);

        if (actuallyRead < count) {
            throw new IOException(
                    SafeResourceLoader.getStringFormatted(
                            "NODE_OUT_OF_DATA",
                            FactoryPlugin.getResourceBundle(),
                            "" + count,
                            "" + actuallyRead));
        }

        return output;
    }

    /**
     * Read a list of doubles.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Double> readDoubleArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Double> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(Double.longBitsToDouble(TreeBinarySerialization.readLong(input, readCount)));
        }

        return result;
    }

    /**
     * Read a list of floats.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Float> readFloatArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Float> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(Float.intBitsToFloat(TreeBinarySerialization.readInteger(input, readCount)));
        }

        return result;
    }

    /**
     * Read in an integer.
     *
     * @param input The stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The value.
     * @throws IOException If something goes wrong reading bytes.
     */
    private static int readInteger(InputStream input, ReadCount readCount) throws IOException {

        final int BYTES = 4;
        byte[] data = TreeBinarySerialization.readBytes(input, BYTES, readCount);

        int value = 0;

        for (int i = 0; i < BYTES; ++i) {
            value |= (data[i] & 0x0000_00FF) << (8 * (BYTES - 1 - i));
        }

        return value;
    }

    /**
     * Read a list of integers.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Integer> readIntegerArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Integer> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readInteger(input, readCount));
        }

        return result;
    }

    /**
     * Read in a long.
     *
     * @param input The stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The value.
     * @throws IOException If something goes wrong reading bytes.
     */
    private static long readLong(InputStream input, ReadCount readCount) throws IOException {

        final int BYTES = 8;

        byte[] data = TreeBinarySerialization.readBytes(input, BYTES, readCount);

        long value = 0;
        for (int i = 0; i < BYTES; ++i) {
            value |= (data[i] & 0xFFL) << (8 * (BYTES - 1 - i));
        }

        return value;
    }

    /**
     * Read a list of longs.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Long> readLongArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Long> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readLong(input, readCount));
        }

        return result;
    }

    /**
     * Read a long directly, without tracking any reads. Only used for the initial size of a root
     * node.
     *
     * @param input The input stream to read from.
     * @return The resulting long.
     * @throws IOException If there is a failure reading the value.
     */
    private static long readLongDirectly(InputStream input) throws IOException {
        final int BYTES = 8;

        byte[] data = new byte[BYTES];

        if (input.read(data) < 8) {
            throw new IOException();
        }

        long value = 0;

        for (int i = 0; i < BYTES; ++i) {
            value |= (data[i] & 0xFFL) << (8 * (BYTES - 1 - i));
        }

        return value;
    }

    /**
     * Reads a node, including the size.
     *
     * @param input The stream to read data from.
     * @param count Tracking for how much we are reading.
     * @return The node, or null if there is a problem.
     * @throws IOException If there is a problem reading data.
     */
    private static Node readNode(InputStream input, ReadCount count) throws IOException {

        final long totalSize = TreeBinarySerialization.readLong(input, count);

        return TreeBinarySerialization.readNode(input, count, totalSize);
    }

    /**
     * Reads a node that already had the total size of the node read in.
     *
     * @param input The stream to read data from.
     * @param count Tracking for how much we are reading.
     * @param size The size of the node in bytes (including the size of the length that has already
     *     been read in).
     * @return The node.
     * @throws IOException If there is a problem reading data.
     */
    private static Node readNode(InputStream input, ReadCount count, long size) throws IOException {

        ReadCount subCount = new ReadCount(size);
        subCount.recordRead(8);
        subCount.setParent(count);

        Node result = new Node();
        while (!subCount.isDone()) {
            byte typeID = TreeBinarySerialization.readByte(input, subCount);
            String key = TreeBinarySerialization.readString(input, subCount);

            switch (NodeType.fromBinaryID(typeID)) {
                case BOOLEAN:
                    result.addBoolean(key, TreeBinarySerialization.readByte(input, subCount) != 0);
                    break;
                case BOOLEAN_ARRAY:
                    result.addBooleanArray(
                            key, TreeBinarySerialization.readBooleanArray(input, subCount));
                    break;
                case BYTE:
                    result.addByte(key, TreeBinarySerialization.readByte(input, subCount));
                    break;
                case BYTE_ARRAY:
                    result.addByteArray(
                            key, TreeBinarySerialization.readByteArray(input, subCount));
                    break;
                case DOUBLE:
                    long temp = TreeBinarySerialization.readLong(input, subCount);
                    result.addDouble(key, Double.longBitsToDouble(temp));
                    break;
                case DOUBLE_ARRAY:
                    result.addDoubleArray(
                            key, TreeBinarySerialization.readDoubleArray(input, subCount));
                    break;
                case FLOAT:
                    result.addFloat(
                            key,
                            Float.intBitsToFloat(
                                    TreeBinarySerialization.readInteger(input, subCount)));
                    break;
                case FLOAT_ARRAY:
                    result.addFloatArray(
                            key, TreeBinarySerialization.readFloatArray(input, subCount));
                    break;
                case INTEGER:
                    result.addInteger(key, TreeBinarySerialization.readInteger(input, subCount));
                    break;
                case INTEGER_ARRAY:
                    result.addIntegerArray(
                            key, TreeBinarySerialization.readIntegerArray(input, subCount));
                    break;
                case LONG:
                    result.addLong(key, TreeBinarySerialization.readLong(input, subCount));
                    break;
                case LONG_ARRAY:
                    result.addLongArray(
                            key, TreeBinarySerialization.readLongArray(input, subCount));
                    break;
                case NODE:
                    result.addNode(key, TreeBinarySerialization.readNode(input, subCount));
                    break;
                case NODE_ARRAY:
                    result.addNodeArray(
                            key, TreeBinarySerialization.readNodeArray(input, subCount));
                    break;
                case SHORT:
                    result.addShort(key, TreeBinarySerialization.readShort(input, subCount));
                    break;
                case SHORT_ARRAY:
                    result.addShortArray(
                            key, TreeBinarySerialization.readShortArray(input, subCount));
                    break;
                case STRING:
                    result.addString(key, TreeBinarySerialization.readString(input, subCount));
                    break;
                case STRING_ARRAY:
                    result.addStringArray(
                            key, TreeBinarySerialization.readStringArray(input, subCount));
                    break;
            }
        }

        return result;
    }

    /**
     * Read a list of nodes.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Node> readNodeArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Node> result = new ArrayList<>();

        final long sizeInBytes = TreeBinarySerialization.readLong(input, readCount);

        ReadCount arrayCount = new ReadCount(sizeInBytes);
        arrayCount.recordRead(8);
        arrayCount.setParent(readCount);

        final int size = TreeBinarySerialization.readInteger(input, arrayCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readNode(input, arrayCount));
        }

        return result;
    }

    /**
     * Read in a short.
     *
     * @param input The stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The value.
     * @throws IOException If something goes wrong reading bytes.
     */
    private static short readShort(InputStream input, ReadCount readCount) throws IOException {

        final int BYTES = 2;
        byte[] data = TreeBinarySerialization.readBytes(input, BYTES, readCount);

        short value = 0;

        for (int i = 0; i < BYTES; ++i) {
            value |= (short) ((data[i] & 0x00FF) << (8 * (BYTES - 1 - i)));
        }

        return value;
    }

    /**
     * Read a list of shorts.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<Short> readShortArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<Short> result = new ArrayList<>();

        final int size = TreeBinarySerialization.readInteger(input, readCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readShort(input, readCount));
        }

        return result;
    }

    /**
     * Reads a String.
     *
     * @param input The stream to read data from.
     * @param count Tracking for how much we are reading.
     * @return The node, or null if there is a problem.
     * @throws IOException If there is a problem reading data.
     */
    private static String readString(InputStream input, ReadCount count) throws IOException {

        final int size = TreeBinarySerialization.readInteger(input, count);
        byte[] rawData = TreeBinarySerialization.readBytes(input, size, count);

        return new String(rawData, StandardCharsets.UTF_8);
    }

    /**
     * Read a list of strings.
     *
     * @param input The input stream to read from.
     * @param readCount Tracking for how much we are reading.
     * @return The resulting value.
     * @throws IOException If there is a failure reading the value.
     */
    private static List<String> readStringArray(InputStream input, ReadCount readCount)
            throws IOException {

        List<String> result = new ArrayList<>();

        final long sizeInBytes = TreeBinarySerialization.readLong(input, readCount);
        ReadCount arrayCount = new ReadCount(sizeInBytes);
        arrayCount.recordRead(8);
        arrayCount.setParent(readCount);

        final int size = TreeBinarySerialization.readInteger(input, arrayCount);

        for (int i = 0; i < size; ++i) {
            result.add(TreeBinarySerialization.readString(input, arrayCount));
        }

        return result;
    }

    /**
     * Log an error about a node being unexpected and throw an exception.
     *
     * @param node The node that was unexpected.
     */
    private static void reportUnexpectedNode(final @NonNull KVT node) {
        log.warn(
                SafeResourceLoader.getStringFormatted(
                        "NODE_UNEXPECTED_TYPE",
                        FactoryPlugin.getResourceBundle(),
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
     * @throws UnsupportedOperationException If there are nodes with invalid/unsupported types.
     */
    public static boolean write(final @NonNull Node node, @NonNull OutputStream stream) {
        Map<KVT, Integer> sizes = new HashMap<>();
        TreeBinarySerialization.calculateTotalSize(node, sizes);
        try {
            TreeBinarySerialization.writeNode(node, stream, sizes);
            return true;
        } catch (IOException e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "NODE_SERIALIZATION_FAILED", FactoryPlugin.getResourceBundle()));
            return false;
        }
    }

    /**
     * Write an array to the output stream.
     *
     * @param <T> The type of the array contents.
     * @param node The array node to write.
     * @param stream The stream we are writing to.
     * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in bytes.
     * @throws IOException If there is a problem writing data.
     */
    @SuppressWarnings("unchecked")
    private static <T> void writeArray(
            final @NonNull ArrayNode<T> node, OutputStream stream, final Map<KVT, Integer> sizes)
            throws IOException {

        switch (node.getType()) {
            case BOOLEAN_ARRAY:
                TreeBinarySerialization.writeBooleanArray((List<Boolean>) node.getValues(), stream);
                break;
            case BYTE_ARRAY:
                TreeBinarySerialization.writeByteArray((List<Byte>) node.getValues(), stream);
                break;
            case DOUBLE_ARRAY:
                TreeBinarySerialization.writeDoubleArray((List<Double>) node.getValues(), stream);
                break;
            case FLOAT_ARRAY:
                TreeBinarySerialization.writeFloatArray((List<Float>) node.getValues(), stream);
                break;
            case INTEGER_ARRAY:
                TreeBinarySerialization.writeIntegerArray((List<Integer>) node.getValues(), stream);
                break;
            case LONG_ARRAY:
                TreeBinarySerialization.writeLongArray((List<Long>) node.getValues(), stream);
                break;
            case NODE_ARRAY:
                TreeBinarySerialization.writeNodeArray(
                        (List<Node>) node.getValues(), sizes.get(node), stream, sizes);
                break;
            case SHORT_ARRAY:
                TreeBinarySerialization.writeShortArray((List<Short>) node.getValues(), stream);
                break;
            case STRING_ARRAY:
                TreeBinarySerialization.writeStringArray(
                        (List<String>) node.getValues(), sizes.get(node), stream);
                break;
            case BOOLEAN, BYTE, DOUBLE, FLOAT, INTEGER, LONG, NODE, SHORT, STRING:
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
    private static void writeBooleanArray(final @NonNull List<Boolean> values, OutputStream stream)
            throws IOException {

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
                    result |= (byte) (1 << 7 - j);
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
    private static void writeByte(final byte value, OutputStream stream) throws IOException {
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
    private static void writeByteArray(final @NonNull List<Byte> values, OutputStream stream)
            throws IOException {

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
    private static void writeDoubleArray(final @NonNull List<Double> values, OutputStream stream)
            throws IOException {

        final int arrayCount = values.size();
        TreeBinarySerialization.writeInteger(arrayCount, stream);
        for (int i = 0; i < arrayCount; ++i) {
            TreeBinarySerialization.writeLong(Double.doubleToLongBits(values.get(i)), stream);
        }
    }

    /**
     * Write an array of float values to the output stream.
     *
     * @param values The values to write.
     * @param stream The stream to write to.
     * @throws IOException If there is an error writing.
     */
    private static void writeFloatArray(final @NonNull List<Float> values, OutputStream stream)
            throws IOException {

        final int arrayCount = values.size();
        TreeBinarySerialization.writeInteger(arrayCount, stream);
        for (int i = 0; i < arrayCount; ++i) {
            TreeBinarySerialization.writeInteger(Float.floatToIntBits(values.get(i)), stream);
        }
    }

    /**
     * Write an integer value to the output stream in big-endian.
     *
     * @param value The value to write.
     * @param stream The stream to write to.
     * @throws IOException If there is an error writing.
     */
    private static void writeInteger(final int value, OutputStream stream) throws IOException {
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
    private static void writeIntegerArray(final @NonNull List<Integer> values, OutputStream stream)
            throws IOException {

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
    private static void writeLong(final long value, OutputStream stream) throws IOException {
        byte[] data = new byte[8];
        data[0] = (byte) ((value >>> 56) & 0xFFL);
        data[1] = (byte) ((value >>> 48) & 0xFFL);
        data[2] = (byte) ((value >>> 40) & 0xFFL);
        data[3] = (byte) ((value >>> 32) & 0xFFL);
        data[4] = (byte) ((value >>> 24) & 0xFFL);
        data[5] = (byte) ((value >>> 16) & 0xFFL);
        data[6] = (byte) ((value >>> 8) & 0xFFL);
        data[7] = (byte) (value & 0xFFL);

        stream.write(data, 0, data.length);
    }

    /**
     * Write an array of long values to the output stream.
     *
     * @param values The values to write.
     * @param stream The stream to write to.
     * @throws IOException If there is an error writing.
     */
    private static void writeLongArray(final @NonNull List<Long> values, OutputStream stream)
            throws IOException {

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
     * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in bytes.
     * @throws IOException If there is a problem writing data.
     */
    private static void writeNode(
            final @NonNull Node node, OutputStream stream, Map<KVT, Integer> sizes)
            throws IOException {

        TreeBinarySerialization.writeLong(sizes.get(node), stream);
        for (var entry : node.getValues().entrySet()) {
            TreeBinarySerialization.writeByte(entry.getValue().getType().getBinaryID(), stream);
            TreeBinarySerialization.writeString(entry.getKey(), stream);

            if (entry.getValue() instanceof ArrayNode<?> array) {
                TreeBinarySerialization.writeArray(array, stream, sizes);
            } else if (entry.getValue() instanceof ValueNode<?> value) {
                TreeBinarySerialization.writeValue(value, stream, sizes);
            } else if (entry.getValue() instanceof Node subNode) {
                TreeBinarySerialization.writeNode(subNode, stream, sizes);
            } else {
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
    private static void writeNodeArray(
            final @NonNull List<Node> values,
            final int size,
            OutputStream stream,
            final Map<KVT, Integer> sizes)
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
    private static void writeShort(final short value, OutputStream stream) throws IOException {
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
    private static void writeShortArray(final @NonNull List<Short> values, OutputStream stream)
            throws IOException {

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
    private static void writeSingleBoolean(final boolean value, OutputStream stream)
            throws IOException {
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
    private static void writeString(final String string, OutputStream stream) throws IOException {
        final byte[] rawData = string.getBytes(StandardCharsets.UTF_8);
        TreeBinarySerialization.writeInteger(rawData.length, stream);
        stream.write(rawData);
    }

    /**
     * Write an array of byte values to the output stream.
     *
     * @param values The values to write.
     * @param size The size of the node in bytes.
     * @param stream The stream to write to.
     * @throws IOException If there is an error writing.
     */
    private static void writeStringArray(
            final @NonNull List<String> values, final int size, OutputStream stream)
            throws IOException {

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
     * @param sizes The map from NODE, NODE_ARRAY, or STRING_ARRAY to size in bytes.
     * @throws IOException If there is a problem writing data.
     */
    private static <T> void writeValue(
            final @NonNull ValueNode<T> node, OutputStream stream, Map<KVT, Integer> sizes)
            throws IOException {
        switch (node.getType()) {
            case BOOLEAN:
                TreeBinarySerialization.writeSingleBoolean((Boolean) node.getValue(), stream);
                break;
            case BYTE:
                TreeBinarySerialization.writeByte((Byte) node.getValue(), stream);
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
                TreeBinarySerialization.writeInteger((Integer) node.getValue(), stream);
                break;
            case LONG:
                TreeBinarySerialization.writeLong((Long) node.getValue(), stream);
                break;
            case NODE:
                TreeBinarySerialization.writeNode((Node) node.getValue(), stream, sizes);
                break;
            case SHORT:
                TreeBinarySerialization.writeShort((Short) node.getValue(), stream);
                break;
            case STRING:
                TreeBinarySerialization.writeString((String) node.getValue(), stream);
                break;
            case BOOLEAN_ARRAY,
                    BYTE_ARRAY,
                    DOUBLE_ARRAY,
                    FLOAT_ARRAY,
                    INTEGER_ARRAY,
                    LONG_ARRAY,
                    NODE_ARRAY,
                    SHORT_ARRAY,
                    STRING_ARRAY:
                // fallthrough
            default:
                TreeBinarySerialization.reportUnexpectedNode(node);
        }
    }

    /** Private constructor so that this class is not instantiated. */
    private TreeBinarySerialization() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
