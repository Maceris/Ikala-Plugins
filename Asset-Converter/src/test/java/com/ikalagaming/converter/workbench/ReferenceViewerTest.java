package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetType;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.index.GameTypes;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The reference viewer's graph: users left, uses right, missing names, overrides. */
class ReferenceViewerTest {
    private static AssetIndex.Snapshot index(Path base, Path mod) {
        final List<AssetType> types = new ArrayList<>(GameTypes.all());
        types.addAll(EditorTypes.all());
        return new AssetIndex(types)
                .scan(List.of(AssetIndex.Root.of(base), AssetIndex.Root.of(mod)), null);
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static ReferenceViewer.Built build(AssetIndex.Snapshot snapshot, Path center) {
        final PinTypes pins = new PinTypes();
        pins.register(new PinTypes.PinType(ReferenceViewer.PIN, "Reference", 0xFFFFFFFF));
        return ReferenceViewer.build(
                snapshot,
                ReferenceViewer.Around.of(snapshot, center.toAbsolutePath().normalize()),
                new NodeTypes(),
                pins,
                "t/");
    }

    private static Set<String> titles(ReferenceViewer.Built built, boolean left) {
        final Graph graph = built.graph();
        final Graph.Node center = graph.node(built.center());
        final Set<String> titles = new TreeSet<>();
        for (Graph.Node node : graph.nodes()) {
            if (node != center && (node.getX() < center.getX()) == left) {
                titles.add(graph.getNodeTypes().get(node.getType()).title());
            }
        }
        return titles;
    }

    @Test
    void usersGoLeftUsesRightAndMissingNamesAreErrors(@TempDir Path temp) throws Exception {
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
        final AssetIndex.Snapshot snapshot = index(base, mod);

        // A model both looks use: two users, two numbered pins, no uses
        final ReferenceViewer.Built cube = build(snapshot, base.resolve("models/block/cube.ika"));
        assertEquals(Set.of("odd.kvt", "rock.kvt"), titles(cube, true));
        assertEquals(Set.of(), titles(cube, false));
        assertEquals(2, cube.graph().links().size());
        final NodeTypes.NodeType middle =
                cube.graph().getNodeTypes().get(cube.graph().node(cube.center()).getType());
        assertEquals(
                List.of("model 1", "model 2"),
                middle.inputs().stream().map(NodeTypes.Pin::name).toList());

        // A look with a missing texture: a red node titled with the name
        final ReferenceViewer.Built odd = build(snapshot, base.resolve("looks/block/odd.kvt"));
        assertEquals(Set.of("cube.ika", "base:block/nothing"), titles(odd, false));
        assertEquals(1, odd.errors().size());
        assertTrue(
                odd.errors().values().iterator().next().contains("base:block/nothing"),
                odd.errors().toString());
        assertEquals(2, odd.graph().links().size());

        // An overridden texture: the winner names the folder it beats, the loser its winner
        final Path override = mod.resolve("overrides/base/textures/block/rock.png");
        final ReferenceViewer.Built winner = build(snapshot, override);
        assertEquals("Overrides base", winner.graph().node(winner.center()).getComment());
        assertEquals(Set.of("rock.kvt"), titles(winner, true));
        final ReferenceViewer.Built loser =
                build(snapshot, base.resolve("textures/block/rock.png"));
        assertEquals("Overridden by mod", loser.graph().node(loser.center()).getComment());
        assertEquals(Set.of(), titles(loser, true), "The look uses the winner");
        assertNull(build(snapshot, base.resolve("looks/block/rock.kvt")).errors().get("t/use0"));
    }
}
