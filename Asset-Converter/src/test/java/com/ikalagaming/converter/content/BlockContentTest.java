package com.ikalagaming.converter.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Reading block looks from a content folder laid out as the game's. */
class BlockContentTest {
    @Test
    void aLookAndTheFilesItNames(@TempDir Path root) throws IOException {
        Path data = root.resolve("plugins").resolve("Testmod").resolve("data");
        Path look = data.resolve("looks/block/grass.kvt");
        Files.createDirectories(look.getParent());
        Files.writeString(
                look,
                "{model:\"testmod:block/cube_bottom_top\", texture:\"testmod:block/grass\","
                        + " material:\"testmod:block/grass\"}");
        Path model = data.resolve("models/block/cube_bottom_top.ika");
        Path texture = data.resolve("textures/block/grass.png");
        Path normal = data.resolve("textures/block/grass.normal.png");
        Path material = data.resolve("render_materials/block/grass.ika");
        for (Path file : new Path[] {model, texture, normal, material}) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }

        BlockContent.Look read = BlockContent.readLook(look).orElseThrow();
        assertEquals("testmod:block/grass", read.texture());
        assertEquals("testmod:grass", read.id(), "Its ID from where it is");
        assertEquals(Optional.of(model), BlockContent.modelFile(read));
        assertEquals(Optional.of(texture), BlockContent.textureFile(read));
        assertEquals(Optional.of(normal), BlockContent.normalFile(read));
        assertEquals(Optional.of(material), BlockContent.materialFile(read));
        assertTrue(
                BlockContent.look("testmod:grass").isPresent(),
                "The folder is known once a look in it was opened");
    }

    @Test
    void aLookWithoutAModelIsACube() {
        BlockContent.Look look = BlockContent.parse("x:glow", "{texture:\"x:block/glow\"}").get();
        assertEquals(BlockContent.DEFAULT_MODEL, look.model());
        assertEquals(null, look.material());
    }

    @Test
    void aFileOutsideTheLooksHasNoId(@TempDir Path root) {
        assertTrue(
                BlockContent.idOf(root.resolve("grass.kvt"), BlockContent.LOOKS, ".kvt").isEmpty());
    }
}
