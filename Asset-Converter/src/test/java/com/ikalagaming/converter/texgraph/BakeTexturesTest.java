package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The bakeTextures command line. */
class BakeTexturesTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return BakeTextures.run(
                List.of(args),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void withoutGraphsItPrintsUsage() {
        assertEquals(2, run());
        assertEquals(2, run("--nonsense", "x"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Usage"));
    }

    @Test
    void aFolderOfGraphsBakesAndAgainChangesNothing(@TempDir Path temp) throws Exception {
        TextureGraph texture = new TextureGraph(TextureLibraries.standard());
        texture.setData("data");
        texture.setTexture("block/noise");
        Graph graph = texture.getGraph();
        String noise = graph.add("noise/value", 0, 0).getId();
        String output = graph.add(OutputNodes.ALL, 200, 0).getId();
        graph.link(noise, "value", output, "color");
        graph.link(noise, "value", output, "height");
        Path graphs = Files.createDirectories(temp.resolve("art"));
        Files.writeString(graphs.resolve("noise" + TextureGraph.SUFFIX), texture.write());

        assertEquals(0, run(graphs.toString()), err.toString(StandardCharsets.UTF_8));
        Path block = graphs.resolve("data/textures/block");
        assertTrue(Files.exists(block.resolve("noise.png")));
        assertTrue(Files.exists(block.resolve("noise.normal.png")));
        assertTrue(Files.exists(block.resolve("noise.height.png")));
        out.reset();
        assertEquals(0, run(graphs.toString()));
        assertTrue(
                out.toString(StandardCharsets.UTF_8).contains("0 maps written, 3 unchanged"),
                out.toString(StandardCharsets.UTF_8));

        Files.writeString(graphs.resolve("broken" + TextureGraph.SUFFIX), "texture graph: 1\n");
        assertEquals(1, run(graphs.toString()), "A graph that can't bake fails the run");
    }
}
