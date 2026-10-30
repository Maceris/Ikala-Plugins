package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.registry.ContentSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Tests for the checks the Content tab makes before adding a folder.
 *
 * @author Ches Burks
 */
class TestContentPanel {
    /** Namespaces come from the folder's name, made lowercase with odd characters replaced. */
    @Test
    void testSuggestNamespace() {
        Assertions.assertEquals(
                "characters_gen", ContentPanel.suggestNamespace(Path.of("D:/art/characters_gen")));
        Assertions.assertEquals(
                "my_hats-2", ContentPanel.suggestNamespace(Path.of("D:/art/ My Hats-2!")));
        Assertions.assertEquals("", ContentPanel.suggestNamespace(Path.of("D:/art/!!!")));
    }

    /** A folder needs a valid namespace that isn't loaded, and can't be loaded twice. */
    @Test
    void testAddProblem() {
        List<ContentSource> loaded =
                List.of(new ContentSource("test", Path.of("D:/art/characters")));
        Assertions.assertNull(
                ContentPanel.addProblem(loaded, Path.of("D:/art/characters_gen"), "gen"));
        Assertions.assertNotNull(
                ContentPanel.addProblem(loaded, Path.of("D:/art/characters_gen"), "Gen"));
        Assertions.assertNotNull(
                ContentPanel.addProblem(loaded, Path.of("D:/art/characters_gen"), ""));
        Assertions.assertNotNull(
                ContentPanel.addProblem(loaded, Path.of("D:/art/characters_gen"), "test"));
        Assertions.assertNotNull(
                ContentPanel.addProblem(loaded, Path.of("D:/art/./characters"), "other"));
    }

    /**
     * A folder's namespace is the one its definitions use, whatever the folder is called.
     *
     * @param temp An empty folder.
     * @throws Exception If the test content can't be found.
     */
    @Test
    void testNamespaceUsed(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        Path content = Path.of(TestContentPanel.class.getResource("/content/ikala").toURI());
        Assertions.assertEquals("ikala", ContentPanel.namespaceUsed(content));
        Assertions.assertNull(ContentPanel.namespaceUsed(temp));
    }
}
