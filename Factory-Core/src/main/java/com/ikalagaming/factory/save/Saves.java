package com.ikalagaming.factory.save;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.NodeType;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.WorldgenHash;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/**
 * Creates, lists and updates save folders. A save folder holds {@value #SAVE_FILE}, which names the
 * save and lists its worlds, and a folder per world under {@value #WORLDS_FOLDER}, each with its
 * {@value #WORLD_FILE}. Both are binary KVT. See the Save File Format design doc.
 */
@Slf4j
public final class Saves {

    /** The file in a save folder that describes the save. */
    public static final String SAVE_FILE = "save.kvt";

    /** The folder in a save folder that holds its worlds. */
    public static final String WORLDS_FOLDER = "worlds";

    /** The file in a world's folder that describes the world. */
    public static final String WORLD_FILE = "world.kvt";

    /** The version of the save and world files this writes. */
    public static final int FORMAT_VERSION = 1;

    /** The longest folder name made from a save's name. */
    static final int MAX_FOLDER_LENGTH = 64;

    /** The folder name used when nothing usable is left of a save's name. */
    static final String FALLBACK_FOLDER = "World";

    /** Keys in {@value #SAVE_FILE} and {@value #WORLD_FILE}. */
    static final String KEY_VERSION = "version";

    static final String KEY_NAME = "name";
    static final String KEY_CREATED = "created";
    static final String KEY_LAST_PLAYED = "lastPlayed";
    static final String KEY_GAME_VERSION = "gameVersion";
    static final String KEY_DEV_BUILD = "devBuild";
    static final String KEY_WORLDS = "worlds";
    static final String KEY_WORLD_TYPE = "worldType";
    static final String KEY_SEED = "seed";
    static final String KEY_SPAWN = "spawn";
    static final String KEY_TIME = "time";
    static final String KEY_SPAWN_PLATFORM = "spawnPlatform";

    /**
     * What a world's {@value #WORLD_FILE} says about it.
     *
     * @param folder The world's folder name.
     * @param worldType Its world type, or blank if it can't be read.
     * @param seed Its seed.
     * @param spawn Where new players start, or null if it hasn't been found yet.
     * @param time The world's clock, in ticks (see {@link WorldTime}).
     */
    public record WorldInfo(
            @NonNull String folder,
            @NonNull String worldType,
            long seed,
            SpawnFinder.Spawn spawn,
            long time) {}

    /** Folder names Windows reserves, which can't be used whatever the extension. */
    private static final Set<String> RESERVED_NAMES =
            Set.of(
                    "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
                    "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7",
                    "LPT8", "LPT9");

    /**
     * Create a save for a new game: its folder, {@value #SAVE_FILE}, and one world.
     *
     * @param savesFolder The folder holding all saves, made if needed.
     * @param game The player's choices. The name must not be blank.
     * @param gameVersion The version of the game creating it.
     * @param random Picks the seed when none was typed.
     * @param clock Gives the creation time.
     * @return The new save.
     * @throws IOException If the save can't be written.
     * @throws IllegalArgumentException If the name is blank.
     */
    public static SaveSummary create(
            @NonNull Path savesFolder,
            @NonNull NewGame game,
            @NonNull String gameVersion,
            @NonNull RandomGenerator random,
            @NonNull Clock clock)
            throws IOException {
        final String name = game.name().strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("A save needs a name");
        }
        Files.createDirectories(savesFolder);
        final String folder = Saves.folderNameFor(savesFolder, name);
        final Path saveFolder = savesFolder.resolve(folder);
        final String world = Saves.worldFolderFor(game.worldType());
        final long seed = Saves.parseSeed(game.seedText(), random);
        final long now = clock.millis();
        final boolean devBuild = Saves.isDevBuild(gameVersion);

        Files.createDirectories(saveFolder.resolve(WORLDS_FOLDER).resolve(world));
        Node worldNode = new Node();
        worldNode.add(KEY_VERSION, FORMAT_VERSION);
        worldNode.add(KEY_WORLD_TYPE, game.worldType());
        worldNode.add(KEY_SEED, seed);
        worldNode.add(KEY_TIME, WorldTime.START_TICKS);
        Saves.write(
                worldNode, saveFolder.resolve(WORLDS_FOLDER).resolve(world).resolve(WORLD_FILE));

        Node save = new Node();
        save.add(KEY_VERSION, FORMAT_VERSION);
        save.add(KEY_NAME, name);
        save.add(KEY_CREATED, now);
        save.add(KEY_LAST_PLAYED, now);
        save.add(KEY_GAME_VERSION, gameVersion);
        save.add(KEY_DEV_BUILD, devBuild);
        save.add(KEY_WORLDS, NodeType.STRING_ARRAY, List.of(world));
        // Written last, so a folder without it is a save that never finished being made
        Saves.write(save, saveFolder.resolve(SAVE_FILE));

        return new SaveSummary(
                folder,
                name,
                now,
                now,
                gameVersion,
                devBuild,
                List.of(world),
                game.worldType(),
                seed);
    }

    /**
     * A folder name for a save that is free in the saves folder. Characters that aren't safe in
     * folder names on every system become underscores, and a number is added if the name is taken,
     * so two saves can share a name.
     *
     * @param savesFolder The folder holding all saves.
     * @param name The save's name.
     * @return A folder name that doesn't exist yet.
     */
    public static String folderNameFor(@NonNull Path savesFolder, @NonNull String name) {
        final String base = Saves.safeFolderName(name);
        String candidate = base;
        for (int number = 2; Files.exists(savesFolder.resolve(candidate)); ++number) {
            candidate = base + " " + number;
        }
        return candidate;
    }

    /**
     * Whether a game version is a development build, which pre-release versions are.
     *
     * @param gameVersion The version.
     * @return True for a version with a pre-release part, like {@code 0.0.1-alpha}.
     */
    public static boolean isDevBuild(@NonNull String gameVersion) {
        final int build = gameVersion.indexOf('+');
        final String version = build < 0 ? gameVersion : gameVersion.substring(0, build);
        return version.contains("-");
    }

    /**
     * Read every save in a folder, most recently played first. Folders without a readable {@value
     * #SAVE_FILE} are skipped.
     *
     * @param savesFolder The folder holding all saves.
     * @return The saves, empty if the folder doesn't exist.
     */
    public static List<SaveSummary> list(@NonNull Path savesFolder) {
        if (!Files.isDirectory(savesFolder)) {
            return List.of();
        }
        final List<SaveSummary> saves = new ArrayList<>();
        try (Stream<Path> folders = Files.list(savesFolder)) {
            folders.filter(Files::isDirectory)
                    .forEach(folder -> Saves.read(folder).ifPresent(saves::add));
        } catch (IOException e) {
            log.warn("Could not list the saves in {}", savesFolder, e);
        }
        saves.sort(
                Comparator.comparingLong(SaveSummary::lastPlayed)
                        .reversed()
                        .thenComparing(SaveSummary::name, String.CASE_INSENSITIVE_ORDER));
        return saves;
    }

    /**
     * Record that a save was just opened.
     *
     * @param saveFolder The save's folder.
     * @param clock Gives the time.
     * @return Whether it was recorded.
     */
    public static boolean markPlayed(@NonNull Path saveFolder, @NonNull Clock clock) {
        final Path file = saveFolder.resolve(SAVE_FILE);
        final Optional<Node> save = Saves.readNode(file);
        if (save.isEmpty()) {
            return false;
        }
        save.get().add(KEY_LAST_PLAYED, clock.millis());
        try {
            Saves.write(save.get(), file);
            return true;
        } catch (IOException e) {
            log.warn("Could not record when {} was played", saveFolder, e);
            return false;
        }
    }

    /**
     * Turn the seed a player typed into a world seed.
     *
     * @param text The text: blank for a random seed, a whole number to use as is, or any other
     *     text, which is hashed, so the same text always gives the same world.
     * @param random Picks the seed when the text is blank.
     * @return The seed.
     */
    public static long parseSeed(@NonNull String text, @NonNull RandomGenerator random) {
        final String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return random.nextLong();
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return WorldgenHash.fnv1a64(trimmed);
        }
    }

    /**
     * Read one save's summary.
     *
     * @param saveFolder The save's folder.
     * @return The summary, or empty if {@value #SAVE_FILE} is missing or can't be read.
     */
    public static Optional<SaveSummary> read(@NonNull Path saveFolder) {
        final Optional<Node> maybeSave = Saves.readNode(saveFolder.resolve(SAVE_FILE));
        if (maybeSave.isEmpty()) {
            return Optional.empty();
        }
        final Node save = maybeSave.get();
        final String folder = saveFolder.getFileName().toString();
        final List<String> worlds = Saves.stringList(save, KEY_WORLDS);
        String worldType = "";
        long seed = 0;
        if (!worlds.isEmpty()) {
            final Optional<Node> world =
                    Saves.readNode(
                            saveFolder
                                    .resolve(WORLDS_FOLDER)
                                    .resolve(worlds.getFirst())
                                    .resolve(WORLD_FILE));
            if (world.isPresent()) {
                worldType = Saves.string(world.get(), KEY_WORLD_TYPE, "");
                seed = Saves.number(world.get(), KEY_SEED, 0L);
            }
        }
        return Optional.of(
                new SaveSummary(
                        folder,
                        Saves.string(save, KEY_NAME, folder),
                        Saves.number(save, KEY_CREATED, 0L),
                        Saves.number(save, KEY_LAST_PLAYED, 0L),
                        Saves.string(save, KEY_GAME_VERSION, ""),
                        Boolean.TRUE.equals(Saves.value(save, KEY_DEV_BUILD)),
                        worlds,
                        worldType,
                        seed));
    }

    /**
     * A name made safe for a folder on every system.
     *
     * @param name The name.
     * @return The folder name, which might already be taken.
     */
    static String safeFolderName(@NonNull String name) {
        StringBuilder safe = new StringBuilder();
        name.strip()
                .codePoints()
                .forEach(
                        c -> {
                            boolean allowed =
                                    (c >= '0' && c <= '9')
                                            || (c >= 'a' && c <= 'z')
                                            || (c >= 'A' && c <= 'Z')
                                            || c == '-'
                                            || c == '_'
                                            || c == '.'
                                            || c == ' ';
                            safe.append(allowed ? (char) c : '_');
                        });
        String result = safe.toString();
        if (result.length() > MAX_FOLDER_LENGTH) {
            result = result.substring(0, MAX_FOLDER_LENGTH);
        }
        // Windows drops trailing dots and spaces, and a leading dot hides the folder elsewhere
        result = result.replaceAll("[. ]+$", "").replaceAll("^[. ]+", "");
        if (result.isEmpty() || result.chars().allMatch(c -> c == '_')) {
            return FALLBACK_FOLDER;
        }
        final int dot = result.indexOf('.');
        final String stem = dot < 0 ? result : result.substring(0, dot);
        if (RESERVED_NAMES.contains(stem.strip().toUpperCase(Locale.ROOT))) {
            result = "_" + result;
        }
        return result;
    }

    /**
     * The folder name for a save's first world, from its world type's path.
     *
     * @param worldType The world type, such as {@code lotomation:overworld}.
     * @return The folder name, such as {@code overworld}.
     */
    static String worldFolderFor(@NonNull String worldType) {
        final int colon = worldType.indexOf(':');
        final String path = colon < 0 ? worldType : worldType.substring(colon + 1);
        return Saves.safeFolderName(path.replace('/', '_'));
    }

    /**
     * Read a binary KVT file.
     *
     * @param file The file.
     * @return The tree, or empty if the file is missing or broken.
     */
    private static Optional<Node> readNode(@NonNull Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream stream = Files.newInputStream(file)) {
            return TreeBinarySerialization.read(stream);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}", file, e);
            return Optional.empty();
        }
    }

    /**
     * Write a binary KVT file whole: to a temporary file first, then moved into place, so a crash
     * never leaves half of one.
     *
     * @param node The tree.
     * @param file The file.
     * @throws IOException If it can't be written.
     */
    private static void write(@NonNull Node node, @NonNull Path file) throws IOException {
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream stream = Files.newOutputStream(temporary)) {
            if (!TreeBinarySerialization.write(node, stream)) {
                throw new IOException("Could not encode " + file);
            }
        }
        Files.move(
                temporary,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * A value in a tree, if it is there.
     *
     * @param node The tree.
     * @param key The key.
     * @return The value, or null.
     */
    private static Object value(@NonNull Node node, @NonNull String key) {
        return node.hasChild(key) ? node.get(key) : null;
    }

    /**
     * A text value in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @param fallback The value if it is missing or not text.
     * @return The text.
     */
    private static String string(@NonNull Node node, @NonNull String key, String fallback) {
        return Saves.value(node, key) instanceof String text ? text : fallback;
    }

    /**
     * A whole number in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @param fallback The value if it is missing or not a number.
     * @return The number.
     */
    private static long number(@NonNull Node node, @NonNull String key, long fallback) {
        return Saves.value(node, key) instanceof Number number ? number.longValue() : fallback;
    }

    /**
     * A list of text in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @return The list, empty if it is missing or not a list.
     */
    private static List<String> stringList(@NonNull Node node, @NonNull String key) {
        if (Saves.value(node, key) instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Read what a world's {@value #WORLD_FILE} says about it.
     *
     * @param saveFolder The save's folder.
     * @param world The world's folder name, from {@link SaveSummary#worlds()}.
     * @return The world, or empty if its file is missing or can't be read.
     */
    public static Optional<WorldInfo> readWorld(@NonNull Path saveFolder, @NonNull String world) {
        final Optional<Node> node =
                Saves.readNode(
                        saveFolder.resolve(WORLDS_FOLDER).resolve(world).resolve(WORLD_FILE));
        if (node.isEmpty()) {
            return Optional.empty();
        }
        SpawnFinder.Spawn spawn = null;
        if (Saves.value(node.get(), KEY_SPAWN) instanceof List<?> list && list.size() == 3) {
            spawn =
                    new SpawnFinder.Spawn(
                            ((Number) list.get(0)).intValue(),
                            ((Number) list.get(1)).intValue(),
                            ((Number) list.get(2)).intValue(),
                            Boolean.TRUE.equals(Saves.value(node.get(), KEY_SPAWN_PLATFORM)));
        }
        return Optional.of(
                new WorldInfo(
                        world,
                        Saves.string(node.get(), KEY_WORLD_TYPE, ""),
                        Saves.number(node.get(), KEY_SEED, 0L),
                        spawn,
                        Saves.number(node.get(), KEY_TIME, WorldTime.START_TICKS)));
    }

    /**
     * Record where new players start in a world.
     *
     * @param saveFolder The save's folder.
     * @param world The world's folder name.
     * @param spawn The spawn.
     * @throws IOException If the world's file is missing or can't be written.
     */
    public static void writeSpawn(
            @NonNull Path saveFolder, @NonNull String world, @NonNull SpawnFinder.Spawn spawn)
            throws IOException {
        final Path file = saveFolder.resolve(WORLDS_FOLDER).resolve(world).resolve(WORLD_FILE);
        final Node node =
                Saves.readNode(file).orElseThrow(() -> new IOException("Can't read " + file));
        node.add(KEY_SPAWN, NodeType.INTEGER_ARRAY, List.of(spawn.x(), spawn.y(), spawn.z()));
        node.add(KEY_SPAWN_PLATFORM, spawn.platform());
        Saves.write(node, file);
    }

    /**
     * Record a world's clock.
     *
     * @param saveFolder The save's folder.
     * @param world The world's folder name.
     * @param ticks The time, in ticks.
     * @throws IOException If the world's file is missing or can't be written.
     */
    public static void writeTime(@NonNull Path saveFolder, @NonNull String world, long ticks)
            throws IOException {
        final Path file = saveFolder.resolve(WORLDS_FOLDER).resolve(world).resolve(WORLD_FILE);
        final Node node =
                Saves.readNode(file).orElseThrow(() -> new IOException("Can't read " + file));
        node.add(KEY_TIME, ticks);
        Saves.write(node, file);
    }

    /** Private constructor so this class is not instantiated. */
    private Saves() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
