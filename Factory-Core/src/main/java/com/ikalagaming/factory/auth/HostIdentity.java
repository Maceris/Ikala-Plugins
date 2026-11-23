package com.ikalagaming.factory.auth;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * A save's identity as a server: a random id made with the save and never changed, which players'
 * keys are derived from, and a host key that proves the server is the one players registered with.
 * Both live in {@code <save>/host/}, so they move with the save. See the Accounts and Identity
 * design doc.
 */
@Slf4j
public final class HostIdentity {

    /** The folder in a save. */
    public static final String FOLDER = "host";

    /** The file holding the server id. */
    public static final String ID_FILE = "server.id";

    /** The file holding the host key. */
    public static final String KEY_FILE = "host.key";

    /** The first line of the key file, which marks its format. */
    static final String KEY_HEADER = "lotomation-host-key-v1";

    /** The server id, 32 bytes. */
    private final byte[] serverId;

    /**
     * The host key pair.
     *
     * @return The keys.
     */
    @Getter private final KeyPair hostKey;

    /**
     * A host identity.
     *
     * @param serverId The server id.
     * @param hostKey The host key.
     */
    public HostIdentity(byte @NonNull [] serverId, @NonNull KeyPair hostKey) {
        if (serverId.length != SignInProof.SERVER_ID_BYTES) {
            throw new IllegalArgumentException("A server id is 32 bytes");
        }
        this.serverId = serverId.clone();
        this.hostKey = hostKey;
    }

    /**
     * The server id.
     *
     * @return A copy of it.
     */
    public byte[] getServerId() {
        return serverId.clone();
    }

    /**
     * The server id as hex, for file names and logs.
     *
     * @return The id.
     */
    public String serverIdHex() {
        return HexFormat.of().formatHex(serverId);
    }

    /**
     * The encoded host public key, as sent to clients.
     *
     * @return The key.
     */
    public byte[] publicKey() {
        return hostKey.getPublic().getEncoded();
    }

    /**
     * Read a save's host identity, or make it if the save has none yet.
     *
     * @param saveFolder The save.
     * @return The identity.
     * @throws IOException If the files can't be read or written, or are damaged.
     */
    public static HostIdentity loadOrCreate(@NonNull Path saveFolder) throws IOException {
        final Path folder = saveFolder.resolve(FOLDER);
        final Path idFile = folder.resolve(ID_FILE);
        final Path keyFile = folder.resolve(KEY_FILE);
        Files.createDirectories(folder);
        final byte[] id;
        if (Files.isRegularFile(idFile)) {
            try {
                id =
                        HexFormat.of()
                                .parseHex(
                                        Files.readString(idFile, StandardCharsets.US_ASCII)
                                                .strip());
            } catch (IllegalArgumentException e) {
                throw new IOException("The server id in " + idFile + " is damaged", e);
            }
        } else {
            id = new byte[SignInProof.SERVER_ID_BYTES];
            new SecureRandom().nextBytes(id);
            write(idFile, HexFormat.of().formatHex(id) + "\n");
            log.info("Made the server id for {}", saveFolder.getFileName());
        }
        final KeyPair key;
        if (Files.isRegularFile(keyFile)) {
            key = readKey(keyFile);
        } else {
            key = Ed25519.generate();
            final Base64.Encoder base64 = Base64.getEncoder();
            write(
                    keyFile,
                    KEY_HEADER
                            + "\n"
                            + base64.encodeToString(key.getPrivate().getEncoded())
                            + "\n"
                            + base64.encodeToString(key.getPublic().getEncoded())
                            + "\n");
            Identity.restrictToOwner(keyFile);
            log.info("Made the host key for {}", saveFolder.getFileName());
        }
        return new HostIdentity(id, key);
    }

    /**
     * Read a host key file.
     *
     * @param file The file.
     * @return The keys.
     * @throws IOException If it can't be read or is damaged.
     */
    private static KeyPair readKey(@NonNull Path file) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
        if (lines.size() < 3 || !KEY_HEADER.equals(lines.get(0).strip())) {
            throw new IOException(file + " isn't a host key");
        }
        try {
            final Base64.Decoder base64 = Base64.getDecoder();
            final PrivateKey privateKey = Ed25519.privateKey(base64.decode(lines.get(1).strip()));
            final PublicKey publicKey = Ed25519.publicKey(base64.decode(lines.get(2).strip()));
            return new KeyPair(publicKey, privateKey);
        } catch (IllegalArgumentException e) {
            throw new IOException("The host key in " + file + " is damaged", e);
        }
    }

    /**
     * Write a small text file whole.
     *
     * @param file The file.
     * @param text Its text.
     * @throws IOException If it can't be written.
     */
    private static void write(@NonNull Path file, @NonNull String text) throws IOException {
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.US_ASCII);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
