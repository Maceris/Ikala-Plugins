package com.ikalagaming.graphics.ui.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.DockLayout;
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
import java.util.Map;

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
    void panelSettingsLoad() {
        UiSpec spec =
                load(
                        "panel: { id: tools/files, title: Files, anchors: top-left, width: 300 }\n"
                                + "content: { type: column, id: root }\n");
        assertEquals("tools/files", spec.surface().id());
        assertEquals("Files", spec.surface().title());
        assertNull(load(SURFACE + "content: { type: column, id: root }\n").surface().title());
    }

    @Test
    void panelProblemsAreReported() {
        assertTrue(
                failure("panel: { id: p }\ncontent: { type: column, id: root }\n")
                        .contains("panel: missing 'title'"));
        assertTrue(
                failure(
                                "panel: { id: p, title: P, layer: overlay }\n"
                                        + "content: { type: column, id: root }\n")
                        .contains("panel.layer: unknown panel property"));
        assertTrue(
                failure(
                                SURFACE
                                        + "panel: { id: p, title: P }\n"
                                        + "content: { type: column, id: root }\n")
                        .contains("not both"));
    }

    @Test
    void dockLayoutLoads() {
        DockLayout layout =
                NodeTypes.dockLayout(
                        Map.of(
                                "splits",
                                List.of(
                                        Map.of("side", "left", "fraction", 0.2, "panels", "a"),
                                        Map.of(
                                                "side",
                                                "bottom",
                                                "fraction",
                                                0.4,
                                                "panels",
                                                List.of("b", "c"),
                                                "ofPrevious",
                                                true)),
                                "center",
                                List.of("d")),
                        "content.layout");
        assertEquals(2, layout.getSplits().size());
        assertEquals(List.of("b", "c"), layout.getSplits().get(1).panels());
        assertTrue(layout.getSplits().get(1).ofPrevious());
        assertEquals(List.of("d"), layout.getCenter());
        SpecException bad =
                assertThrows(
                        SpecException.class,
                        () ->
                                NodeTypes.dockLayout(
                                        Map.of(
                                                "splits",
                                                List.of(
                                                        Map.of(
                                                                "side", "left",
                                                                "fraction", 1.5,
                                                                "panels", "a"))),
                                        "content.layout"));
        assertTrue(bad.getMessage().startsWith("content.layout.splits[0]: "), bad.getMessage());
    }

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

    /**
     * A spec whose templates each use the one before, built from numbered template bodies.
     *
     * @param name The template name prefix.
     * @param first The body of template 0.
     * @param next Makes the body of template k from the name of template k - 1.
     * @param count How many templates follow the first.
     * @param use How the content uses the last template.
     * @return The spec.
     */
    private static String chain(
            String name,
            String first,
            java.util.function.Function<String, String> next,
            int count,
            String use) {
        StringBuilder spec = new StringBuilder(SURFACE).append("templates:\n");
        spec.append("  ").append(name).append("0: ").append(first).append('\n');
        for (int k = 1; k <= count; ++k) {
            spec.append("  ")
                    .append(name)
                    .append(k)
                    .append(": ")
                    .append(next.apply(name + (k - 1)))
                    .append('\n');
        }
        return spec.append("content: { use: ")
                .append(name)
                .append(count)
                .append(use)
                .append(" }\n")
                .toString();
    }

    @Test
    void templatesThatMultiplyAreStopped() {
        String tenUses = String.join(", ", java.util.Collections.nCopies(10, "{ use: %s }"));
        String spec =
                chain(
                        "t",
                        "{ node: { type: label, id: x } }",
                        previous ->
                                "{ node: { type: column, id: c, children: ["
                                        + tenUses.replace("%s", previous)
                                        + "] } }",
                        5,
                        "");
        assertTrue(failure(spec).contains("more than " + SpecLoader.MAX_NODES + " nodes"));
    }

    @Test
    void templatesThatNestTooDeepAreStopped() {
        String spec =
                chain(
                        "d",
                        "{ node: { type: label, id: x } }",
                        previous ->
                                "{ node: { type: column, id: c, children: [{ use: "
                                        + previous
                                        + " }] } }",
                        SpecLoader.MAX_DEPTH + 10,
                        "");
        assertTrue(failure(spec).contains("nest more than " + SpecLoader.MAX_DEPTH + " deep"));
    }

    @Test
    void textThatDoublesIsStopped() {
        String spec =
                chain(
                        "s",
                        "{ params: [x], node: { type: label, id: a, text: \"${x}\" } }",
                        previous ->
                                "{ params: [x], node: { use: "
                                        + previous
                                        + ", with: { x: \"${x}${x}\" } } }",
                        25,
                        ", with: { x: abcd }");
        assertTrue(failure(spec).contains("text longer than"));
    }

    @Test
    void listParametersThatDoubleAreStopped() {
        String spec =
                chain(
                        "l",
                        "{ params: [x], node: { type: label, id: a, items: \"${x}\" } }",
                        previous ->
                                "{ params: [x], node: { use: "
                                        + previous
                                        + ", with: { x: [\"${x}\", \"${x}\"] } } }",
                        30,
                        ", with: { x: [1] }");
        assertTrue(
                failure(spec)
                        .contains("more than " + SpecLoader.MAX_SUBSTITUTED_VALUES + " values"));
    }

    @Test
    void importsCannotLeaveTheSpecsFolder(@TempDir Path folder) throws IOException {
        Files.writeString(
                folder.resolve("common.yml"),
                "templates:\n  shared: { node: { type: label, id: shared, text: Shared } }\n");
        Path menus = Files.createDirectory(folder.resolve("menus"));
        for (String escape :
                new String[] {"../common.yml", folder.resolve("common.yml").toString()}) {
            Path spec = menus.resolve("menu.yml");
            Files.writeString(
                    spec,
                    SURFACE
                            + "templates:\n  import: ['"
                            + escape
                            + "']\ncontent: { use: shared }\n");
            SpecException thrown = assertThrows(SpecException.class, () -> SpecLoader.load(spec));
            assertTrue(
                    thrown.getMessage().contains("outside the spec's folder"), thrown::getMessage);
        }
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
