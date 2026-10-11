package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Saved Lab views: a name, a target, a plane, where the view is centered and its zoom. Kept in a
 * KVT file in the plugin's data folder, so they survive restarts and can be shared.
 */
@Slf4j
public final class Bookmarks {

    /**
     * One saved view.
     *
     * @param name What it is called.
     * @param target What it shows.
     * @param plane The plane.
     * @param center The world position at the middle, as x, y, z.
     * @param scale Blocks per pixel.
     */
    public record Bookmark(
            @NonNull String name,
            @NonNull String target,
            WorldgenDebug.@NonNull Plane plane,
            double @NonNull [] center,
            double scale) {}

    /** Where they are saved. */
    private final Path file;

    /** The bookmarks, in the order added. */
    private final List<Bookmark> entries = new ArrayList<>();

    /**
     * Read bookmarks from a file, if it exists.
     *
     * @param file Where they are kept.
     */
    public Bookmarks(@NonNull Path file) {
        this.file = file;
        load();
    }

    /**
     * The bookmarks.
     *
     * @return The bookmarks, in the order added.
     */
    public List<Bookmark> list() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Add a bookmark and save.
     *
     * @param bookmark The bookmark.
     */
    public void add(@NonNull Bookmark bookmark) {
        entries.add(bookmark);
        save();
    }

    /**
     * Remove a bookmark and save.
     *
     * @param index Which one.
     */
    public void remove(int index) {
        entries.remove(index);
        save();
    }

    /** Read the file, keeping anything readable. */
    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            final Optional<Node> root =
                    TreeStringSerialization.fromString(
                            Files.readString(file, StandardCharsets.UTF_8));
            if (root.isEmpty() || !root.get().hasChild("bookmarks")) {
                return;
            }
            for (Node node : root.get().getNodeArray("bookmarks")) {
                entries.add(
                        new Bookmark(
                                node.getString("name"),
                                node.getString("target"),
                                WorldgenDebug.Plane.valueOf(node.getString("plane")),
                                node.getDoubleArray("center").stream()
                                        .mapToDouble(Double::doubleValue)
                                        .toArray(),
                                node.getDouble("scale")));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the Worldgen Lab bookmarks from {}", file, e);
        }
    }

    /** Write the file. */
    private void save() {
        Node root = new Node();
        root.addNodeArray("bookmarks");
        List<Node> nodes = root.getNodeArray("bookmarks");
        for (Bookmark bookmark : entries) {
            Node node = new Node();
            node.addString("name", bookmark.name());
            node.addString("target", bookmark.target());
            node.addString("plane", bookmark.plane().name());
            List<Double> center = new ArrayList<>();
            for (double v : bookmark.center()) {
                center.add(v);
            }
            node.addDoubleArray("center", center);
            node.addDouble("scale", bookmark.scale());
            nodes.add(node);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, TreeStringSerialization.toString(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not save the Worldgen Lab bookmarks to {}", file, e);
        }
    }
}
