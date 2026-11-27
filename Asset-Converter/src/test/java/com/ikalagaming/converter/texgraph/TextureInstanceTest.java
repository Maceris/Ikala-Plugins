package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Texture instances: a graph with some of its values changed. */
class TextureInstanceTest {
    @TempDir Path folder;

    /**
     * Write a graph: a tint parameter times noise, scaled by an amount, baking to block/base.
     *
     * @return The file.
     */
    private Path graph() throws Exception {
        final TextureGraph texture = new TextureGraph(TextureLibraries.standard());
        texture.setData(".");
        texture.setTexture("block/base");
        texture.putParameter(
                GraphParameter.of("tint", GraphParameter.Kind.COLOR).withValue("#808080"));
        texture.putParameter(
                GraphParameter.of("amount", GraphParameter.Kind.SCALAR).withValue("1"));
        texture.setShared(List.of("wear.texparams.yml"));
        SharedParameters.save(
                folder.resolve("wear.texparams.yml"),
                List.of(GraphParameter.of("wear", GraphParameter.Kind.SCALAR).withValue("0.5")));
        final Graph graph = texture.getGraph();
        graph.put("tint", ParameterNodes.typeOf(GraphParameter.Kind.COLOR), 0, 0);
        graph.setParam("tint", ParameterNodes.NAME, "tint");
        graph.put("noise", "noise/value", 0, 100);
        graph.put("blend", "ops/blend", 200, 0);
        graph.put("out", OutputNodes.ALL, 400, 0);
        graph.link("tint", "color", "blend", "top");
        graph.link("noise", "value", "blend", "mask");
        graph.link("blend", "color", "out", "color");
        final Path file = folder.resolve("base" + TextureGraph.SUFFIX);
        Files.writeString(file, texture.write());
        return file;
    }

    private Path instance(String name, String parent, String texture, Map<String, String> values)
            throws Exception {
        final TextureInstance instance = new TextureInstance();
        instance.setParent(parent);
        instance.setTexture(texture);
        values.forEach(instance::setValue);
        final Path file = folder.resolve(name + TextureInstance.SUFFIX);
        Files.writeString(file, instance.write());
        return file;
    }

    @Test
    void aFileListsOnlyWhatItOverrides() {
        final TextureInstance instance = new TextureInstance();
        instance.setParent("planks.texgraph.yml");
        instance.setTexture("block/birch_planks");
        instance.setValue("tint", "#c8b48a");
        final String text = instance.write();
        assertEquals(
                "texture instance: 1\nparent: 'planks.texgraph.yml'\n"
                        + "texture: 'block/birch_planks'\nparameters:\n  tint: '#c8b48a'\n",
                text);
        final TextureInstance read = new TextureInstance();
        assertEquals(List.of(), read.read(text));
        assertEquals(text, read.write());
        assertNull(read.getSeed(), "No seed unless overridden");
    }

    @Test
    void overridesApplyDownTheChainTheNearestWinning() throws Exception {
        graph();
        instance("red", "base.texgraph.yml", "block/red", Map.of("tint", "#ff0000", "wear", "0.9"));
        final Path dim = instance("dim", "red.texinst.yml", "block/dim", Map.of("amount", "0.25"));
        final TextureInstance.Chain chain = TextureInstance.chain(dim);
        assertEquals(List.of(), chain.problems());
        assertEquals(2, chain.instances().size());
        final List<String> problems = new ArrayList<>();
        final TextureGraph read = TextureInstance.readGraph(chain, problems);
        final TextureGraph applied =
                TextureInstance.resolve(chain, read, chain.instances().size(), problems);
        assertEquals(List.of(), problems);
        assertEquals("#ff0000", applied.parameter("tint").value(), "From the parent instance");
        assertEquals("0.25", applied.parameter("amount").value());
        assertEquals("block/dim", applied.getTexture(), "The nearest wins");
        assertEquals(
                "0.9",
                applied.resolveParameters(folder, new ArrayList<>()).get("wear").value(),
                "Shared parameters too");
        assertEquals("#808080", read.parameter("tint").value(), "The graph read isn't changed");
    }

    @Test
    void loopsAndUnknownParametersAreProblems() throws Exception {
        graph();
        final Path a = instance("a", "b.texinst.yml", "block/a", Map.of());
        instance("b", "a.texinst.yml", "block/b", Map.of());
        assertTrue(TextureInstance.chain(a).problems().getFirst().contains("loop"));

        final Path odd = instance("odd", "base.texgraph.yml", "block/odd", Map.of("nope", "1"));
        final TextureInstance.Chain chain = TextureInstance.chain(odd);
        final List<String> problems = new ArrayList<>();
        TextureInstance.resolve(chain, TextureInstance.readGraph(chain, problems), 1, problems);
        assertEquals(List.of("The graph has no parameter nope"), problems);
        assertFalse(TextureInstance.bake(odd).ok(), "Not baked with a value it can't place");
        assertTrue(
                Files.readString(odd).contains("nope"),
                "The value stays in the file, never dropped");
    }

    @Test
    void anInstanceBakesItsGraphWithItsValues() throws Exception {
        final Path base = graph();
        final Path red =
                instance("red", "base.texgraph.yml", "block/red", Map.of("tint", "#ff0000"));
        final Baker.Result baked = TextureInstance.bake(red);
        assertTrue(baked.ok(), baked.problems().toString());

        // The graph baked by hand with the same value
        final TextureGraph byHand = new TextureGraph(TextureLibraries.standard());
        byHand.read(Files.readString(base), folder);
        byHand.putParameter(byHand.parameter("tint").withValue("#ff0000"));
        byHand.setTexture("block/by_hand");
        assertTrue(Baker.bake(byHand, new Evaluator(byHand.getLibrary()), folder, null).ok());
        assertArrayEquals(
                pixels(folder.resolve("textures/block/by_hand.png")),
                pixels(folder.resolve("textures/block/red.png")));
        assertFalse(
                java.util.Arrays.equals(
                        pixels(folder.resolve("textures/block/red.png")), pixels(writeBase(base))),
                "Not the parent's colors");

        final Path unnamed = instance("unnamed", "base.texgraph.yml", null, Map.of());
        assertFalse(TextureInstance.bake(unnamed).ok(), "It must name its own texture");
    }

    private Path writeBase(Path base) throws Exception {
        final TextureGraph graph = new TextureGraph(TextureLibraries.standard());
        graph.read(Files.readString(base), folder);
        assertTrue(Baker.bake(graph, new Evaluator(graph.getLibrary()), folder, null).ok());
        return folder.resolve("textures/block/base.png");
    }

    private static int[] pixels(Path file) throws Exception {
        final BufferedImage image = ImageIO.read(file.toFile());
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void savingAnEditedGraphWritesOnlyWhatDiffers() throws Exception {
        graph();
        final Path red =
                instance("red", "base.texgraph.yml", "block/red", Map.of("tint", "#ff0000"));
        final TextureInstance.Chain chain = TextureInstance.chain(red);
        final List<String> problems = new ArrayList<>();
        final TextureGraph read = TextureInstance.readGraph(chain, problems);
        final TextureGraph base = TextureInstance.resolve(chain, read, 0, problems);
        final TextureGraph edited = TextureInstance.resolve(chain, read, 1, problems);
        edited.setSeed(9);
        edited.putParameter(edited.parameter("amount").withValue("0.5"));
        final TextureInstance saved =
                TextureInstance.between("base.texgraph.yml", folder, folder, base, edited);
        assertEquals(Map.of("tint", "#ff0000", "amount", "0.5"), saved.getValues());
        assertEquals(9L, saved.getSeed());
        assertEquals("block/red", saved.getTexture());
        assertNull(saved.getData(), "The content folder is the parent's");
    }
}
