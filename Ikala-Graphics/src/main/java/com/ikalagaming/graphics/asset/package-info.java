/**
 * The binary asset container that every asset and runtime cache is stored in.
 *
 * <p>A container is a fixed header, a table of sections, and the section payloads. Each section has
 * a four character tag, its own version, flags, and a CRC-32C of its payload. Everything is
 * little-endian, and payloads start on a 16 byte boundary, so bulk data such as vertices can be
 * copied to the GPU without being parsed. Every container has exactly one {@link
 * com.ikalagaming.graphics.asset.SectionTag#META META} section, which records which exporter wrote
 * it.
 *
 * <h2>Header (48 bytes in format 1.0)</h2>
 *
 * <pre>
 *  0  magic            89 'I' 'K' 'A' 0D 0A 1A 0A
 *  8  u16 formatMajor, u16 formatMinor
 * 12  u32 headerSize   48 in 1.0
 * 16  u32 flags        0 in 1.0
 * 20  u32 sectionCount
 * 24  u32 tableEntrySize 32 in 1.0
 * 28  u32 reserved
 * 32  u64 tableOffset
 * 40  u64 fileLength
 * </pre>
 *
 * <h2>Table entry (32 bytes in format 1.0)</h2>
 *
 * <pre>
 *  0  u32 tag          four ASCII characters, first character in the lowest byte
 *  4  u16 sectionVersion
 *  6  u16 flags        bit 0: required
 *  8  u64 offset       a multiple of 16
 * 16  u64 length
 * 24  u32 crc32c       of the payload
 * 28  u32 reserved
 * </pre>
 *
 * <h2>Compatibility rules</h2>
 *
 * <ul>
 *   <li>A reader rejects a newer major format version. A minor version only appends fields to the
 *       header or table entries, and readers skip what they don't know using {@code headerSize} and
 *       {@code tableEntrySize}.
 *   <li>Sections are versioned on their own. A reader that does not know a section's tag or version
 *       skips it, unless the section is marked required, in which case the file is rejected.
 *   <li>Inside a section, a new version keeps the old fields where they were. Fixed records start
 *       with their own size, so readers can skip trailing fields they don't know. A breaking change
 *       gets a new section version, and readers keep decoding the versions they still support.
 *   <li>A tag may appear more than once (for example one section per mesh), and the order of
 *       sections is kept.
 * </ul>
 *
 * <p>Java buffers are big-endian unless told otherwise, so every buffer this package hands out is
 * set to little-endian. Bulk copies of section data are unaffected by byte order.
 *
 * @see com.ikalagaming.graphics.asset.AssetFile
 * @see com.ikalagaming.graphics.asset.AssetWriter
 * @see com.ikalagaming.graphics.asset.AssetValidator
 */
package com.ikalagaming.graphics.asset;
