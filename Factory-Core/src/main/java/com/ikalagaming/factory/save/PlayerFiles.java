package com.ikalagaming.factory.save;

import com.ikalagaming.factory.kvt.Node;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Players' own files, {@code players/<account-id>.kvt} in a save: where they are and their game
 * mode, and later their inventory and the rest. See the Save File Format design doc.
 */
@Slf4j
public final class PlayerFiles {

    /** The folder in a save. */
    public static final String FOLDER = "players";

    /** The file extension. */
    public static final String EXTENSION = ".kvt";

    /** The format version written. */
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_WORLD = "world";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YAW = "yaw";
    private static final String KEY_PITCH = "pitch";
    private static final String KEY_GAME_MODE = "gameMode";

    /**
     * What a player's file holds.
     *
     * @param accountId Their account.
     * @param world The world they are in.
     * @param x Where their feet are.
     * @param y Where their feet are.
     * @param z Where their feet are.
     * @param yaw Which way they face, in degrees.
     * @param pitch How far up or down they look, in degrees.
     * @param gameMode Their game mode's name.
     */
    public record PlayerData(
            @NonNull String accountId,
            @NonNull String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            @NonNull String gameMode) {}

    /** Not made. */
    private PlayerFiles() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Where a player's file is.
     *
     * @param saveFolder The save.
     * @param accountId Their account id.
     * @return The path.
     * @throws IllegalArgumentException If the id isn't safe in a file name.
     */
    public static Path fileFor(@NonNull Path saveFolder, @NonNull String accountId) {
        if (!accountId.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("Not an account id: " + accountId);
        }
        return saveFolder.resolve(FOLDER).resolve(accountId + EXTENSION);
    }

    /**
     * Read a player's file.
     *
     * @param saveFolder The save.
     * @param accountId Their account id.
     * @return What it holds, or empty if they have none yet or it can't be read.
     */
    public static Optional<PlayerData> read(@NonNull Path saveFolder, @NonNull String accountId) {
        final Optional<Node> node = Saves.readNode(fileFor(saveFolder, accountId));
        if (node.isEmpty()) {
            return Optional.empty();
        }
        try {
            final Node n = node.get();
            return Optional.of(
                    new PlayerData(
                            accountId,
                            n.get(KEY_WORLD),
                            ((Number) n.get(KEY_X)).doubleValue(),
                            ((Number) n.get(KEY_Y)).doubleValue(),
                            ((Number) n.get(KEY_Z)).doubleValue(),
                            ((Number) n.get(KEY_YAW)).floatValue(),
                            ((Number) n.get(KEY_PITCH)).floatValue(),
                            n.hasChild(KEY_GAME_MODE) ? n.get(KEY_GAME_MODE) : ""));
        } catch (RuntimeException e) {
            log.warn("The player file for {} in {} is damaged", accountId, saveFolder, e);
            return Optional.empty();
        }
    }

    /**
     * Write a player's file.
     *
     * @param saveFolder The save.
     * @param data What to write.
     * @throws IOException If it can't be written.
     */
    public static void write(@NonNull Path saveFolder, @NonNull PlayerData data)
            throws IOException {
        final Path file = fileFor(saveFolder, data.accountId());
        Files.createDirectories(file.getParent());
        final Node node = new Node();
        node.add(KEY_VERSION, FORMAT_VERSION);
        node.add(KEY_ACCOUNT, data.accountId());
        node.add(KEY_WORLD, data.world());
        node.add(KEY_X, data.x());
        node.add(KEY_Y, data.y());
        node.add(KEY_Z, data.z());
        node.add(KEY_YAW, data.yaw());
        node.add(KEY_PITCH, data.pitch());
        node.add(KEY_GAME_MODE, data.gameMode());
        Saves.write(node, file);
    }
}
