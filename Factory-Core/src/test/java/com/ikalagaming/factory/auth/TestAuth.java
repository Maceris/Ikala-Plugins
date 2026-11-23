package com.ikalagaming.factory.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.save.PlayerFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;

/** Tests for identities, host keys, sign-in proofs, accounts and player files. */
class TestAuth {

    private static final HexFormat HEX = HexFormat.of();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);

    /** HKDF gives RFC 5869's first test vector. */
    @Test
    void testHkdf() {
        final byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        final byte[] okm =
                Hkdf.derive(
                        ikm,
                        HEX.parseHex("000102030405060708090a0b0c"),
                        HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"),
                        42);
        assertEquals(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                HEX.formatHex(okm));
    }

    /** The same seed always gives the same keys, and the keys sign and verify. */
    @Test
    void testSeededKeys() {
        final byte[] seed = new byte[Ed25519.SEED_BYTES];
        seed[0] = 7;
        final KeyPair first = Ed25519.fromSeed(seed);
        final KeyPair second = Ed25519.fromSeed(seed);
        assertArrayEquals(first.getPublic().getEncoded(), second.getPublic().getEncoded());
        final byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        final byte[] signature = Ed25519.sign(first.getPrivate(), data);
        assertTrue(Ed25519.verify(second.getPublic(), data, signature));
        assertFalse(Ed25519.verify(Ed25519.generate().getPublic(), data, signature));
        assertFalse(
                Ed25519.verify(
                        first.getPublic(), "hellO".getBytes(StandardCharsets.UTF_8), signature));
        assertArrayEquals(
                first.getPublic().getEncoded(),
                Ed25519.publicKey(first.getPublic().getEncoded()).getEncoded());
        assertThrows(IllegalArgumentException.class, () -> Ed25519.fromSeed(new byte[5]));
    }

    /**
     * An identity gives each server its own key, the same every time, and survives being saved; an
     * old-format file is kept aside.
     *
     * @param folder A scratch folder.
     */
    @Test
    void testIdentity(@TempDir Path folder) throws Exception {
        final Path file = folder.resolve("identity").resolve("identity.key");
        final Identity made = Identity.loadOrCreate(file);
        final byte[] serverA = new byte[32];
        final byte[] serverB = new byte[32];
        serverB[31] = 1;
        final byte[] keyA = made.keyFor(serverA).getPublic().getEncoded();
        assertFalse(Arrays.equals(keyA, made.keyFor(serverB).getPublic().getEncoded()));
        final Identity loaded = Identity.loadOrCreate(file);
        assertArrayEquals(keyA, loaded.keyFor(serverA).getPublic().getEncoded());

        Files.writeString(file, "-----BEGIN RSA PRIVATE KEY-----\n");
        final Identity replaced = Identity.loadOrCreate(file);
        assertTrue(Files.exists(file.resolveSibling("identity.key.old")));
        assertFalse(Arrays.equals(keyA, replaced.keyFor(serverA).getPublic().getEncoded()));
    }

    /**
     * A save's host identity is made once and read back the same.
     *
     * @param folder A scratch save folder.
     */
    @Test
    void testHostIdentity(@TempDir Path folder) throws Exception {
        final HostIdentity made = HostIdentity.loadOrCreate(folder);
        final HostIdentity loaded = HostIdentity.loadOrCreate(folder);
        assertArrayEquals(made.getServerId(), loaded.getServerId());
        assertArrayEquals(made.publicKey(), loaded.publicKey());
        final byte[] data = new byte[] {1, 2, 3};
        assertTrue(
                Ed25519.verify(
                        made.getHostKey().getPublic(),
                        data,
                        Ed25519.sign(loaded.getHostKey().getPrivate(), data)));
    }

    /** Proofs differ when any field moves between fields, so they can't be spliced. */
    @Test
    void testProofFraming() {
        final byte[] a = {1, 2};
        final byte[] b = {3};
        final byte[] nonce = new byte[32];
        assertFalse(
                Arrays.equals(
                        SignInProof.host(a, b, nonce, nonce),
                        SignInProof.host(new byte[] {1}, new byte[] {2, 3}, nonce, nonce)));
        assertFalse(
                Arrays.equals(
                        SignInProof.host(a, b, nonce, nonce),
                        SignInProof.signIn(a, b, nonce, nonce, "", new byte[0])));
    }

    /**
     * The first key seen for a server is pinned; the same key matches and another is a change.
     *
     * @param folder A scratch folder.
     */
    @Test
    void testKnownHosts(@TempDir Path folder) {
        final KnownHosts hosts = new KnownHosts(folder.resolve(KnownHosts.FILE));
        final byte[] server = new byte[32];
        final byte[] key = Ed25519.generate().getPublic().getEncoded();
        assertEquals(KnownHosts.Result.NEW, hosts.check(server, key));
        assertEquals(KnownHosts.Result.MATCH, hosts.check(server, key));
        final KnownHosts reread = new KnownHosts(folder.resolve(KnownHosts.FILE));
        assertEquals(KnownHosts.Result.MATCH, reread.check(server, key));
        assertEquals(
                KnownHosts.Result.CHANGED,
                reread.check(server, Ed25519.generate().getPublic().getEncoded()));
    }

    /**
     * Accounts register, refuse taken names and keys, change, and are read back.
     *
     * @param folder A scratch save folder.
     */
    @Test
    void testAccounts(@TempDir Path folder) {
        final Accounts accounts = Accounts.load(folder);
        assertFalse(accounts.hasOwner());
        final byte[] key = Ed25519.generate().getPublic().getEncoded();
        final Accounts.Account owner =
                accounts.register("Owner_1", key, Accounts.Role.OWNER, false, CLOCK);
        assertTrue(accounts.hasOwner());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        accounts.register(
                                "owner_1", new byte[] {1}, Accounts.Role.PLAYER, false, CLOCK));
        assertThrows(
                IllegalArgumentException.class,
                () -> accounts.register("Someone", key, Accounts.Role.PLAYER, false, CLOCK));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        accounts.register(
                                "no spaces", new byte[] {2}, Accounts.Role.PLAYER, false, CLOCK));
        final Accounts.Account friend =
                accounts.register("Friend", new byte[] {3}, Accounts.Role.PLAYER, true, CLOCK);
        accounts.update(friend.withPending(false).withRole(Accounts.Role.OPERATOR));

        final Accounts loaded = Accounts.load(folder);
        assertEquals(owner, loaded.byKey(key).orElseThrow());
        final Accounts.Account reread = loaded.byName("FRIEND").orElseThrow();
        assertEquals(Accounts.Role.OPERATOR, reread.role());
        assertFalse(reread.pending());
        assertEquals(1234, reread.registered());
        assertTrue(loaded.remove(reread.id()));
        assertTrue(Accounts.load(folder).byId(reread.id()).isEmpty());
    }

    /**
     * A player's file is read back as written; a missing one is empty.
     *
     * @param folder A scratch save folder.
     */
    @Test
    void testPlayerFiles(@TempDir Path folder) throws Exception {
        final PlayerFiles.PlayerData data =
                new PlayerFiles.PlayerData(
                        "0f8fad5b-d9cb-469f-a165-70867728950e",
                        "overworld",
                        1.5,
                        -2.25,
                        3,
                        90,
                        -10,
                        "creative");
        assertTrue(PlayerFiles.read(folder, data.accountId()).isEmpty());
        PlayerFiles.write(folder, data);
        assertEquals(data, PlayerFiles.read(folder, data.accountId()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> PlayerFiles.fileFor(folder, "../x"));
        assertNotEquals(PlayerFiles.fileFor(folder, "a"), PlayerFiles.fileFor(folder, "b"));
    }
}
