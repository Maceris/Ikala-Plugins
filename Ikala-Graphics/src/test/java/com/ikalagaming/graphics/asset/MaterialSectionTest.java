package com.ikalagaming.graphics.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.Material;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/** Tests for material sections and their text form. */
class MaterialSectionTest {

    /**
     * A material with every value set to something other than its default.
     *
     * @return The material.
     */
    private static Material everything() {
        final Material material = new Material();
        material.setTransparency(Material.Transparency.CUTOUT);
        material.getBaseColor().set(0.1f, 0.2f, 0.3f, 0.4f);
        material.getEmissive().set(5, 6, 7);
        material.setAnisotropic(0.11f);
        material.setClearcoat(0.12f);
        material.setClearcoatGloss(0.13f);
        material.setMetallic(0.14f);
        material.setRoughness(0.15f);
        material.setSheen(0.16f);
        material.setSheenTint(0.17f);
        material.setSpecular(0.18f);
        material.setSpecularTint(0.19f);
        material.setSubsurface(0.2f);
        material.setNormalGreenDown(true);
        material.setTriplanar(true);
        return material;
    }

    /** The names used with {@link #everything()}. */
    private static final MaterialSection.Textures NAMES =
            new MaterialSection.Textures("color.png", null, "../maps/ä orm.png", "glow.png");

    /**
     * Check two materials hold the same values.
     *
     * @param expected What they should be.
     * @param actual What they are.
     */
    private static void assertSame(Material expected, Material actual) {
        assertEquals(expected.getTransparency(), actual.getTransparency());
        assertEquals(expected.getBaseColor(), actual.getBaseColor());
        assertEquals(expected.getEmissive(), actual.getEmissive());
        assertEquals(expected.getAnisotropic(), actual.getAnisotropic());
        assertEquals(expected.getClearcoat(), actual.getClearcoat());
        assertEquals(expected.getClearcoatGloss(), actual.getClearcoatGloss());
        assertEquals(expected.getMetallic(), actual.getMetallic());
        assertEquals(expected.getRoughness(), actual.getRoughness());
        assertEquals(expected.getSheen(), actual.getSheen());
        assertEquals(expected.getSheenTint(), actual.getSheenTint());
        assertEquals(expected.getSpecular(), actual.getSpecular());
        assertEquals(expected.getSpecularTint(), actual.getSpecularTint());
        assertEquals(expected.getSubsurface(), actual.getSubsurface());
        assertEquals(expected.isNormalGreenDown(), actual.isNormalGreenDown());
        assertEquals(expected.isPackedOcclusion(), actual.isPackedOcclusion());
        assertEquals(expected.isTriplanar(), actual.isTriplanar());
    }

    /** Every value and name comes back from a file as it went in. */
    @Test
    void testFileRoundTrip() {
        final ByteBuffer bytes =
                MaterialSection.file(everything(), NAMES, AssetMetadata.of("test", "1"));
        final MaterialSection.Stored read = MaterialSection.read(AssetFile.read(bytes));
        assertSame(everything(), read.material());
        assertEquals(NAMES, read.textures());
    }

    /** Text holds the same as the binary form, both ways, byte for byte. */
    @Test
    void testTextRoundTrip() {
        final String text = MaterialText.write(everything(), NAMES);
        final MaterialSection.Stored read = MaterialText.read(text);
        assertSame(everything(), read.material());
        assertEquals(NAMES, read.textures());
        assertEquals(text, MaterialText.write(read.material(), read.textures()));
        assertEquals(
                MaterialSection.encode(everything(), NAMES),
                MaterialSection.encode(read.material(), read.textures()));
    }

    /** Numbers are written short, as typed. */
    @Test
    void testShortNumbers() {
        final Material material = MaterialText.defaults();
        material.setRoughness(0.8f);
        final String text = MaterialText.write(material, MaterialSection.Textures.NONE);
        assertTrue(text.contains("roughness: 0.8\n"), text);
        assertTrue(text.contains("baseColor: [1, 1, 1, 1]\n"), text);
        assertTrue(!text.contains("textures"), text);
    }

    /** Missing keys take the defaults: a matte non-metal reflecting 4%. */
    @Test
    void testDefaults() {
        final MaterialSection.Stored read = MaterialText.read("roughness: 0.3\n");
        final Material expected = MaterialText.defaults();
        expected.setRoughness(0.3f);
        assertSame(expected, read.material());
        assertEquals(0.5f, read.material().getSpecular());
        assertNull(read.textures().baseColor());
        assertSame(MaterialText.defaults(), MaterialText.read("").material());
    }

    /** Unknown keys and values of the wrong kind say what is wrong. */
    @Test
    void testBadText() {
        assertThrows(AssetFormatException.class, () -> MaterialText.read("rough: 1"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("roughness: high"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("baseColor: [1, 1]"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("transparency: glass"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("textures: {bump: a}"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("- a list"));
        assertThrows(AssetFormatException.class, () -> MaterialText.read("a: [unclosed"));
    }

    /** A record from a later version, with more fields, still reads. */
    @Test
    void testLongerRecord() {
        final ByteBuffer encoded = MaterialSection.encode(everything(), NAMES);
        final ByteBuffer longer =
                ByteBuffer.allocate(encoded.remaining() + 8)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        longer.putInt(MaterialSection.RECORD_SIZE + 8);
        longer.put(encoded.slice(4, MaterialSection.RECORD_SIZE - 4));
        longer.putLong(0);
        longer.put(
                encoded.slice(
                        MaterialSection.RECORD_SIZE,
                        encoded.limit() - MaterialSection.RECORD_SIZE));
        final MaterialSection.Stored read =
                MaterialSection.decode(
                        new Section(
                                MaterialSection.TAG, MaterialSection.VERSION, 1, longer.flip()));
        assertSame(everything(), read.material());
        assertEquals(NAMES, read.textures());
    }

    /** Cut short or unknown sections are errors. */
    @Test
    void testBadSection() {
        final ByteBuffer encoded = MaterialSection.encode(everything(), NAMES);
        assertThrows(
                AssetFormatException.class,
                () ->
                        MaterialSection.decode(
                                new Section(
                                        MaterialSection.TAG,
                                        MaterialSection.VERSION,
                                        1,
                                        encoded.slice(0, encoded.limit() - 3))));
        assertThrows(
                AssetFormatException.class,
                () ->
                        MaterialSection.decode(
                                new Section(MaterialSection.TAG, 2, 1, encoded.duplicate())));
        assertThrows(
                AssetFormatException.class,
                () -> MaterialSection.decode(new Section(MeshSection.TAG, 1, 1, encoded)));
    }
}
