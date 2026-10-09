package com.ikalagaming.graphics.asset;

/**
 * The parts of a container header that matter after the file is read.
 *
 * @param formatMajor The major format version the file was written with.
 * @param formatMinor The minor format version the file was written with.
 * @param flags Container flags, unused so far.
 * @param fileLength The length of the whole file in bytes.
 */
public record AssetHeader(int formatMajor, int formatMinor, int flags, long fileLength) {}
