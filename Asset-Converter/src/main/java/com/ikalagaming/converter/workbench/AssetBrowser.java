package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.index.AssetCollections;
import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetSearch;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.index.GameTypes;
import com.ikalagaming.converter.index.Thumbnails;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Dropdown;
import com.ikalagaming.graphics.ui.Image;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.MenuItem;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.launcher.AppPaths;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The Assets panel: the asset index's roots and folders as a tree beside a grid or list of tiles,
 * found by type, namespace, folder and fuzzy search, with saved collections. Textures and images
 * show thumbnails from the cache, made in the background as their tiles are shown. A click selects
 * a tile, a double click opens its file, and a drag carries its path. Render thread only.
 */
@Slf4j
public final class AssetBrowser implements AutoCloseable {
    /** What dragging a tile carries: its file's path. */
    public static final String DRAG_TYPE = "asset-file";

    /** The choice meaning no filter. */
    private static final String ANY = "Any";

    /** The choice meaning files without a namespace. */
    private static final String NO_NAMESPACE = "(none)";

    /** The choice meaning every asset. */
    private static final String ALL_ASSETS = "All assets";

    /** A tile's side, in units. */
    private static final float TILE = 112;

    /** A thumbnail's side in a tile, in units. */
    private static final float THUMBNAIL = 88;

    /** A list row's width, in units. */
    private static final float ROW_WIDTH = 900;

    /** The folder tree's width, in units. */
    private static final float TREE_WIDTH = 220;

    /** The asset index. */
    private final AssetIndexService assets;

    /** Uploads thumbnails. */
    private final Textures textures;

    /** Opens a file in the workbench. */
    private final Consumer<Path> open;

    /** Shows a file's references, from a tile's menu, or null for no such item. */
    @Setter private @Nullable Consumer<Path> showReferences;

    /** Renders thumbnails of materials, models and looks, or null without one. */
    private @Nullable ThumbnailStudio studio;

    /**
     * Makes and keeps thumbnails. -- GETTER -- The thumbnail cache.
     *
     * @return The cache.
     */
    @Getter
    private final Thumbnails thumbnails =
            new Thumbnails(AppPaths.cache().resolve("thumbnails"), Thumbnails.DEFAULT_CAP);

    /**
     * The saved searches and hand-picked sets. -- GETTER -- The collections.
     *
     * @return The collections.
     */
    @Getter
    private final AssetCollections collections =
            new AssetCollections(AppPaths.state().resolve("asset-collections.yml"));

    /** The fuzzy search. */
    private final TextInput search;

    /** The type filter. */
    private final Dropdown typeBox = new Dropdown("asset-type");

    /** The namespace filter. */
    private final Dropdown namespaceBox = new Dropdown("asset-namespace");

    /** The collection shown. */
    private final Dropdown collectionBox = new Dropdown("asset-collection");

    /** A collection's name, for saving the search or picking into a set. */
    private final TextInput collectionName = new TextInput("asset-collection-name", "");

    /** How many files are shown. */
    private final Label count = new Label("asset-count", "");

    /** The tiles. */
    private final VirtualGrid grid = new VirtualGrid("asset-grid").border(true);

    /** Grid or list. */
    private final Button modeButton;

    /** The query. */
    private String text = "";

    /** The type shown, or null for any. */
    private @Nullable String type;

    /** The namespace shown, or null for any. */
    private @Nullable String namespace;

    /** The folder shown, or null for every root. */
    private @Nullable Path folder;

    /** The collection shown, or null for every asset. */
    private @Nullable String collection;

    /** Whether files are listed rather than tiled. */
    private boolean listed;

    /**
     * The files shown, in order. -- GETTER -- The files shown.
     *
     * @return The entries.
     */
    @Getter private List<AssetIndex.Entry> shown = List.of();

    /** The snapshot the files were found in. */
    private @Nullable AssetIndex.Snapshot shownFrom;

    /** Whether to find the files again. */
    private boolean dirty = true;

    /**
     * The file selected, for "use selected", or null. -- GETTER -- The file selected.
     *
     * @return The file, or null.
     */
    @Getter private @Nullable Path selected;

    /** Every file selected, the one clicked last among them, in the order picked. */
    private final Set<Path> selection = new LinkedHashSet<>();

    /** Each shown thumbnail's texture, by file. */
    private final Map<Path, TextureHandle> loaded = new HashMap<>();

    /** The types and namespaces offered, as last listed. */
    private List<String> typeOptions = List.of(ANY);

    /** The namespaces offered. */
    private List<String> namespaceOptions = List.of(ANY);

    /** The collections offered. */
    private List<String> collectionOptions = List.of(ALL_ASSETS);

    /** Each folder's subfolders holding indexed files, for the tree. */
    private Map<Path, SortedSet<Path>> children = Map.of();

    /**
     * Make the panel's state.
     *
     * @param assets The asset index.
     * @param textures Uploads thumbnails.
     * @param open Opens a file in the workbench.
     */
    public AssetBrowser(
            @NonNull AssetIndexService assets,
            @NonNull Textures textures,
            @NonNull Consumer<Path> open) {
        this.assets = assets;
        this.textures = textures;
        this.open = open;
        search =
                new TextInput("asset-search", "")
                        .onSubmit(
                                value -> {
                                    text = value;
                                    dirty = true;
                                })
                        .width(Sizing.fixed(200));
        typeBox.onChange(
                index -> {
                    type = index <= 0 ? null : typeOptions.get(index);
                    dirty = true;
                });
        namespaceBox.onChange(
                index -> {
                    final String picked = index <= 0 ? null : namespaceOptions.get(index);
                    namespace = NO_NAMESPACE.equals(picked) ? "" : picked;
                    dirty = true;
                });
        collectionBox.onChange(
                index -> showCollection(index <= 0 ? null : collectionOptions.get(index)));
        modeButton = new Button("asset-mode", "List").onClick(() -> setListed(!listed));
        collectionName
                .width(Sizing.fixed(140))
                .tooltip("A collection's name, for the buttons beside it");
        search.tooltip("Find by name or ID: the letters in order, like grs for grass");
        typeBox.width(Sizing.fixed(150)).tooltip("Show one type");
        namespaceBox.width(Sizing.fixed(130)).tooltip("Show one namespace");
        collectionBox.width(Sizing.fixed(150)).tooltip("Show a saved search or a hand-picked set");
        layoutGrid();
    }

    /**
     * Render thumbnails of materials, models and looks with a studio.
     *
     * @param renders The studio, closed with this panel.
     */
    public void setStudio(@NonNull ThumbnailStudio renders) {
        studio = renders;
    }

    /**
     * Whether a file gets a thumbnail: made here, or rendered.
     *
     * @param entry The file's entry.
     * @return True if it does.
     */
    private boolean hasPicture(AssetIndex.Entry entry) {
        return Thumbnails.canMake(entry) || studio != null && ThumbnailStudio.canRender(entry);
    }

    /**
     * The panel's content.
     *
     * @return The content.
     */
    public Node<?> content() {
        final Row toolbar =
                new Row("asset-toolbar")
                        .gap(4)
                        .add(
                                search,
                                typeBox,
                                namespaceBox,
                                collectionBox,
                                modeButton,
                                collectionName,
                                new Button("asset-save-search", "Save search")
                                        .tooltip(
                                                "Save what is shown, the search, filters and"
                                                        + " folder, as a collection named in the"
                                                        + " box.")
                                        .onClick(this::saveSearch),
                                new Button("asset-pick", "Pick selected")
                                        .tooltip(
                                                "Add the selected file to the hand-picked set"
                                                        + " named in the box.")
                                        .onClick(this::pickSelected),
                                new Button("asset-delete-collection", "Delete collection")
                                        .onClick(this::deleteCollection),
                                count);
        return new Row("assets-root")
                .gap(4)
                .align(Align.STRETCH)
                .add(
                        new Immediate("asset-tree", this::drawTree).width(Sizing.fixed(TREE_WIDTH)),
                        new Column("asset-main")
                                .gap(4)
                                .align(Align.STRETCH)
                                .width(Sizing.grow())
                                .add(
                                        new Immediate("asset-sync", this::sync)
                                                .height(Sizing.fixed(0)),
                                        toolbar,
                                        grid.height(Sizing.grow())));
    }

    /**
     * Find the files again when the index or the query changed. Each frame.
     *
     * @param frame The current frame.
     */
    private void sync(UiFrame frame) {
        assets.start();
        if (studio != null) {
            studio.update();
        }
        final AssetIndex.Snapshot snapshot = assets.getSnapshot();
        if (snapshot != shownFrom) {
            refreshChoices(snapshot);
            dirty = true;
        }
        if (dirty) {
            dirty = false;
            refilter(snapshot);
        }
    }

    /**
     * The types, namespaces, collections and folders to offer.
     *
     * @param snapshot The index.
     */
    private void refreshChoices(AssetIndex.Snapshot snapshot) {
        final SortedSet<String> types = new TreeSet<>();
        final SortedSet<String> namespaces = new TreeSet<>();
        final Map<Path, SortedSet<Path>> folders = new TreeMap<>();
        final List<Path> roots = snapshot.roots().stream().map(AssetIndex.Root::folder).toList();
        for (AssetIndex.Entry entry : snapshot.entries()) {
            types.add(entry.type());
            final String own = AssetSearch.namespaceOf(entry);
            namespaces.add(own.isEmpty() ? NO_NAMESPACE : own);
            Path at = entry.file().getParent();
            while (at != null && !roots.contains(at)) {
                final Path parent = at.getParent();
                if (parent == null) {
                    break;
                }
                folders.computeIfAbsent(parent, p -> new TreeSet<>()).add(at);
                at = parent;
            }
        }
        children = folders;
        typeOptions = withFirst(ANY, types);
        namespaceOptions = withFirst(ANY, namespaces);
        typeBox.options(typeOptions)
                .selected(type == null ? 0 : Math.max(0, typeOptions.indexOf(type)));
        namespaceBox
                .options(namespaceOptions)
                .selected(
                        namespace == null
                                ? 0
                                : Math.max(
                                        0,
                                        namespaceOptions.indexOf(
                                                namespace.isEmpty() ? NO_NAMESPACE : namespace)));
        refreshCollections();
    }

    /** The collections to offer. */
    private void refreshCollections() {
        final List<String> names = new ArrayList<>();
        names.add(ALL_ASSETS);
        collections.all().forEach(c -> names.add(c.name()));
        collectionOptions = List.copyOf(names);
        collectionBox
                .options(collectionOptions)
                .selected(
                        collection == null
                                ? 0
                                : Math.max(0, collectionOptions.indexOf(collection)));
    }

    /**
     * A choice before some others.
     *
     * @param first The first.
     * @param rest The others.
     * @return The list.
     */
    private static List<String> withFirst(String first, Set<String> rest) {
        final List<String> list = new ArrayList<>();
        list.add(first);
        list.addAll(rest);
        return List.copyOf(list);
    }

    /**
     * Find the files to show.
     *
     * @param snapshot The index.
     */
    private void refilter(AssetIndex.Snapshot snapshot) {
        shownFrom = snapshot;
        final AssetCollections.Collection picked =
                collection == null ? null : collections.get(collection);
        if (picked != null && picked.isSearch()) {
            shown = AssetSearch.find(snapshot, picked.search(), null);
        } else {
            shown =
                    AssetSearch.find(
                            snapshot,
                            new AssetSearch.Query(text, type, namespace, folder),
                            picked == null ? null : picked.files());
        }
        count.text(shown.size() + " files");
        // Thumbnails of files no longer shown are let go
        final Set<Path> still = new java.util.HashSet<>();
        shown.forEach(entry -> still.add(entry.file()));
        loaded.entrySet()
                .removeIf(
                        known -> {
                            if (still.contains(known.getKey())) {
                                return false;
                            }
                            textures.release(known.getValue());
                            return true;
                        });
        grid.releaseAll();
        grid.items(
                shown.size(),
                new VirtualGrid.Factory() {
                    @Override
                    public Node<?> create(int index) {
                        return cell(index);
                    }

                    @Override
                    public void release(int index, @NonNull Node<?> node) {
                        // A render not started for a tile scrolled away isn't wanted now
                        if (studio != null && index < shown.size()) {
                            studio.cancel(shown.get(index).file());
                        }
                    }
                });
    }

    /** Lay the grid out for tiles or rows. */
    private void layoutGrid() {
        if (listed) {
            grid.columns(1).cellSize(Length.u(ROW_WIDTH), Length.em(1.6f)).gap(Length.ZERO);
        } else {
            grid.columns(0).cellSize(TILE, TILE).gap(Length.u(4));
        }
    }

    /**
     * Show files as a list or as tiles.
     *
     * @param list True for a list.
     */
    public void setListed(boolean list) {
        listed = list;
        modeButton.text(listed ? "Tiles" : "List");
        layoutGrid();
        dirty = true;
    }

    /**
     * Make a tile or row.
     *
     * @param index The file's place among those shown.
     * @return The node.
     */
    private Node<?> cell(int index) {
        final AssetIndex.Entry entry = shown.get(index);
        final Path file = entry.file();
        final String name = file.getFileName().toString();
        final Selectable label =
                new Selectable(
                                "asset-name" + index,
                                listed
                                        ? name
                                                + "    "
                                                + entry.type()
                                                + (entry.id() == null ? "" : "    " + entry.id())
                                        : name)
                        .selected(selection.contains(file))
                        .onClick(() -> click(file))
                        .onDoubleClick(() -> open.accept(file))
                        .tooltip(file + (entry.id() == null ? "" : "\n" + entry.id()))
                        .contextMenu(menu(file))
                        .dragSource(DRAG_TYPE, () -> file, name);
        if (listed) {
            return label;
        }
        final Node<?> picture =
                hasPicture(entry)
                        ? new Image("asset-thumb" + index)
                                .texture(() -> thumbnail(entry))
                                .size(THUMBNAIL, THUMBNAIL)
                                .pixelSharp(true)
                                .onClick(() -> click(file))
                        : new Label("asset-type" + index, entry.type()).classes("muted");
        return new Column("asset-tile" + index).align(Align.CENTER).add(picture, label);
    }

    /**
     * A tile's menu: opening it, and its collections.
     *
     * @param file The tile's file.
     * @return The menu's items.
     */
    private MenuItem[] menu(Path file) {
        final List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Open", () -> open.accept(file)));
        final Consumer<Path> references = showReferences;
        if (references != null) {
            items.add(new MenuItem("Show references", () -> references.accept(file)));
        }
        for (AssetCollections.Collection set : collections.all()) {
            if (set.isSearch()) {
                continue;
            }
            if (set.files().contains(file)) {
                items.add(
                        new MenuItem("Remove from " + set.name(), () -> unpick(set.name(), file)));
            } else {
                items.add(new MenuItem("Add to " + set.name(), () -> pick(set.name(), file)));
            }
        }
        return items.toArray(MenuItem[]::new);
    }

    /**
     * A shown tile's thumbnail, uploaded once it is made.
     *
     * @param entry The tile's file.
     * @return The texture, or null until made.
     */
    @Nullable TextureHandle thumbnail(AssetIndex.Entry entry) {
        final TextureHandle known = loaded.get(entry.file());
        if (known != null) {
            return known;
        }
        final AssetIndex.Snapshot snapshot = shownFrom;
        if (snapshot == null) {
            return null;
        }
        Path made = null;
        if (Thumbnails.canMake(entry)) {
            made = thumbnails.get(entry, snapshot);
        } else if (studio != null && ThumbnailStudio.canRender(entry)) {
            final Path target = thumbnails.fileFor(entry, snapshot);
            if (Files.isRegularFile(target)) {
                made = target;
            } else {
                studio.request(entry, target, lookUsing(entry, snapshot));
            }
        }
        if (made == null) {
            return null;
        }
        try {
            final TextureHandle handle = textures.load(made.toString());
            loaded.put(entry.file(), handle);
            return handle;
        } catch (RuntimeException e) {
            log.debug("Can't upload the thumbnail {}", made, e);
            return null;
        }
    }

    /**
     * A look that uses a render material, directly or through the material a source makes, for the
     * texture to show it with.
     *
     * @param entry The material, or its source.
     * @param snapshot The index.
     * @return The first such look, or null.
     */
    private static @Nullable Path lookUsing(AssetIndex.Entry entry, AssetIndex.Snapshot snapshot) {
        Path material = entry.file();
        if (!EditorTypes.RENDER_MATERIAL.equals(entry.type())) {
            material = null;
            for (AssetIndex.Link link : snapshot.uses(entry.file())) {
                if ("makes".equals(link.label()) && link.file() != null) {
                    material = link.file();
                }
            }
            if (material == null) {
                return null;
            }
        }
        for (AssetIndex.Link user : snapshot.usedBy(material)) {
            final AssetIndex.Entry look = snapshot.entry(user.file());
            if ("material".equals(user.label())
                    && look != null
                    && GameTypes.LOOK.equals(look.type())) {
                return look.file();
            }
        }
        return null;
    }

    /**
     * Whether a shown file's thumbnail is in the cache, without making it.
     *
     * @param file The file.
     * @return True if it is.
     */
    public boolean isCached(@NonNull Path file) {
        final AssetIndex.Snapshot snapshot = shownFrom;
        final AssetIndex.Entry entry = snapshot == null ? null : snapshot.entry(file);
        return entry != null && thumbnails.has(entry, snapshot);
    }

    /**
     * The thumbnail of a shown file, once made.
     *
     * @param file The file.
     * @return True if its thumbnail is uploaded.
     */
    public boolean hasThumbnail(@NonNull Path file) {
        return loaded.containsKey(file.toAbsolutePath().normalize());
    }

    /**
     * Select a file.
     *
     * @param file The file, or null for none.
     */
    public void select(@Nullable Path file) {
        selected = file == null ? null : file.toAbsolutePath().normalize();
        selection.clear();
        if (selected != null) {
            selection.add(selected);
        }
        dirty = true;
    }

    /**
     * Add a file to the selection, or take it out if it is in it, as a ctrl-click does.
     *
     * @param file The file.
     */
    public void toggle(@NonNull Path file) {
        final Path normal = file.toAbsolutePath().normalize();
        if (selection.remove(normal)) {
            selected = selection.isEmpty() ? null : List.copyOf(selection).getLast();
        } else {
            selection.add(normal);
            selected = normal;
        }
        dirty = true;
    }

    /**
     * Select the shown files from the one clicked last to another, as a shift-click does.
     *
     * @param file The file at the range's other end.
     */
    public void selectRange(@NonNull Path file) {
        final Path normal = file.toAbsolutePath().normalize();
        final List<Path> files = shown.stream().map(AssetIndex.Entry::file).toList();
        final int from = selected == null ? -1 : files.indexOf(selected);
        final int to = files.indexOf(normal);
        if (from < 0 || to < 0) {
            select(normal);
            return;
        }
        final Path anchor = selected;
        selection.clear();
        for (int i = Math.min(from, to); i <= Math.max(from, to); ++i) {
            selection.add(files.get(i));
        }
        // The anchor stays, so another shift-click measures from it
        selected = anchor;
        dirty = true;
    }

    /**
     * A tile clicked: with ctrl, toggled in the selection; with shift, a range; else selected.
     *
     * @param file The tile's file.
     */
    private void click(Path file) {
        final var io = com.ikalagaming.graphics.gui.IkGui.getIO();
        if (io.keyCtrl) {
            toggle(file);
        } else if (io.keyShift) {
            selectRange(file);
        } else {
            select(file);
        }
    }

    /**
     * Every file selected.
     *
     * @return The files, in the order picked.
     */
    public List<Path> getSelection() {
        return List.copyOf(selection);
    }

    /**
     * Search for text.
     *
     * @param query The text.
     */
    public void search(@NonNull String query) {
        text = query;
        search.text(query);
        dirty = true;
    }

    /**
     * Show one type.
     *
     * @param shownType The type, or null for any.
     */
    public void setType(@Nullable String shownType) {
        type = shownType;
        typeBox.selected(shownType == null ? 0 : Math.max(0, typeOptions.indexOf(shownType)));
        dirty = true;
    }

    /**
     * Show the files in a folder.
     *
     * @param shownFolder The folder, or null for every root.
     */
    public void setFolder(@Nullable Path shownFolder) {
        folder = shownFolder == null ? null : shownFolder.toAbsolutePath().normalize();
        dirty = true;
    }

    /**
     * Show a collection.
     *
     * @param name Its name, or null for every asset.
     */
    public void showCollection(@Nullable String name) {
        collection = name;
        collectionBox.selected(name == null ? 0 : Math.max(0, collectionOptions.indexOf(name)));
        dirty = true;
    }

    /** Save the search shown as a collection named in the box. */
    private void saveSearch() {
        saveSearch(collectionName.text().trim());
    }

    /**
     * Save the search shown as a collection.
     *
     * @param name Its name.
     */
    public void saveSearch(@NonNull String name) {
        if (name.isEmpty()) {
            return;
        }
        collections.saveSearch(name, new AssetSearch.Query(text, type, namespace, folder));
        refreshCollections();
    }

    /** Add the selected file to the set named in the box. */
    private void pickSelected() {
        if (selected != null) {
            pick(collectionName.text().trim(), selected);
        }
    }

    /**
     * Add a file to a hand-picked set.
     *
     * @param name The set's name.
     * @param file The file.
     */
    public void pick(@NonNull String name, @NonNull Path file) {
        if (!name.isEmpty() && collections.add(name, file)) {
            refreshCollections();
            dirty = true;
        }
    }

    /**
     * Take a file out of a hand-picked set.
     *
     * @param name The set's name.
     * @param file The file.
     */
    private void unpick(String name, Path file) {
        collections.remove(name, file);
        dirty = true;
    }

    /** Delete the collection shown. */
    private void deleteCollection() {
        if (collection != null) {
            collections.delete(collection);
            collection = null;
            refreshCollections();
            dirty = true;
        }
    }

    /**
     * Draw the roots and their folders; a click shows a folder's files.
     *
     * @param frame The current frame.
     */
    private void drawTree(UiFrame frame) {
        if (IkGui.selectable("All roots", folder == null)) {
            frame.fire(() -> setFolder(null));
        }
        for (Path root : assets.getRoots()) {
            drawFolder(frame, root, rootLabel(root));
        }
    }

    /**
     * Draw a folder and, while open, its subfolders.
     *
     * @param frame The current frame.
     * @param at The folder.
     * @param label Its name.
     */
    private void drawFolder(UiFrame frame, Path at, String label) {
        final SortedSet<Path> below = children.getOrDefault(at, new TreeSet<>());
        int flags = TreeNodeFlags.OPEN_ON_ARROW | TreeNodeFlags.SPAN_AVAIL_WIDTH;
        if (at.equals(folder)) {
            flags |= TreeNodeFlags.SELECTED;
        }
        if (below.isEmpty()) {
            flags |= TreeNodeFlags.LEAF;
        }
        final boolean open = IkGui.treeNodeEx(label + "##" + at, flags);
        if (IkGui.isItemClicked()) {
            frame.fire(() -> setFolder(at));
        }
        if (open) {
            for (Path child : below) {
                drawFolder(frame, child, child.getFileName().toString());
            }
            IkGui.treePop();
        }
    }

    /**
     * A root's name: its folder's, after its parent's.
     *
     * @param root The root.
     * @return The name.
     */
    static String rootLabel(Path root) {
        final Path name = root.getFileName();
        final Path parent = root.getParent();
        if (name == null) {
            return root.toString();
        }
        return parent == null || parent.getFileName() == null
                ? name.toString()
                : parent.getFileName() + "/" + name;
    }

    /**
     * How many thumbnails were made this run, rather than found in the cache.
     *
     * @return The count.
     */
    public int thumbnailsMade() {
        return thumbnails.getMade();
    }

    /**
     * Wait for the thumbnails of the files shown, for checks.
     *
     * @return How many were made or found.
     */
    public int awaitThumbnails() {
        final AssetIndex.Snapshot snapshot = shownFrom;
        int made = 0;
        if (snapshot == null) {
            return 0;
        }
        for (AssetIndex.Entry entry : shown) {
            final var future = thumbnails.make(entry, snapshot);
            if (future != null) {
                future.join();
                ++made;
            }
        }
        return made;
    }

    /**
     * Wait for the shown files' rendered thumbnails, for checks. Render thread, so this only says
     * how many are left; the frames go on while a check waits.
     *
     * @return How many renders wait.
     */
    public int rendersLeft() {
        return studio == null ? 0 : studio.pending();
    }

    /**
     * How many shown files have a thumbnail in the cache.
     *
     * @return The count.
     */
    public int cachedCount() {
        final AssetIndex.Snapshot snapshot = shownFrom;
        if (snapshot == null) {
            return 0;
        }
        int count = 0;
        for (AssetIndex.Entry entry : shown) {
            if (hasPicture(entry) && Files.isRegularFile(thumbnails.fileFor(entry, snapshot))) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public void close() {
        if (studio != null) {
            studio.close();
        }
        thumbnails.close();
        loaded.values().forEach(textures::release);
        loaded.clear();
    }
}
