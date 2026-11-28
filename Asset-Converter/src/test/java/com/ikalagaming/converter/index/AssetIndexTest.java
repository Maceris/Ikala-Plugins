package com.ikalagaming.converter.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The asset index: types, references both ways, overrides and missing references. */
class AssetIndexTest {
    /** Lotomation's content folder, beside Ikala-Plugins. */
    private static final Path LOTOMATION =
            Path.of("../../Lotomation/plugins/Lotomation/data").toAbsolutePath().normalize();

    /** Lotomation's art sources. */
    private static final Path ART = Path.of("../../Lotomation/art").toAbsolutePath().normalize();

    private static AssetIndex index() {
        final List<AssetType> types = new ArrayList<>(GameTypes.all());
        types.addAll(EditorTypes.all());
        return new AssetIndex(types);
    }

    @Test
    void whatUsesEachMaterialAndTextureMatchesASearchOfTheLooks() throws Exception {
        assumeTrue(Files.isDirectory(LOTOMATION), "Lotomation isn't beside Ikala-Plugins");
        final AssetIndex.Snapshot snapshot =
                index().scan(
                                List.of(AssetIndex.Root.of(LOTOMATION), AssetIndex.Root.of(ART)),
                                null);
        final List<Path> looks;
        try (Stream<Path> files = Files.walk(LOTOMATION.resolve("looks"))) {
            looks = files.filter(f -> f.toString().endsWith(".kvt")).toList();
        }
        int checked = 0;
        for (AssetIndex.Entry entry : snapshot.entries()) {
            final String key =
                    switch (entry.type()) {
                        case EditorTypes.RENDER_MATERIAL -> "material:";
                        case EditorTypes.TEXTURE -> "texture:";
                        default -> null;
                    };
            if (key == null) {
                continue;
            }
            // The grep: looks whose text names the ID
            final Set<Path> grep = new TreeSet<>();
            for (Path look : looks) {
                final String text = Files.readString(look);
                if (text.contains(key + "\"" + entry.id() + "\"")
                        || text.contains(key + "\"" + entry.id() + "#")) {
                    grep.add(look.toAbsolutePath().normalize());
                }
            }
            final Set<Path> users =
                    snapshot.usedBy(entry.file()).stream()
                            .filter(link -> link.file().toString().endsWith(".kvt"))
                            .map(AssetIndex.Link::file)
                            .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(grep, users, entry.id());
            ++checked;
        }
        assertTrue(checked >= 20, "Every material and texture: " + checked);
        assertEquals(List.of(), snapshot.problems(), "Lotomation's references all resolve");

        // A block's material, its look, and a graph's shipped texture
        final AssetIndex.Entry stone = snapshot.entry(LOTOMATION.resolve("blocks/stone.kvt"));
        assertNotNull(stone);
        assertTrue(
                snapshot.uses(stone.file()).stream()
                        .anyMatch(l -> "look".equals(l.label()) && l.file() != null));
        final Path graph = ART.resolve("textures/block/stone.texgraph.yml");
        assertTrue(
                snapshot.uses(graph).stream()
                        .anyMatch(
                                l ->
                                        "makes".equals(l.label())
                                                && LOTOMATION
                                                        .resolve("textures/block/stone.ika")
                                                        .equals(l.file())),
                snapshot.uses(graph).toString());
    }

    @Test
    void missingReferencesAreProblemsAndLaterFoldersWin(@TempDir Path temp) throws Exception {
        final Path base = temp.resolve("plugins/Base/data");
        final Path mod = temp.resolve("plugins/Mod/data");
        write(base.resolve("textures/block/rock.png"), "png");
        write(base.resolve("models/block/cube.ika"), "ika");
        write(
                base.resolve("looks/block/rock.kvt"),
                "{model:\"base:block/cube\", texture:\"base:block/rock\"}");
        write(
                base.resolve("looks/block/odd.kvt"),
                "{model:\"base:block/cube\", texture:\"base:block/nothing\"}");
        write(mod.resolve("overrides/base/textures/block/rock.png"), "a better png");
        final AssetIndex index = index();
        final List<AssetIndex.Root> roots =
                List.of(AssetIndex.Root.of(base), AssetIndex.Root.of(mod));
        final AssetIndex.Snapshot snapshot = index.scan(roots, null);

        assertEquals(1, snapshot.problems().size(), snapshot.problems().toString());
        final AssetIndex.Problem problem = snapshot.problems().getFirst();
        assertEquals(
                base.resolve("looks/block/odd.kvt").toAbsolutePath().normalize(), problem.file());
        assertEquals("base:block/nothing", problem.name());

        final Path override =
                mod.resolve("overrides/base/textures/block/rock.png").toAbsolutePath();
        assertEquals("base:block/rock", snapshot.entry(override).id());
        assertEquals(
                List.of(base.resolve("textures/block/rock.png").toAbsolutePath(), override),
                snapshot.providers(override),
                "The later folder's file wins, last");
        assertEquals(
                override,
                snapshot.uses(base.resolve("looks/block/rock.kvt")).stream()
                        .filter(l -> "texture".equals(l.label()))
                        .findFirst()
                        .orElseThrow()
                        .file(),
                "The look gets the winning file");
    }

    @Test
    void theCacheIsReusedForUnchangedFilesOnly(@TempDir Path temp) throws Exception {
        final Path base = temp.resolve("plugins/Base/data");
        write(base.resolve("models/block/cube.ika"), "ika");
        final Path look = base.resolve("looks/block/rock.kvt");
        write(look, "{model:\"base:block/cube\", texture:\"base:block/rock\"}");
        final Path other = base.resolve("looks/block/other.kvt");
        write(other, "{model:\"base:block/cube\"}");
        final AssetIndex index = index();
        final List<AssetIndex.Root> roots = List.of(AssetIndex.Root.of(base));
        final Path cache = temp.resolve("cache/asset-index.yml");
        AssetIndex.save(index.scan(roots, null), cache);
        final AssetIndex.Snapshot loaded = index.load(cache, roots);
        assertNotNull(loaded);
        assertEquals(1, loaded.problems().size(), "The texture is missing");

        write(base.resolve("textures/block/rock.png"), "png");
        write(look, "{model:\"base:block/cube\"}");
        Files.setLastModifiedTime(look, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        final AssetIndex.Snapshot again = index.scan(roots, loaded);
        assertEquals(List.of(), again.problems(), "The changed look was read again");
        assertSame(
                loaded.entry(other), again.entry(other), "The unchanged one came from the cache");
        assertFalse(again.usedBy(base.resolve("models/block/cube.ika")).isEmpty());
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
