package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.SceneLod;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.lod.DagBuilder;
import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.lod.LodPanel;
import com.ikalagaming.graphics.lod.MaterialAverages;
import com.ikalagaming.graphics.vulkan.ToneMapMath;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;

/**
 * The scene view's level of detail section: builds a DAG from the whole scene or the selected node
 * in the background, then shows its cut through a {@link LodPanel}, with the originals it stands
 * for hidden unless asked for. Materials are taken as they are when building starts; edits to a
 * material's values show in the level of detail, but a mesh given another material needs a rebuild.
 *
 * <p>Render thread only.
 */
final class LodSection {

    /** The choices of what to build from, in combo order. */
    private static final String[] SCOPES = {"Whole scene", "Selected node"};

    /** Color channels in an average. */
    private static final int COLORS = 3;

    /** Build from the whole scene. */
    private static final int WHOLE_SCENE = 0;

    /**
     * A mesh that went in.
     *
     * @param node The node.
     * @param sceneMesh The scene's index of the mesh.
     */
    private record Part(Node node, int sceneMesh) {}

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** What to build from, an index into {@link #SCOPES}. */
    private final IkInt scope = new IkInt(WHOLE_SCENE);

    /**
     * Whether to join separate pieces with virtual edges. Off by default: on Bistro's interior they
     * took the build from 29 s to 93 s and its memory from 3.7 to 15.8 GB, and left it stuck six
     * levels sooner.
     */
    private final IkBoolean virtualEdges = new IkBoolean(false);

    /**
     * Whether groups whose error has grown past how far apart their textures repeat blend their
     * materials' average looks instead of keeping each material and texture, so seams stop holding
     * simplifying back far out. Bistro's textures stretch over whole meshes, so its groups stall
     * before their error gets there.
     */
    private final IkBoolean blendFar = new IkBoolean(true);

    /**
     * Whether triangles keep their normals and texture coordinates. Their seams lock so much that
     * Bistro's exterior stops halving at level 3; without them it gets to level 7.
     */
    private final IkBoolean keepAttributes = new IkBoolean(true);

    /** Whether cut out meshes, like leaves, go in too, drawn with an alpha test. */
    private final IkBoolean cutOut = new IkBoolean(true);

    /**
     * Whether far groups bake their appearance into texture tiles and simplify their geometry
     * alone, rather than keep their materials and texture seams (see {@code LodBakes}).
     */
    private final IkBoolean bake = new IkBoolean(true);

    /** The side of the largest regular bake tile, in texels. */
    private static final int BAKE_TILE = 64;

    /** The most texels across a baked cluster's error to choose. */
    private static final int MAX_BAKE_DENSITY = 4;

    /** Texels across a baked cluster's error. */
    private final int[] bakeDensity = {(int) DagBuilder.Options.DEFAULT_BAKE_DENSITY};

    /** Whether to keep the lineage, to measure how faithful each level is. */
    private final IkBoolean measureFidelity = new IkBoolean(true);

    /** Whether to draw the originals the level of detail stands for. */
    private final IkBoolean showOriginal = new IkBoolean(false);

    /** The panel, while there is a level of detail. */
    private @Nullable LodPanel panel;

    /** What the level of detail was built from, for the panel. */
    private String builtFrom = "";

    /** The scene it was built in. */
    private @Nullable ScenePlacement builtIn;

    /** The opaque meshes that went in, which it stands for. */
    private final List<Part> parts = new ArrayList<>();

    /** Whether the parts are hidden now. */
    private boolean partsHidden;

    /**
     * Set up the section; nothing is built until asked.
     *
     * @param graphics The plugin's graphics context.
     */
    LodSection(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Draw the section.
     *
     * @param shown The scene.
     * @param selected The selected node, or null.
     */
    void draw(@NonNull ScenePlacement shown, @Nullable Node selected) {
        if (panel != null && builtIn != shown) {
            close();
        }
        final LodPanel current = panel;
        if (current == null) {
            drawBuild(shown, selected);
            return;
        }
        IkGui.text("Level of detail of " + builtFrom);
        IkGui.checkbox("Show the original", showOriginal);
        IkGui.sameLine();
        if (IkGui.button("Close level of detail")) {
            close();
            return;
        }
        hideParts(shown, current.isBuilt() && !showOriginal.get());
        current.draw();
    }

    /**
     * Draw the choice of what to build from, and the button that starts it.
     *
     * @param shown The scene.
     * @param selected The selected node, or null.
     */
    private void drawBuild(@NonNull ScenePlacement shown, @Nullable Node selected) {
        IkGui.combo("Build from", scope, SCOPES);
        final Node top = scope.get() == WHOLE_SCENE ? shown.getImported().root() : selected;
        if (top == null) {
            IkGui.textDisabled("Select a node to build from");
            return;
        }
        if (!shown.isTexturesReady()) {
            // Which triangles keep texture coordinates, and what cuts out, depends on them
            IkGui.textDisabled("Building can start once the textures have loaded");
            return;
        }
        IkGui.checkbox("Join separate pieces (slow, needs a lot of memory)", virtualEdges);
        IkGui.checkbox("Keep normals and texture coordinates", keepAttributes);
        IkGui.checkbox("Include cut out meshes", cutOut);
        IkGui.checkbox("Bake far textures", bake);
        if (bake.get()) {
            IkGui.sliderInt("Bake texels per error", bakeDensity, 1, MAX_BAKE_DENSITY, "%d");
        }
        IkGui.checkbox("Blend materials far out", blendFar);
        IkGui.checkbox("Measure fidelity", measureFidelity);
        IkGui.textWrapped(
                "Builds from the opaque meshes, and the cut out ones if included, drawn alpha"
                        + " tested; see-through ones stay as they are. Bistro's exterior takes a"
                        + " few minutes and about 11 GB.");
        if (IkGui.button("Build level of detail")) {
            build(shown, top);
        }
    }

    /**
     * Start building from a node's meshes, taking their materials as they are now.
     *
     * @param shown The scene.
     * @param top The node.
     */
    private void build(@NonNull ScenePlacement shown, @NonNull Node top) {
        final ImportedScene imported = shown.getImported();
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        // The build reads materials on its own thread, so it gets a copy of who has which
        final Map<Node, Map<Integer, Material>> drawnWith = new IdentityHashMap<>();
        final Map<Material, Integer> indices = new IdentityHashMap<>();
        final Map<Integer, String> names = new HashMap<>();
        final List<Material> sceneMaterials = shown.getMaterials();
        for (int i = 0; i < sceneMaterials.size(); ++i) {
            names.put(cache.getMaterialIndex(sceneMaterials.get(i)), shown.nameOf(i));
        }
        parts.clear();
        final boolean withCutOut = cutOut.get();
        final Deque<Node> open = new ArrayDeque<>();
        open.push(top);
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            for (int sceneMesh : node.meshes()) {
                final Material material = shown.materialOf(node, sceneMesh);
                drawnWith.computeIfAbsent(node, n -> new HashMap<>()).put(sceneMesh, material);
                indices.computeIfAbsent(material, cache::getMaterialIndex);
                if (SceneLod.goesIn(material, withCutOut)) {
                    parts.add(new Part(node, sceneMesh));
                }
            }
        }
        builtFrom = top == imported.root() ? "the whole scene" : top.name();
        builtIn = shown;
        final boolean attributes = keepAttributes.get();
        showOriginal.set(false);
        panel =
                new LodPanel(
                        graphics,
                        top.name(),
                        () -> {
                            final SceneLod.Input input =
                                    SceneLod.gather(
                                            imported,
                                            top,
                                            (node, mesh) ->
                                                    drawnWith
                                                            .getOrDefault(node, Map.of())
                                                            .get(mesh),
                                            indices::get,
                                            attributes,
                                            withCutOut);
                            if (input == null) {
                                throw new IllegalArgumentException("there are no opaque meshes");
                            }
                            return List.<LodMesh>of(input.mesh());
                        },
                        material -> names.getOrDefault(material, "material " + material),
                        options(averages(indices))
                                .withCutOut(withCutOut ? cutOutMaterials(indices) : null));
    }

    /**
     * How to build, from the choices.
     *
     * @param averages The materials' averages, or null.
     * @return The options.
     */
    private DagBuilder.Options options(@Nullable MaterialAverages averages) {
        final DagBuilder.Options defaults = DagBuilder.Options.defaults();
        return new DagBuilder.Options(
                virtualEdges.get() ? defaults.virtualEdgeFactor() : 0,
                defaults.lockOpenBorder(),
                defaults.threads(),
                measureFidelity.get(),
                defaults.visibilityReach(),
                defaults.mergePlanar(),
                blendFar.get() ? averages : null,
                bake.get() ? BAKE_TILE : 0,
                bakeDensity[0]);
    }

    /**
     * What each material that goes in looks like averaged, as the scene draws it: its texture's
     * average where it has one, in place of its base color, else its base color, in linear light,
     * with its roughness, metallic and specular. Taken now, on the render thread, for the build.
     *
     * @param indices Each material's index in the material cache.
     * @return The averages.
     */
    private MaterialAverages averages(@NonNull Map<Material, Integer> indices) {
        final Map<Integer, float[]> known = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : indices.entrySet()) {
            final Material material = entry.getKey();
            final float[] out = new float[MaterialAverages.VALUES];
            final float[] texture = graphics.textures().average(material.getTexture());
            if (texture != null) {
                System.arraycopy(texture, 0, out, MaterialAverages.RED, COLORS);
            } else {
                out[MaterialAverages.RED] = ToneMapMath.decode(material.getBaseColor().x);
                out[MaterialAverages.RED + 1] = ToneMapMath.decode(material.getBaseColor().y);
                out[MaterialAverages.RED + 2] = ToneMapMath.decode(material.getBaseColor().z);
            }
            out[MaterialAverages.ROUGHNESS] = material.getRoughness();
            out[MaterialAverages.METALLIC] = material.getMetallic();
            out[MaterialAverages.SPECULAR] = material.getSpecular();
            known.put(entry.getValue(), out);
        }
        return known::get;
    }

    /**
     * Which of the materials that go in are cut out, by index, taken now for the build.
     *
     * @param indices Each material's index in the material cache.
     * @return Whether a material index is cut out.
     */
    private static IntPredicate cutOutMaterials(@NonNull Map<Material, Integer> indices) {
        final Set<Integer> cut = new HashSet<>();
        for (Map.Entry<Material, Integer> entry : indices.entrySet()) {
            if (entry.getKey().getTransparency() == Material.Transparency.CUTOUT) {
                cut.add(entry.getValue());
            }
        }
        return cut::contains;
    }

    /**
     * Hide or show the meshes the level of detail stands for, if they aren't already.
     *
     * @param shown The scene.
     * @param hide Whether to hide them.
     */
    private void hideParts(@NonNull ScenePlacement shown, boolean hide) {
        if (hide == partsHidden) {
            return;
        }
        for (Part part : parts) {
            shown.setHidden(part.node(), part.sceneMesh(), hide);
        }
        partsHidden = hide;
    }

    /** Take the level of detail away and show the originals again. */
    void close() {
        if (panel != null) {
            panel.close();
            panel = null;
        }
        if (builtIn != null) {
            hideParts(builtIn, false);
            builtIn = null;
        }
        parts.clear();
        partsHidden = false;
    }
}
