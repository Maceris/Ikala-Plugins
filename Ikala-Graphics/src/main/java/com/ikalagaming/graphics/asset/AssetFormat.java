package com.ikalagaming.graphics.asset;

/**
 * Constants describing the layout of the asset container. See the {@link
 * com.ikalagaming.graphics.asset package documentation} for the layout itself.
 */
public final class AssetFormat {
    /** The bytes every container starts with. Like PNG, this catches text mode line mangling. */
    static final byte[] MAGIC = {(byte) 0x89, 'I', 'K', 'A', 0x0D, 0x0A, 0x1A, 0x0A};

    /** The newest major format version this code reads and writes. */
    public static final int FORMAT_MAJOR = 1;

    /** The minor format version this code writes. */
    public static final int FORMAT_MINOR = 0;

    /** The size of the header written by this version, and the smallest header we accept. */
    static final int HEADER_SIZE = 48;

    /** The size of a table entry written by this version, and the smallest entry we accept. */
    static final int TABLE_ENTRY_SIZE = 32;

    /** Section payloads start on a multiple of this many bytes. */
    public static final int ALIGNMENT = 16;

    /** The file extension used for containers. */
    public static final String EXTENSION = ".ika";

    /** Header field offsets. */
    static final int HEADER_FORMAT_MAJOR = 8;

    static final int HEADER_FORMAT_MINOR = 10;
    static final int HEADER_HEADER_SIZE = 12;
    static final int HEADER_FLAGS = 16;
    static final int HEADER_SECTION_COUNT = 20;
    static final int HEADER_TABLE_ENTRY_SIZE = 24;
    static final int HEADER_TABLE_OFFSET = 32;
    static final int HEADER_FILE_LENGTH = 40;

    /** Table entry field offsets. */
    static final int ENTRY_TAG = 0;

    static final int ENTRY_VERSION = 4;
    static final int ENTRY_FLAGS = 6;
    static final int ENTRY_OFFSET = 8;
    static final int ENTRY_LENGTH = 16;
    static final int ENTRY_CRC = 24;

    /**
     * Round a size up to the section alignment.
     *
     * @param size The size or offset to round.
     * @return The next multiple of {@link #ALIGNMENT} at or after the size.
     */
    static long align(long size) {
        return (size + ALIGNMENT - 1) & -ALIGNMENT;
    }

    /** Constants only. */
    private AssetFormat() {}
}
