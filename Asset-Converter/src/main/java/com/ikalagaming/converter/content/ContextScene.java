package com.ikalagaming.converter.content;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A scene to judge a block in: where blocks stand, on the 1 m grid, and which. The block being
 * looked at is {@value #SUBJECT}; others are named by ID, like {@code lotomation:grass}. Read from
 * {@code *.context.yml} files in the converter's data folder, so a modder can add their own:
 *
 * <pre>
 * name: Neighbors
 * order: 3                 # where it is listed, lowest first
 * blocks:
 *   - fill: [[-2, -1, -2], [2, -1, 2]]   # every cell from one corner to the other
 *     block: lotomation:grass
 *   - at: [0, 0, 0]                       # one cell, by its low corner
 *     block: subject
 *   - at: [1, 0, 0]
 *     block: lotomation:stone
 * </pre>
 *
 * @param name What it is called.
 * @param order Where it is listed, lowest first.
 * @param cells The blocks, one per cell.
 */
@Slf4j
public record ContextScene(@NonNull String name, int order, @NonNull List<Cell> cells) {
    /** The end of a context scene file's name. */
    public static final String SUFFIX = ".context.yml";

    /** The folder in the converter's data folder that holds them. */
    public static final String FOLDER = "context";

    /** The block name that stands for the block being looked at. */
    public static final String SUBJECT = "subject";

    /** The most cells a scene may fill, so a typo can't ask for millions. */
    static final int MAX_CELLS = 4096;

    /**
     * One block.
     *
     * @param x The cell's low corner across.
     * @param y Its low corner up.
     * @param z Its low corner along.
     * @param block The block's ID, or {@value #SUBJECT}.
     */
    public record Cell(int x, int y, int z, @NonNull String block) {}

    /**
     * Read every context scene in a folder, listed in their order then by name. Files that can't be
     * read are left out, with a warning.
     *
     * @param folder The folder.
     * @return The scenes.
     */
    public static List<ContextScene> loadAll(@NonNull Path folder) {
        final List<ContextScene> scenes = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return scenes;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file :
                    files.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).toList()) {
                try {
                    scenes.add(parse(Files.readString(file)));
                } catch (IOException | IllegalArgumentException | YAMLException e) {
                    log.warn("Could not read the context scene {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not list the context scenes in {}", folder, e);
        }
        scenes.sort(Comparator.comparingInt(ContextScene::order).thenComparing(ContextScene::name));
        return scenes;
    }

    /**
     * Read a context scene.
     *
     * @param text The YAML.
     * @return The scene.
     * @throws IllegalArgumentException If something is missing or of the wrong kind, or it fills
     *     too many cells.
     */
    public static ContextScene parse(@NonNull String text) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> map) || map.get("name") == null) {
            throw new IllegalArgumentException("a context scene is a map with a name");
        }
        final int order = map.get("order") instanceof Number number ? number.intValue() : 0;
        final List<Cell> cells = new ArrayList<>();
        if (!(map.get("blocks") instanceof List<?> blocks)) {
            throw new IllegalArgumentException("blocks is a list");
        }
        for (Object entry : blocks) {
            if (!(entry instanceof Map<?, ?> block) || block.get("block") == null) {
                throw new IllegalArgumentException("each of the blocks names its block");
            }
            final String id = String.valueOf(block.get("block"));
            if (block.get("fill") instanceof List<?> corners && corners.size() == 2) {
                final int[] from = cell(corners.get(0), "fill");
                final int[] to = cell(corners.get(1), "fill");
                for (int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); ++x) {
                    for (int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); ++y) {
                        for (int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); ++z) {
                            add(cells, new Cell(x, y, z, id));
                        }
                    }
                }
            } else if (block.containsKey("at")) {
                final int[] at = cell(block.get("at"), "at");
                add(cells, new Cell(at[0], at[1], at[2], id));
            } else {
                throw new IllegalArgumentException("each of the blocks has at or fill");
            }
        }
        return new ContextScene(String.valueOf(map.get("name")), order, List.copyOf(cells));
    }

    /**
     * Add a cell, keeping to the limit.
     *
     * @param cells The cells.
     * @param cell The new one.
     * @throws IllegalArgumentException If there would be too many.
     */
    private static void add(List<Cell> cells, Cell cell) {
        if (cells.size() >= MAX_CELLS) {
            throw new IllegalArgumentException("a context scene holds at most " + MAX_CELLS);
        }
        cells.add(cell);
    }

    /**
     * A cell's three whole numbers.
     *
     * @param value The YAML value.
     * @param key What it was, for the message.
     * @return The numbers.
     * @throws IllegalArgumentException If it isn't three whole numbers.
     */
    private static int[] cell(Object value, String key) {
        if (!(value instanceof List<?> list)
                || list.size() != 3
                || !(list.get(0) instanceof Integer x)
                || !(list.get(1) instanceof Integer y)
                || !(list.get(2) instanceof Integer z)) {
            throw new IllegalArgumentException(key + " is three whole numbers");
        }
        return new int[] {x, y, z};
    }

    /**
     * Whether the scene has a place for the block being looked at.
     *
     * @return True if a cell is {@value #SUBJECT}.
     */
    public boolean hasSubject() {
        return cells.stream().anyMatch(cell -> SUBJECT.equals(cell.block()));
    }
}
