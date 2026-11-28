package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetType;
import com.ikalagaming.converter.index.EditorTypes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What a slot takes, what its picker lists, and texture files' normal map marks. */
class AssetSlotTest {
    private static final Set<String> TEXTURES = Set.of(EditorTypes.TEXTURE, EditorTypes.IMAGE);

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void slotsTakeTheirTypesAndThePickerListsOnlyThose(@TempDir Path temp) throws Exception {
        final Path data = temp.resolve("plugins/Base/data");
        final Path art = temp.resolve("art");
        write(data.resolve("textures/block/stone.ika"), "ika");
        write(data.resolve("render_materials/block/stone.ika"), "ika");
        write(art.resolve("stone.png"), "png");
        final List<AssetType> types = new ArrayList<>(EditorTypes.all());
        final AssetIndex.Snapshot snapshot =
                new AssetIndex(types)
                        .scan(List.of(AssetIndex.Root.of(data), AssetIndex.Root.of(art)), null);

        final Path texture = data.resolve("textures/block/stone.ika").toAbsolutePath();
        final Path material = data.resolve("render_materials/block/stone.ika").toAbsolutePath();
        assertTrue(AssetSlot.accepts(snapshot, TEXTURES, texture));
        assertTrue(
                AssetSlot.accepts(snapshot, TEXTURES, art.resolve("stone.png").toAbsolutePath()));
        assertFalse(AssetSlot.accepts(snapshot, TEXTURES, material), "A render material isn't one");
        assertFalse(AssetSlot.accepts(snapshot, TEXTURES, temp.resolve("elsewhere.png")));

        final List<Path> listed =
                AssetSlot.candidates(snapshot, TEXTURES, "stone").stream()
                        .map(AssetIndex.Entry::file)
                        .toList();
        assertEquals(2, listed.size(), listed.toString());
        assertFalse(listed.contains(material));
    }

    @Test
    void aNormalMapMarkIsLeftOffTheFile() {
        final Path file = Path.of("textures", "stone.ika");
        assertEquals(file, AssetSlot.withoutMark(Path.of(file + "#normal")));
        assertEquals(file, AssetSlot.withoutMark(file));
    }
}
