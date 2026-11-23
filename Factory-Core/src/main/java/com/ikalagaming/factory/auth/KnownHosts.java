package com.ikalagaming.factory.auth;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.save.Saves;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The host keys a client has seen, by server id, in {@code <home>/known_hosts.kvt}, like SSH's
 * known hosts: the first key seen for a server is pinned, and a different key later is a warning.
 */
@Slf4j
public final class KnownHosts {

    /** The file in the Home. */
    public static final String FILE = "known_hosts.kvt";

    /** What a check found. */
    public enum Result {
        /** The server wasn't known, and its key is now pinned. */
        NEW,
        /** The key matches the pinned one. */
        MATCH,
        /** The key differs from the pinned one. */
        CHANGED
    }

    /** Guards the file. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The file. */
    private final Path file;

    /**
     * Known hosts kept in a file.
     *
     * @param file The file; it is made when the first host is pinned.
     */
    public KnownHosts(@NonNull Path file) {
        this.file = file;
    }

    /**
     * Check a server's host key against the pinned one, pinning it if the server is new.
     *
     * @param serverId The server's id.
     * @param hostKey Its encoded host public key.
     * @return What the check found.
     */
    public Result check(byte @NonNull [] serverId, byte @NonNull [] hostKey) {
        final String id = HexFormat.of().formatHex(serverId);
        lock.lock();
        try {
            final Node hosts = Saves.readNode(file).orElseGet(Node::new);
            if (hosts.hasChild(id) && hosts.get(id) instanceof String pinned) {
                final byte[] known;
                try {
                    known = Base64.getDecoder().decode(pinned);
                } catch (IllegalArgumentException e) {
                    log.warn("The pinned key for {} in {} is damaged", id, file);
                    return Result.CHANGED;
                }
                return Arrays.equals(known, hostKey) ? Result.MATCH : Result.CHANGED;
            }
            hosts.add(id, Base64.getEncoder().encodeToString(hostKey));
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Saves.write(hosts, file);
            } catch (IOException e) {
                log.warn("Could not pin the host key for {} in {}", id, file, e);
            }
            return Result.NEW;
        } finally {
            lock.unlock();
        }
    }
}
