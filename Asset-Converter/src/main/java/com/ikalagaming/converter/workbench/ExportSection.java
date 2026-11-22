package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.MeshExport;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * The scene view's export section: writes the whole scene or the selected node as one mesh in an
 * asset container ({@link MeshExport}), for a game to load as a model, such as a block's shape.
 *
 * <p>Render thread only.
 */
@Slf4j
final class ExportSection {

    /** The choices of what to export, in combo order. */
    private static final String[] SCOPES = {"Whole scene", "Selected node"};

    /** Export the whole scene. */
    private static final int WHOLE_SCENE = 0;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** What to export, an index into {@link #SCOPES}. */
    private final IkInt scope = new IkInt(WHOLE_SCENE);

    /**
     * Whether the file's origin is the middle of the model's bottom, as Blockbench and Minecraft
     * place block models, so it is moved to the corner of the block's cell.
     */
    private final IkBoolean bottomCenter = new IkBoolean(true);

    /** What the last export did, or empty. */
    private String status = "";

    /**
     * Set up the section.
     *
     * @param graphics The plugin's graphics context.
     */
    ExportSection(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Draw the section.
     *
     * @param shown The scene shown.
     * @param selected The selected node, or null.
     */
    void draw(@NonNull ScenePlacement shown, @Nullable Node selected) {
        final ImportedScene imported = shown.getImported();
        IkGui.combo("Export from###exportScope", scope, SCOPES);
        final Node top = scope.get() == WHOLE_SCENE ? imported.root() : selected;
        IkGui.checkbox("The file's origin is the middle of the bottom", bottomCenter);
        IkGui.textWrapped(
                "Writes every mesh as one mesh, without materials. Block models fill the cell from"
                        + " 0 to 1 m; Blockbench and Minecraft models are centered on their"
                        + " bottom instead, which the box above moves to the cell's corner.");
        if (top == null) {
            IkGui.textDisabled("Select a node to export");
        } else if (IkGui.button("Export mesh...")) {
            final Path source = imported.file();
            final String name = source.getFileName().toString();
            final int dot = name.lastIndexOf('.');
            graphics.ui()
                    .saveFile(
                            "Export mesh",
                            source.toAbsolutePath().getParent(),
                            (dot > 0 ? name.substring(0, dot) : name) + AssetFormat.EXTENSION,
                            target -> {
                                if (target != null) {
                                    export(imported, top, target);
                                }
                            },
                            AssetFormat.EXTENSION.substring(1));
        }
        if (!status.isEmpty()) {
            IkGui.textWrapped(status);
        }
    }

    /**
     * Export, and say how it went.
     *
     * @param imported The scene.
     * @param top The node to export.
     * @param target The file to write.
     */
    private void export(@NonNull ImportedScene imported, @NonNull Node top, @NonNull Path target) {
        try {
            status =
                    MeshExport.describe(
                            MeshExport.export(imported, top, bottomCenter.get(), target), target);
        } catch (RuntimeException e) {
            log.warn("Couldn't export {} to {}", top.name(), target, e);
            status = "Couldn't export: " + e.getMessage();
        }
    }
}
