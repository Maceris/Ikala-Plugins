package com.ikalagaming.graphics.ui.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Sizing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SpecLoaderTest {

    private static UiSpec load(String yaml) {
        return SpecLoader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test");
    }

    private static String failure(String yaml) {
        return assertThrows(SpecException.class, () -> load(yaml)).getMessage();
    }

    private static final String SURFACE = "surface: { id: test }\n";

    @Test
    void surfaceSettingsLoad() {
        UiSpec spec =
                load(
                        """
                        surface:
                          id: menu
                          anchors: { at: [1, 0], offset: [-8, 8] }
                          width: { grow: 2, min: 100, max: 400 }
                          height: "50%"
                          layer: background
                          movable: true
                          classes: [one, two]
                          style: { background: "#FF0000" }
                        content: { type: column, id: root }
                        """);

        UiSpec.SurfaceSpec surface = spec.surface();
        assertEquals("menu", surface.id());
        assertEquals(Anchors.at(1, 0).offset(Length.u(-8), Length.u(8)), surface.anchors());
        assertEquals(Sizing.grow(2, Length.u(100), Length.u(400)), surface.width());
        assertEquals(Sizing.percent(0.5f), surface.height());
        assertEquals(Layer.BACKGROUND, surface.layer());
        assertTrue(surface.movable());
        assertEquals(List.of("one", "two"), surface.classes());
        assertTrue(!surface.style().isEmpty());
        assertTrue(spec.files().isEmpty(), "Not from a file");
    }

    @Test
    void nodesKeepTheirPropertiesAndChildren() {
        UiSpec spec =
                load(
                        SURFACE
                                +
                                """
                                content:
                                  type: column
                                  id: root
                                  gap: 8
                                  children:
                                    - { type: label, id: title, text: Hello }
                                    - { type: button, id: go, text: Go, onClick: start }
                                """);

        NodeSpec root = spec.content();
        assertEquals("column", root.type());
        assertEquals(8, root.properties().get("gap"));
        assertEquals(2, root.children().size());
        assertEquals("go", root.children().get(1).id());
        assertEquals("start", root.children().get(1).properties().get("onClick"));
        assertEquals("content.children[1]", root.children().get(1).path());
    }

    @Test
    void templatesExpandWithTypedParameters() {
        UiSpec spec =
                load(
                        SURFACE
                                +
                                """
                                templates:
                                  labelled:
                                    params: [id, text, size]
                                    node: { type: label, id: "${id}", text: "Item: ${text}", width: "${size}" }
                                  pair:
                                    params: [name]
                                    node:
                                      type: row
                                      id: "${name}"
                                      children:
                                        - use: labelled
                                          with: { id: "${name}-label", text: "${name}", size: 120 }
                                content:
                                  type: column
                                  id: root
                                  children:
                                    - use: pair
                                      with: { name: first }
                                """);

        NodeSpec pair = spec.content().children().getFirst();
        assertEquals("row", pair.type());
        assertEquals("first", pair.id());
        NodeSpec label = pair.children().getFirst();
        assertEquals("first-label", label.id());
        assertEquals("Item: first", label.properties().get("text"));
        assertEquals(120, label.properties().get("width"), "A whole parameter keeps its type");
        assertTrue(label.path().contains("<labelled>"), label.path());
    }

    @Test
    void repeatsExpandTheirTemplate() {
        UiSpec spec =
                load(
                        SURFACE
                                +
                                """
                                templates:
                                  row: { node: { type: label, id: row, text: "{file.name}" } }
                                content:
                                  type: column
                                  id: files
                                  repeat: { list: files, as: file, template: row, key: "{file.path}" }
                                """);

        NodeSpec.RepeatSpec repeat = spec.content().repeat();
        assertNotNull(repeat);
        assertEquals("files", repeat.list());
        assertEquals("file", repeat.alias());
        assertEquals("label", repeat.item().type());
        assertEquals("{file.path}", repeat.key());
    }

    @Test
    void templatesCanBeImported(@TempDir Path folder) throws IOException {
        Files.writeString(
                folder.resolve("common.yml"),
                """
                templates:
                  shared: { node: { type: label, id: shared, text: Shared } }
                """);
        Path spec = folder.resolve("menu.yml");
        Files.writeString(
                spec,
                SURFACE
                        +
                        """
                        templates:
                          import: [common.yml]
                        content: { use: shared }
                        """);

        UiSpec loaded = SpecLoader.load(spec);

        assertEquals("shared", loaded.content().id());
        assertEquals(2, loaded.files().size(), "The spec and its import");
    }

    @Test
    void problemsSayWhereTheyAre() {
        assertTrue(
                failure("surface: { id: x }\ncontent: {type: column, id: r}\nextra: 1")
                        .contains("unknown key 'extra'"));
        assertTrue(failure(SURFACE).contains("missing 'content'"));
        assertTrue(failure("content: { type: column, id: r }").contains("missing 'surface'"));
        assertTrue(
                failure(SURFACE + "content: { type: column }").contains("content: missing 'id'"));
        assertTrue(failure(SURFACE + "content: { use: nope }").contains("unknown template 'nope'"));
        assertTrue(
                failure(
                                SURFACE
                                        +
                                        """
                                        templates:
                                          t: { params: [a], node: { type: label, id: x } }
                                        content: { use: t }
                                        """)
                        .contains("content.with: missing parameter 'a' for template t"));
        assertTrue(
                failure(
                                SURFACE
                                        +
                                        """
                                        templates:
                                          t: { node: { type: label, id: x } }
                                        content: { use: t, with: { b: 1 } }
                                        """)
                        .contains("content.with.b: template t has no parameter b"));
        assertTrue(
                failure(
                                SURFACE
                                        +
                                        """
                                        templates:
                                          t: { node: { type: label, id: "${missing}" } }
                                        content: { use: t }
                                        """)
                        .contains("templates.t.node.id: unknown parameter 'missing'"));
        assertTrue(
                failure(
                                SURFACE
                                        +
                                        """
                                        templates:
                                          a: { node: { use: b } }
                                          b: { node: { use: a } }
                                        content: { use: a }
                                        """)
                        .contains("template cycle"));
        assertTrue(
                failure("surface: { id: x, width: wide }\ncontent: {type: column, id: r}")
                        .contains("surface.width"));
        assertTrue(
                failure("surface: { id: x, anchors: middle }\ncontent: {type: column, id: r}")
                        .contains("unknown anchor preset 'middle'"));
        assertTrue(
                failure(
                                SURFACE
                                        + "content: { type: column, id: r, repeat: { as: x, template: t } }")
                        .contains("missing 'list'"));
        assertTrue(
                failure(SURFACE + "templates: { import: [a.yml] }\ncontent: {type: column, id: r}")
                        .contains("imports need a spec loaded from a file"));
        assertTrue(failure("").contains("empty"));
    }

    @Test
    void textTemplatesParseBindingsAndBraces() {
        TextTemplate template = TextTemplate.parse("HP {player.health} of {{max}}", "here");

        assertEquals(3, template.parts().size());
        assertEquals(
                "HP 7 of {max}", template.render(name -> "player.health".equals(name) ? 7 : null));
        assertTrue(TextTemplate.parse("{flag}", "here").isSingleBinding());
        assertThrows(SpecException.class, () -> TextTemplate.parse("{open", "here"));
        assertNull(null);
    }
}
