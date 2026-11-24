package com.ikalagaming.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.MaterialSection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Tests for converting assets between binary and text. */
class AssetConvertTest {

    /** Text to binary and back gives the same text, and the binary holds the values. */
    @Test
    void testRoundTrip(@TempDir Path folder) throws IOException {
        final Path text = folder.resolve("grass.material.yml");
        Files.writeString(text, "roughness: 1\nspecular: 0.35\nsubsurface: 0.3\n");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(
                0,
                AssetConvert.run(
                        List.of(text.toString()), new PrintStream(out), new PrintStream(err)),
                err.toString(StandardCharsets.UTF_8));
        final Path binary = folder.resolve("grass.ika");
        final MaterialSection.Stored stored = MaterialSection.read(AssetFile.open(binary));
        assertEquals(1f, stored.material().getRoughness());
        assertEquals(0.35f, stored.material().getSpecular());
        assertEquals(0.3f, stored.material().getSubsurface());
        assertEquals(0f, stored.material().getMetallic());

        final Path back = folder.resolve("back.material.yml");
        assertEquals(
                0,
                AssetConvert.run(
                        List.of(binary.toString(), "-o", back.toString()),
                        new PrintStream(out),
                        new PrintStream(err)));
        final String written = Files.readString(back);
        assertTrue(written.contains("roughness: 1\n"), written);
        assertTrue(written.contains("specular: 0.35\n"), written);
        AssetConvert.convert(back, binary);
        AssetConvert.convert(binary, back);
        assertEquals(written, Files.readString(back));
    }

    /** Files with other endings, bad text and wrong arguments fail without stopping the rest. */
    @Test
    void testFailures(@TempDir Path folder) throws IOException {
        final Path bad = folder.resolve("bad.material.yml");
        Files.writeString(bad, "rough: 1\n");
        final Path good = folder.resolve("good.material.yml");
        Files.writeString(good, "metallic: 1\n");
        final PrintStream quiet = new PrintStream(new ByteArrayOutputStream());
        assertEquals(
                1,
                AssetConvert.run(List.of(bad.toString(), "x.png", good.toString()), quiet, quiet));
        assertTrue(Files.exists(folder.resolve("good.ika")));
        assertEquals(2, AssetConvert.run(List.of(), quiet, quiet));
        assertEquals(2, AssetConvert.run(List.of("a.ika", "b.ika", "-o", "c"), quiet, quiet));
    }
}
