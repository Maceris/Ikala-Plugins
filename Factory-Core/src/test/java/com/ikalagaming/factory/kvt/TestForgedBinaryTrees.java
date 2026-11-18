package com.ikalagaming.factory.kvt;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/** Tests that binary trees with forged sizes and counts are refused, quickly and cleanly. */
class TestForgedBinaryTrees {
    /** Long enough for any refusal, far too short to allocate or recurse without limit. */
    private static final Duration QUICKLY = Duration.ofSeconds(2);

    /** A key used for every entry. */
    private static final byte[] KEY = "k".getBytes(StandardCharsets.UTF_8);

    /** Writes the parts of a tree by hand. */
    @FunctionalInterface
    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * A root node holding whatever the body writes, with a correct size.
     *
     * @param body The entries.
     * @return The bytes.
     */
    private static byte[] root(Body body) throws IOException {
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        body.write(new DataOutputStream(entries));
        return sized(entries.toByteArray());
    }

    /**
     * A node: its size, then its entries.
     *
     * @param entries The entries.
     * @return The bytes.
     */
    private static byte[] sized(byte[] entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(8L + entries.length);
        out.write(entries);
        return bytes.toByteArray();
    }

    /**
     * Write an entry's type and key.
     *
     * @param out Where to write.
     * @param type The type.
     */
    private static void header(DataOutputStream out, NodeType type) throws IOException {
        out.writeByte(type.getBinaryID());
        out.writeInt(KEY.length);
        out.write(KEY);
    }

    private static Optional<Node> read(byte[] bytes) {
        return assertTimeoutPreemptively(
                QUICKLY, () -> TreeBinarySerialization.read(new ByteArrayInputStream(bytes)));
    }

    private static void assertRefused(byte[] bytes) {
        assertTrue(read(bytes).isEmpty());
    }

    @Test
    void hugeArrayCountsAreRefused() throws IOException {
        for (NodeType type :
                new NodeType[] {
                    NodeType.BYTE_ARRAY,
                    NodeType.SHORT_ARRAY,
                    NodeType.INTEGER_ARRAY,
                    NodeType.LONG_ARRAY,
                    NodeType.FLOAT_ARRAY,
                    NodeType.DOUBLE_ARRAY,
                    NodeType.BOOLEAN_ARRAY
                }) {
            for (int count : new int[] {Integer.MAX_VALUE, -1, Integer.MIN_VALUE, 100}) {
                assertRefused(
                        root(
                                out -> {
                                    header(out, type);
                                    out.writeInt(count);
                                    out.writeInt(0);
                                }));
            }
        }
    }

    @Test
    void stringLengthsAreChecked() throws IOException {
        for (int length : new int[] {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 50}) {
            assertRefused(
                    root(
                            out -> {
                                header(out, NodeType.STRING);
                                out.writeInt(length);
                                out.writeInt(0);
                            }));
        }
    }

    @Test
    void nestedSizesMustFitTheirParent() throws IOException {
        for (long size : new long[] {1_000, Long.MAX_VALUE, -1, 0, 7}) {
            assertRefused(
                    root(
                            out -> {
                                header(out, NodeType.NODE);
                                out.writeLong(size);
                            }));
        }
        for (NodeType type : new NodeType[] {NodeType.NODE_ARRAY, NodeType.STRING_ARRAY}) {
            assertRefused(
                    root(
                            out -> {
                                header(out, type);
                                out.writeLong(Long.MAX_VALUE);
                                out.writeInt(Integer.MAX_VALUE);
                            }));
            assertRefused(
                    root(
                            out -> {
                                header(out, type);
                                out.writeLong(12);
                                out.writeInt(Integer.MAX_VALUE);
                            }));
        }
    }

    /**
     * Nodes nested inside each other.
     *
     * @param levels How many nodes deep below the root.
     * @return The bytes.
     */
    private static byte[] nested(int levels) throws IOException {
        byte[] node = sized(new byte[0]);
        for (int i = 0; i < levels; ++i) {
            ByteArrayOutputStream entry = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(entry);
            header(out, NodeType.NODE);
            out.write(node);
            node = sized(entry.toByteArray());
        }
        return node;
    }

    @Test
    void nestingIsLimited() throws IOException {
        assertTrue(read(nested(10)).isPresent(), "Ordinary nesting reads");
        assertRefused(nested(TreeBinarySerialization.MAX_DEPTH + 1));
        // Deep enough that reading without a limit would risk the stack
        assertRefused(nested(2_000));
    }

    @Test
    void unknownTypesAndShortInputAreRefused() throws IOException {
        assertRefused(
                root(
                        out -> {
                            out.writeByte(99);
                            out.writeInt(KEY.length);
                            out.write(KEY);
                        }));
        byte[] whole =
                root(
                        out -> {
                            header(out, NodeType.LONG);
                            out.writeLong(5);
                        });
        for (int cut = 0; cut < whole.length; ++cut) {
            byte[] part = new byte[cut];
            System.arraycopy(whole, 0, part, 0, cut);
            assertRefused(part);
        }
        assertTrue(read(whole).isPresent());
    }

    @Test
    void rootSizeMustCoverItself() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeLong(3);
        assertRefused(bytes.toByteArray());
    }
}
