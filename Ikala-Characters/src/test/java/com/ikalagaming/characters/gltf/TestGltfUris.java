package com.ikalagaming.characters.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;

/** Tests reading the URIs a glTF file names. */
class TestGltfUris {

    @Test
    void decodesPercentEscapesButKeepsPlusSigns() throws IOException {
        assertEquals("a b+c.bin", GltfFile.percentDecode("a%20b+c.bin"));
        assertEquals("textures/café.png", GltfFile.percentDecode("textures/caf%C3%A9.png"));
        assertEquals("plain.bin", GltfFile.percentDecode("plain.bin"));
        assertEquals("../x.bin", GltfFile.percentDecode("%2E%2E/x.bin"));
    }

    @Test
    void rejectsBrokenEscapes() {
        assertThrows(IOException.class, () -> GltfFile.percentDecode("a%2"));
        assertThrows(IOException.class, () -> GltfFile.percentDecode("a%zz.bin"));
    }
}
