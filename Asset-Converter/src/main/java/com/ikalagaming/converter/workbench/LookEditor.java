package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.AssetConvert;
import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.scene.BlockScenes;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MaterialText;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Edits a block's look where it is seen: a strip of slots for its model, texture and render
 * material above the 3D view's context scene, and the chosen piece's details in the properties. The
 * scene is rebuilt from the look as edited, and the render material's values change on the live
 * material, so changes show before they are saved. Saving writes the look's changed keys, keeping
 * the rest of its text, and the render material to its file and the art source that makes it. Uses
 * Factory-Core's classes through {@link BlockContent}, so only made while it is loaded. Render
 * thread.
 */
@Slf4j
public final class LookEditor {
    /** The model slot's label. */
    public static final String MODEL_SLOT = "Look model";

    /** The texture slot's label. */
    public static final String TEXTURE_SLOT = "Look texture";

    /** The render material slot's label. */
    public static final String MATERIAL_SLOT = "Look material";

    /** How wide each slot in the strip is, in pixels. */
    private static final float SLOT_WIDTH = 250;

    /** The size of the chosen texture's picture in the details. */
    private static final float PICTURE = 128;

    /** A render material with no textures, as looks' are. */
    private static final MaterialSection.Textures NO_TEXTURES =
            new MaterialSection.Textures(null, null, null, null);

    /** A piece of a look. */
    public enum Piece {
        /** The model. */
        MODEL,
        /** The texture. */
        TEXTURE,
        /** The render material. */
        MATERIAL
    }

    /** Draws the slots. */
    private final AssetSlot slots;

    /** The asset index, as of now. */
    private final Supplier<AssetIndex.Snapshot> index;

    /** A file's thumbnail, or null until there is one. */
    private final Function<AssetIndex.Entry, TextureHandle> thumbnails;

    /** Opens a file in the workbench. */
    private final Consumer<Path> open;

    /** Builds the scene again from the look as edited, keeping the view. */
    private final Runnable rebuild;

    /** Draws a material's values. */
    private final MaterialEditor materials;

    /** Says what happened, in the status line. */
    private final Consumer<String> report;

    /** The look file edited, or null before one is opened. */
    @Getter private @Nullable Path file;

    /** The look as saved. */
    private @Nullable BlockContent.Look saved;

    /** The look as edited. */
    @Getter private @Nullable BlockContent.Look edited;

    /** The piece whose details show. */
    @Getter private Piece chosen = Piece.MATERIAL;

    /** Whether the 3D view shows the look now. */
    @Getter private boolean showing;

    /** The look's render material in the scene shown, edited in place, or null. */
    @Getter private @Nullable Material live;

    /** The render material file the live material was read from, or null. */
    private @Nullable Path liveFile;

    /** The live material's values as saved, as text, to tell when they changed. */
    private String savedValues = "";

    /** Values changed but not saved, kept across a rebuild of the same material, or null. */
    private @Nullable Material pending;

    /** The material file {@link #pending} belongs to. */
    private @Nullable Path pendingFile;

    /**
     * Make the editor.
     *
     * @param slots Draws the slots.
     * @param index The asset index, as of now.
     * @param thumbnails A file's thumbnail, or null until it is made.
     * @param open Opens a file in the workbench.
     * @param rebuild Builds the scene again from the look as edited, keeping the view.
     * @param materials Draws a material's values.
     * @param report Says what happened.
     */
    public LookEditor(
            @NonNull AssetSlot slots,
            @NonNull Supplier<AssetIndex.Snapshot> index,
            @NonNull Function<AssetIndex.Entry, TextureHandle> thumbnails,
            @NonNull Consumer<Path> open,
            @NonNull Runnable rebuild,
            @NonNull MaterialEditor materials,
            @NonNull Consumer<String> report) {
        this.slots = slots;
        this.index = index;
        this.thumbnails = thumbnails;
        this.open = open;
        this.rebuild = rebuild;
        this.materials = materials;
        this.report = report;
    }

    /**
     * Start editing a look, dropping any unsaved changes to another.
     *
     * @param lookFile The look file.
     * @return Whether it could be read.
     */
    public boolean open(@NonNull Path lookFile) {
        final Path normal = lookFile.toAbsolutePath().normalize();
        if (normal.equals(file) && edited != null) {
            return true;
        }
        final BlockContent.Look look = BlockContent.readLook(normal).orElse(null);
        if (look == null) {
            return false;
        }
        file = normal;
        saved = look;
        edited = look;
        live = null;
        pending = null;
        showing = false;
        return true;
    }

    /**
     * The look to build a scene around: the one edited if it is this file, else the file as saved.
     *
     * @param lookFile The look file.
     * @return The look as edited, or null if this editor doesn't have that file.
     */
    public @Nullable BlockContent.Look editedFor(@Nullable Path lookFile) {
        return lookFile != null && lookFile.toAbsolutePath().normalize().equals(file)
                ? edited
                : null;
    }

    /**
     * Note that the 3D view shows the look, built from it as edited: take its render material to
     * edit in place, with any unsaved values put back.
     *
     * @param placement The scene shown.
     * @param built How it was built.
     */
    public void shown(@NonNull ScenePlacement placement, BlockScenes.@NonNull Built built) {
        showing = true;
        live =
                built.subject() >= 0 && built.subject() < placement.getMaterials().size()
                        ? placement.getMaterials().get(built.subject())
                        : null;
        liveFile = edited == null ? null : BlockContent.materialFile(edited).orElse(null);
        if (live == null) {
            return;
        }
        // As saved: the file's values, which the scene was built with
        savedValues = valuesOf(live);
        if (pending != null && Objects.equals(pendingFile, liveFile)) {
            live.setValues(pending);
        }
        pending = null;
    }

    /** Note that the 3D view shows something else. */
    public void hidden() {
        rememberPending();
        showing = false;
        live = null;
    }

    /**
     * Whether the look or its render material has changes not saved.
     *
     * @return True if either does.
     */
    public boolean isDirty() {
        return lookDirty() || materialDirty();
    }

    /**
     * Whether the look's model, texture or material changed.
     *
     * @return True if so.
     */
    boolean lookDirty() {
        return edited != null && !edited.equals(saved);
    }

    /**
     * Whether the live render material's values changed.
     *
     * @return True if so.
     */
    boolean materialDirty() {
        return live != null && !valuesOf(live).equals(savedValues);
    }

    /**
     * Choose the piece whose details show.
     *
     * @param piece The piece.
     */
    public void choose(@NonNull Piece piece) {
        chosen = piece;
    }

    /**
     * Draw the strip above the 3D view's picture: the look's name, Save and Revert, and its three
     * slots side by side, each chosen with the button above it.
     *
     * @param frame The current frame.
     */
    public void drawStrip(@NonNull UiFrame frame) {
        final BlockContent.Look look = edited;
        if (!showing || look == null || file == null) {
            return;
        }
        IkGui.textUnformatted("Look of " + look.id() + (isDirty() ? " (changed)" : ""));
        IkGui.sameLine();
        IkGui.beginDisabled(!isDirty());
        if (IkGui.button("Save look")) {
            frame.fire(this::save);
        }
        IkGui.sameLine();
        if (IkGui.button("Revert")) {
            frame.fire(this::revert);
        }
        IkGui.endDisabled();

        // Side by side, each in a column of its own
        final float left = IkGui.getCursorPosX();
        drawPiece(Piece.MODEL);
        IkGui.sameLine(left + SLOT_WIDTH, -1);
        drawPiece(Piece.TEXTURE);
        IkGui.sameLine(left + 2 * SLOT_WIDTH, -1);
        drawPiece(Piece.MATERIAL);
    }

    /**
     * Draw one piece's slot, with the button choosing it for the details above.
     *
     * @param piece The piece.
     */
    private void drawPiece(Piece piece) {
        final BlockContent.Look look = Objects.requireNonNull(edited);
        IkGui.beginGroup();
        final String label =
                switch (piece) {
                    case MODEL -> MODEL_SLOT;
                    case TEXTURE -> TEXTURE_SLOT;
                    case MATERIAL -> MATERIAL_SLOT;
                };
        if (IkGui.radioButton("Details##" + label, chosen == piece)) {
            chosen = piece;
        }
        final AssetSlot.Change change =
                switch (piece) {
                    case MODEL ->
                            slots.draw(
                                    label,
                                    Set.of(EditorTypes.MODEL),
                                    BlockContent.modelFile(look).orElse(null),
                                    null,
                                    true);
                    case TEXTURE ->
                            slots.draw(
                                    label,
                                    Set.of(EditorTypes.TEXTURE),
                                    BlockContent.textureFile(look).orElse(null),
                                    null,
                                    true);
                    case MATERIAL ->
                            slots.draw(
                                    label,
                                    Set.of(EditorTypes.RENDER_MATERIAL),
                                    BlockContent.materialFile(look).orElse(null),
                                    null,
                                    true);
                };
        IkGui.endGroup();
        if (change != null) {
            set(piece, change.file());
        }
    }

    /**
     * Put a file in a piece, or empty it, and build the scene again.
     *
     * @param piece The piece.
     * @param chosenFile The file, which has a content ID, or null to empty it.
     */
    void set(@NonNull Piece piece, @Nullable Path chosenFile) {
        final BlockContent.Look look = edited;
        if (look == null) {
            return;
        }
        String id = null;
        if (chosenFile != null) {
            final AssetIndex.Entry entry = index.get().entry(chosenFile);
            if (entry == null || entry.id() == null) {
                report.accept(chosenFile.getFileName() + " has no content ID for a look to use");
                return;
            }
            id = entry.id();
        }
        final BlockContent.Look changed =
                switch (piece) {
                        // A look always has a model; without one it is the default cube
                    case MODEL ->
                            new BlockContent.Look(
                                    look.id(),
                                    id == null ? BlockContent.DEFAULT_MODEL : id,
                                    look.texture(),
                                    look.material());
                    case TEXTURE ->
                            new BlockContent.Look(
                                    look.id(),
                                    look.model(),
                                    keepMap(look.texture(), id),
                                    look.material());
                    case MATERIAL ->
                            new BlockContent.Look(look.id(), look.model(), look.texture(), id);
                };
        if (changed.equals(look)) {
            return;
        }
        rememberPending();
        edited = changed;
        chosen = piece;
        rebuild.run();
    }

    /**
     * A texture's new ID, keeping the map named after the old one's {@code #} if the texture is the
     * same.
     *
     * @param old The ID now, maybe with a map.
     * @param id The new ID, or null.
     * @return The ID to write.
     */
    private static @Nullable String keepMap(@Nullable String old, @Nullable String id) {
        if (old == null || id == null) {
            return id;
        }
        final int hash = old.indexOf('#');
        return hash >= 0 && old.substring(0, hash).equals(id) ? old : id;
    }

    /** Keep the live material's unsaved values, so a rebuild of the same material keeps them. */
    private void rememberPending() {
        if (materialDirty()) {
            pending = MaterialEditor.copy(Objects.requireNonNull(live));
            pendingFile = liveFile;
        }
    }

    /**
     * Draw the chosen piece's details, for the properties: the model's ID and file, the texture's
     * picture and graph, or the render material's values, edited in place.
     *
     * @param frame The current frame.
     */
    public void drawDetails(@NonNull UiFrame frame) {
        final BlockContent.Look look = edited;
        if (!showing || look == null) {
            return;
        }
        if (!IkGui.collapsingHeader(
                "Look", com.ikalagaming.graphics.gui.flags.TreeNodeFlags.DEFAULT_OPEN)) {
            return;
        }
        switch (chosen) {
            case MODEL ->
                    drawFileDetails(
                            frame,
                            "Model",
                            look.model(),
                            BlockContent.modelFile(look).orElse(null));
            case TEXTURE -> drawTextureDetails(frame, look);
            case MATERIAL -> drawMaterialDetails(frame, look);
        }
    }

    /**
     * Draw a piece's ID and file, with Open.
     *
     * @param frame The current frame.
     * @param what What the piece is.
     * @param id Its ID, or null.
     * @param pieceFile Its file, or null if it can't be found.
     */
    private void drawFileDetails(
            UiFrame frame, String what, @Nullable String id, @Nullable Path pieceFile) {
        IkGui.textUnformatted(what + ": " + (id == null ? "none" : id));
        if (pieceFile == null) {
            IkGui.textDisabled(
                    id == null ? "The look names none" : "Not found in any content folder");
            return;
        }
        IkGui.textDisabled(pieceFile.toString());
        if (IkGui.button("Open##" + what)) {
            frame.fire(() -> open.accept(pieceFile));
        }
    }

    /**
     * Draw the texture's details: its picture, Open, and Open graph when a graph makes it.
     *
     * @param frame The current frame.
     * @param look The look.
     */
    private void drawTextureDetails(UiFrame frame, BlockContent.Look look) {
        final Path texture = BlockContent.textureFile(look).orElse(null);
        drawFileDetails(frame, "Texture", look.texture(), texture);
        if (texture == null) {
            return;
        }
        final AssetIndex.Snapshot snapshot = index.get();
        final AssetIndex.Entry entry = snapshot.entry(texture);
        final TextureHandle picture = entry == null ? null : thumbnails.apply(entry);
        if (picture != null) {
            IkGui.image(picture, PICTURE, PICTURE);
        }
        final Path graph = graphMaking(snapshot, texture);
        if (graph != null) {
            if (IkGui.button("Open graph")) {
                frame.fire(() -> open.accept(graph));
            }
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip(graph.toString());
            }
        }
    }

    /**
     * The texture graph that makes a texture, if any.
     *
     * @param snapshot The index.
     * @param texture The texture file.
     * @return The graph, or null.
     */
    static @Nullable Path graphMaking(AssetIndex.Snapshot snapshot, Path texture) {
        for (AssetIndex.Link link : snapshot.usedBy(texture)) {
            final AssetIndex.Entry user = snapshot.entry(link.file());
            if ("makes".equals(link.label())
                    && user != null
                    && EditorTypes.TEXTURE_GRAPH.equals(user.type())) {
                return link.file();
            }
        }
        return null;
    }

    /**
     * Draw the render material's details: its ID and file, and its values, edited in place.
     *
     * @param frame The current frame.
     * @param look The look.
     */
    private void drawMaterialDetails(UiFrame frame, BlockContent.Look look) {
        drawFileDetails(
                frame,
                "Render material",
                look.material(),
                BlockContent.materialFile(look).orElse(null));
        final Material material = live;
        if (material == null) {
            IkGui.textDisabled("Drawn with the default material, which isn't edited here");
            return;
        }
        materials.drawValues(material);
    }

    /** Put the look and its material back as saved. */
    public void revert() {
        if (live != null) {
            final Path materialFile = liveFile;
            if (materialFile != null && materialDirty()) {
                try {
                    live.setValues(AssetConvert.readMaterial(materialFile).material());
                } catch (RuntimeException e) {
                    log.warn("Could not read {} again: {}", materialFile, e.getMessage());
                }
            }
        }
        pending = null;
        if (lookDirty()) {
            edited = saved;
            rebuild.run();
        }
        report.accept("Reverted the look");
    }

    /**
     * Save what changed: the look's keys in its text, and the render material to its file and the
     * art source that makes it.
     *
     * @return Whether everything that changed was written.
     */
    public boolean save() {
        final Path lookFile = file;
        final BlockContent.Look look = edited;
        if (lookFile == null || look == null) {
            return false;
        }
        try {
            if (lookDirty()) {
                final String text = Files.readString(lookFile, StandardCharsets.UTF_8);
                Files.writeString(
                        lookFile,
                        rewrite(text, Objects.requireNonNull(saved), look),
                        StandardCharsets.UTF_8);
                saved = look;
            }
            if (materialDirty()) {
                saveMaterial(Objects.requireNonNull(live), Objects.requireNonNull(liveFile));
                savedValues = valuesOf(live);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not save the look {}", lookFile, e);
            report.accept("Couldn't save " + lookFile.getFileName() + ": " + e.getMessage());
            return false;
        }
        report.accept("Saved " + lookFile.getFileName());
        return true;
    }

    /**
     * Write a render material to its file, and to the art source that makes it, if one does, with
     * only the values that differ from the defaults, as hand-written sources are.
     *
     * @param material The values.
     * @param materialFile The render material file.
     * @throws UncheckedIOException If a file can't be written.
     */
    private void saveMaterial(Material material, Path materialFile) {
        final AssetIndex.Snapshot snapshot = index.get();
        Path source = null;
        for (AssetIndex.Link link : snapshot.usedBy(materialFile)) {
            if ("makes".equals(link.label())) {
                source = link.file();
            }
        }
        final MaterialSection.Textures textures =
                AssetConvert.readMaterial(materialFile).textures();
        AssetConvert.writeMaterial(materialFile, material, textures, source);
        if (source != null) {
            try {
                Files.writeString(
                        source,
                        MaterialText.writeDiffering(material, textures),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * A material's values as text, to compare.
     *
     * @param material The material.
     * @return The text.
     */
    private static String valuesOf(Material material) {
        return MaterialText.write(material, NO_TEXTURES);
    }

    /**
     * A look's text with the model, texture and material keys that changed set to the new look's,
     * keeping the rest: layout, comments, other keys, and keys that didn't change, written as they
     * were (a fluid's model is read as the default cube, but stays {@code fluid}).
     *
     * @param text The text.
     * @param before The look as the text has it.
     * @param after The look to write.
     * @return The new text.
     */
    static String rewrite(
            @NonNull String text,
            BlockContent.@NonNull Look before,
            BlockContent.@NonNull Look after) {
        String result = text;
        if (!after.model().equals(before.model())) {
            result = withKey(result, "model", after.model());
        }
        if (!Objects.equals(after.texture(), before.texture())) {
            result = withKey(result, "texture", after.texture());
        }
        if (!Objects.equals(after.material(), before.material())) {
            result = withKey(result, "material", after.material());
        }
        return result;
    }

    /**
     * A look's text with one key set, added before the closing brace, or taken out.
     *
     * @param text The text.
     * @param key The key.
     * @param value Its text value, or null to take it out.
     * @return The new text.
     */
    static String withKey(@NonNull String text, @NonNull String key, @Nullable String value) {
        final Matcher matcher =
                Pattern.compile("([{,]\\s*)" + key + "\\s*:\\s*\"[^\"]*\"").matcher(text);
        if (matcher.find()) {
            final String before = text.substring(0, matcher.start());
            final String after = text.substring(matcher.end());
            if (value != null) {
                return before + matcher.group(1) + key + ":\"" + value + "\"" + after;
            }
            if (matcher.group(1).startsWith(",")) {
                // Take the comma before it with it
                return before + after;
            }
            // The first key: take the comma after it instead
            return before + matcher.group(1) + after.replaceFirst("^\\s*,\\s*", "");
        }
        if (value == null) {
            return text;
        }
        final int close = text.lastIndexOf('}');
        if (close < 0) {
            return text;
        }
        final String head = text.substring(0, close).stripTrailing();
        final String separator = head.endsWith("{") ? "" : ", ";
        return head + separator + key + ":\"" + value + "\"" + text.substring(close);
    }
}
