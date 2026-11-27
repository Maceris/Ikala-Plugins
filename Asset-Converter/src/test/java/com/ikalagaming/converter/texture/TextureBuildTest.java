package com.ikalagaming.converter.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.texgraph.GraphParameter;
import com.ikalagaming.converter.texgraph.OutputNodes;
import com.ikalagaming.converter.texgraph.SharedParameters;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureLibraries;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.TextureSection;
import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;

/** PNG maps to .ika texture files, and building a folder of sources into shipped textures. */
class TextureBuildTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    /**
     * Write a PNG of one color.
     *
     * @param file Where.
     * @param width Its width.
     * @param argb Its color.
     */
    private static void png(Path file, int width, int argb) throws IOException {
        final BufferedImage image = new BufferedImage(width, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < width; ++x) {
                image.setRGB(x, y, argb);
            }
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private int build(String... args) {
        out.reset();
        return BuildTextures.run(
                List.of(args),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void aTexturesMapsBecomeOneFileLeftAloneWhenUnchanged(@TempDir Path temp) throws Exception {
        final Path base = temp.resolve("art/block/rock.png");
        png(base, 32, 0xFF806040);
        png(temp.resolve("art/block/rock.normal.png"), 32, 0xFF8080FF);
        png(temp.resolve("art/block/rock.height.png"), 32, 0xFF808080);
        final Path target = temp.resolve("data/block/rock.ika");

        assertTrue(TextureConvert.convert(base, target, "1").written());
        final TextureSection.Stored stored = TextureSection.read(AssetFile.open(target));
        assertEquals(32, stored.width());
        assertEquals(6, stored.levels());
        assertEquals(2, stored.maps().size(), "Color and normal; height isn't shipped");
        assertFalse(TextureConvert.convert(base, target, "2").written(), "Same maps, left alone");

        png(base, 32, 0xFF806041);
        assertTrue(TextureConvert.convert(base, target, "2").written());
        png(temp.resolve("art/block/rock.normal.png"), 16, 0xFF8080FF);
        assertThrows(
                IllegalArgumentException.class,
                () -> TextureConvert.convert(base, target, "2"),
                "Maps of different sizes");
        assertEquals(List.of(base), TextureConvert.bases(temp.resolve("art")), "Only base colors");
    }

    @Test
    void buildingBakesGraphsAndConvertsEveryTexture(@TempDir Path temp) throws Exception {
        final Path sources = temp.resolve("art/textures");
        final Path shipped = temp.resolve("data/textures");
        // A painted texture, and a graph baking beside itself
        png(sources.resolve("block/painted.png"), 16, 0xFF203040);
        final TextureGraph texture = new TextureGraph(TextureLibraries.standard());
        texture.setMaps("..");
        texture.setTexture("block/made");
        texture.setShared(List.of("wear.texparams.yml"));
        final Graph graph = texture.getGraph();
        final String noise = graph.add("noise/white", 0, 0).getId();
        final String output = graph.add(OutputNodes.ALL, 200, 0).getId();
        graph.link(noise, "value", output, "color");
        graph.link(noise, "value", output, "height");
        Files.writeString(sources.resolve("block/made" + TextureGraph.SUFFIX), texture.write());
        SharedParameters.save(
                sources.resolve("block/wear.texparams.yml"),
                List.of(GraphParameter.of("wear", GraphParameter.Kind.SCALAR)));

        assertEquals(0, build(sources.toString(), shipped.toString()), err.toString());
        assertTrue(Files.isRegularFile(sources.resolve("block/made.png")), "Baked beside it");
        assertTrue(Files.isRegularFile(shipped.resolve("block/made.ika")));
        assertTrue(Files.isRegularFile(shipped.resolve("block/painted.ika")));
        final String ignored = Files.readString(sources.resolve("block/.gitignore"));
        assertTrue(ignored.contains("made.png") && ignored.contains("made.normal.png"), ignored);
        assertFalse(ignored.contains("painted.png"), "Painted maps stay tracked");
        assertEquals(0, build(sources.toString(), shipped.toString()));
        assertTrue(
                out.toString(StandardCharsets.UTF_8).contains("0 textures written, 2 unchanged"),
                out.toString(StandardCharsets.UTF_8));

        final String other = sources.resolve("block/other.texparams.yml").toString();
        assertEquals(0, build(sources.toString(), shipped.toString(), "--uses", other));
        assertTrue(
                out.toString(StandardCharsets.UTF_8).startsWith("Baked 0 graphs"),
                "No graph reads it");
        final String wear = sources.resolve("block/wear.texparams.yml").toString();
        assertEquals(0, build(sources.toString(), shipped.toString(), "--uses", wear));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("Baked 1 graphs"));
        assertEquals(2, build());
    }

    @Test
    void aLookFindsItsTextureFileAndTheNormalMapInIt(@TempDir Path root) throws Exception {
        final Path data = root.resolve("plugins/Texmod/data");
        final Path look = data.resolve("looks/block/rock.kvt");
        Files.createDirectories(look.getParent());
        Files.writeString(look, "{model:\"texmod:block/cube\", texture:\"texmod:block/rock\"}");
        final Path art = root.resolve("art");
        png(art.resolve("block/rock.png"), 16, 0xFF806040);
        png(art.resolve("block/rock.normal.png"), 16, 0xFF8080FF);
        // A stale PNG beside the file loses to it
        png(data.resolve("textures/block/rock.png"), 16, 0xFF000000);
        final Path file = data.resolve("textures/block/rock.ika");
        TextureConvert.convert(art.resolve("block/rock.png"), file, "1");

        final BlockContent.Look read = BlockContent.readLook(look).orElseThrow();
        assertEquals(Optional.of(file), BlockContent.textureFile(read));
        assertEquals(
                Optional.of(Path.of(file + BlockContent.NORMAL_MAP)),
                BlockContent.normalFile(read));
    }
}
