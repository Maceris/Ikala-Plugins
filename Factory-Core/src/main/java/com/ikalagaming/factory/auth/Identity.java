package com.ikalagaming.factory.auth;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's master secret, from {@code <home>/identity/identity.key}: one file to back up, from
 * which their key for every server is derived. Each server gets a different key, so servers can't
 * link a player's accounts by comparing keys. See the Accounts and Identity design doc.
 */
@Slf4j
public final class Identity {

    /** How long the master secret is, in bytes. */
    public static final int SECRET_BYTES = 32;

    /** The first line of the file, which marks its format. */
    static final String HEADER = "lotomation-identity-v1";

    /** The HKDF salt for player keys. */
    private static final byte[] SALT = "lotomation identity".getBytes(StandardCharsets.UTF_8);

    /** The HKDF info prefix for player keys, followed by the server id. */
    private static final byte[] PLAYER_KEY_INFO =
            "lotomation player key v1".getBytes(StandardCharsets.UTF_8);

    /** The secret. */
    private final byte[] secret;

    /**
     * An identity.
     *
     * @param secret The master secret, 32 bytes; copied.
     */
    public Identity(byte @NonNull [] secret) {
        if (secret.length != SECRET_BYTES) {
            throw new IllegalArgumentException("A master secret is 32 bytes");
        }
        this.secret = secret.clone();
    }

    /**
     * Read the identity file, or make a new one if there is none. A file in an older format is kept
     * beside it, renamed, and a new secret is made.
     *
     * @param file The file.
     * @return The identity.
     * @throws IOException If the file can't be read or written.
     */
    public static Identity loadOrCreate(@NonNull Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
            if (lines.size() >= 2 && HEADER.equals(lines.get(0).strip())) {
                try {
                    return new Identity(Base64.getDecoder().decode(lines.get(1).strip()));
                } catch (IllegalArgumentException e) {
                    throw new IOException("The identity in " + file + " is damaged", e);
                }
            }
            final Path old = file.resolveSibling(file.getFileName() + ".old");
            log.warn("{} is in an old format; keeping it as {} and making a new one", file, old);
            Files.move(file, old, StandardCopyOption.REPLACE_EXISTING);
        }
        final byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        final Identity made = new Identity(secret);
        Arrays.fill(secret, (byte) 0);
        made.write(file);
        log.info("Made a new identity in {}", file);
        return made;
    }

    /**
     * Write the identity to a file only its owner can read, where the file system allows.
     *
     * @param file The file.
     * @throws IOException If it can't be written.
     */
    void write(@NonNull Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                HEADER + "\n" + Base64.getEncoder().encodeToString(secret) + "\n",
                StandardCharsets.US_ASCII);
        restrictToOwner(temporary);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Let only the file's owner read or write it. Best effort: a file system that can't say so
     * keeps its defaults.
     *
     * @param file The file.
     */
    static void restrictToOwner(@NonNull Path file) {
        try {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(
                        file,
                        EnumSet.of(
                                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                return;
            }
            final AclFileAttributeView acl =
                    Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl != null) {
                // The running user and the file's owner can differ, as when elevated
                final Set<UserPrincipal> allowed = new LinkedHashSet<>();
                allowed.add(Files.getOwner(file));
                allowed.add(
                        file.getFileSystem()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName(System.getProperty("user.name")));
                final List<AclEntry> entries = new ArrayList<>();
                for (UserPrincipal principal : allowed) {
                    entries.add(
                            AclEntry.newBuilder()
                                    .setType(AclEntryType.ALLOW)
                                    .setPrincipal(principal)
                                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                    .build());
                }
                acl.setAcl(entries);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            log.warn("Could not limit {} to its owner", file, e);
        }
    }

    /**
     * The key this player signs in to a server with, the same every time for that server.
     *
     * @param serverId The server's id.
     * @return The key pair.
     */
    public KeyPair keyFor(byte @NonNull [] serverId) {
        final byte[] info = new byte[PLAYER_KEY_INFO.length + serverId.length];
        System.arraycopy(PLAYER_KEY_INFO, 0, info, 0, PLAYER_KEY_INFO.length);
        System.arraycopy(serverId, 0, info, PLAYER_KEY_INFO.length, serverId.length);
        final byte[] seed = Hkdf.derive(secret, SALT, info, Ed25519.SEED_BYTES);
        try {
            return Ed25519.fromSeed(seed);
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }
}
