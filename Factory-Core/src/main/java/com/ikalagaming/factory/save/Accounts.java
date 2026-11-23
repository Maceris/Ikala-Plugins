package com.ikalagaming.factory.save;

import com.ikalagaming.factory.kvt.Node;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * A save's accounts, in {@value #FILE}: who can sign in, with which key, and their role. The
 * account id, not the username, names a player's file, so a username can change later. See the Save
 * File Format and Accounts and Identity design docs.
 */
@Slf4j
public final class Accounts {

    /** The file in a save. */
    public static final String FILE = "accounts.kvt";

    /** The format version written. */
    public static final int FORMAT_VERSION = 1;

    /** What usernames look like. */
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final String KEY_VERSION = "version";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PUBLIC_KEY = "publicKey";
    private static final String KEY_ROLE = "role";
    private static final String KEY_BANNED = "banned";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_REGISTERED = "registered";

    /** What an account can do, as a starting point for its permissions. */
    public enum Role {
        /** The save's owner: everything, and can't be taken away by operators. */
        OWNER,
        /** An operator: runs the server. */
        OPERATOR,
        /** Everyone else. */
        PLAYER;

        /**
         * The role's name in files and commands.
         *
         * @return The name, in lower case.
         */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The role with a name.
         *
         * @param name The name.
         * @return The role, or PLAYER if the name isn't one.
         */
        public static Role named(String name) {
            for (Role role : values()) {
                if (role.id().equalsIgnoreCase(name == null ? "" : name.strip())) {
                    return role;
                }
            }
            return PLAYER;
        }

        /**
         * Whether this role runs the server.
         *
         * @return True for owners and operators.
         */
        public boolean isOperator() {
            return this != PLAYER;
        }
    }

    /**
     * One account.
     *
     * @param id A random id, which names the player's file.
     * @param username The name they sign in and show as.
     * @param publicKey The key they sign in with, encoded.
     * @param role Their role.
     * @param banned Whether they are banned.
     * @param pending Whether they wait for an operator to accept them.
     * @param registered When they registered, in milliseconds since the epoch.
     */
    public record Account(
            @NonNull String id,
            @NonNull String username,
            byte @NonNull [] publicKey,
            @NonNull Role role,
            boolean banned,
            boolean pending,
            long registered) {

        /**
         * This account with another role.
         *
         * @param newRole The role.
         * @return The account.
         */
        public Account withRole(@NonNull Role newRole) {
            return new Account(id, username, publicKey, newRole, banned, pending, registered);
        }

        /**
         * This account accepted or waiting.
         *
         * @param isPending Whether it waits.
         * @return The account.
         */
        public Account withPending(boolean isPending) {
            return new Account(id, username, publicKey, role, banned, isPending, registered);
        }

        /**
         * This account banned or not.
         *
         * @param isBanned Whether it is banned.
         * @return The account.
         */
        public Account withBanned(boolean isBanned) {
            return new Account(id, username, publicKey, role, isBanned, pending, registered);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Account account
                    && id.equals(account.id)
                    && username.equals(account.username)
                    && Arrays.equals(publicKey, account.publicKey)
                    && role == account.role
                    && banned == account.banned
                    && pending == account.pending
                    && registered == account.registered;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return username + " (" + id + ", " + role.id() + (pending ? ", pending" : "") + ")";
        }
    }

    /** Guards the accounts and the file. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The accounts, by id, in registration order. */
    private final Map<String, Account> byId = new LinkedHashMap<>();

    /** The file, or null to keep them in memory only. */
    private final Path file;

    /**
     * Accounts kept in a file.
     *
     * @param file The file, or null for memory only.
     */
    private Accounts(Path file) {
        this.file = file;
    }

    /**
     * Accounts in memory only, for tests.
     *
     * @return Empty accounts.
     */
    public static Accounts inMemory() {
        return new Accounts(null);
    }

    /**
     * Read a save's accounts.
     *
     * @param saveFolder The save.
     * @return The accounts; none if the file is missing.
     */
    public static Accounts load(@NonNull Path saveFolder) {
        final Accounts accounts = new Accounts(saveFolder.resolve(FILE));
        final Optional<Node> root = Saves.readNode(accounts.file);
        if (root.isEmpty() || !root.get().hasChild(KEY_ACCOUNTS)) {
            return accounts;
        }
        final Node list = root.get().get(KEY_ACCOUNTS);
        for (String id : list.getKeys()) {
            try {
                final Node node = list.get(id);
                accounts.byId.put(
                        id,
                        new Account(
                                id,
                                node.get(KEY_USERNAME),
                                Base64.getDecoder().decode((String) node.get(KEY_PUBLIC_KEY)),
                                Role.named(node.get(KEY_ROLE)),
                                Boolean.TRUE.equals(node.get(KEY_BANNED)),
                                Boolean.TRUE.equals(node.get(KEY_PENDING)),
                                ((Number) node.get(KEY_REGISTERED)).longValue()));
            } catch (RuntimeException e) {
                log.warn("Skipping the damaged account {} in {}", id, accounts.file, e);
            }
        }
        return accounts;
    }

    /**
     * Whether a username is allowed: 3 to 16 letters, digits and underscores.
     *
     * @param username The name.
     * @return True if allowed.
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /**
     * The account that signs in with a key.
     *
     * @param publicKey The encoded key.
     * @return The account, if any.
     */
    public Optional<Account> byKey(byte @NonNull [] publicKey) {
        lock.lock();
        try {
            return byId.values().stream()
                    .filter(account -> Arrays.equals(account.publicKey(), publicKey))
                    .findFirst();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The account with a username, in any case.
     *
     * @param username The name.
     * @return The account, if any.
     */
    public Optional<Account> byName(@NonNull String username) {
        lock.lock();
        try {
            return byId.values().stream()
                    .filter(account -> account.username().equalsIgnoreCase(username))
                    .findFirst();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The account with an id.
     *
     * @param id The id.
     * @return The account, if any.
     */
    public Optional<Account> byId(@NonNull String id) {
        lock.lock();
        try {
            return Optional.ofNullable(byId.get(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Every account, in registration order.
     *
     * @return A copy.
     */
    public List<Account> all() {
        lock.lock();
        try {
            return new ArrayList<>(byId.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the save has an owner yet.
     *
     * @return True if some account is the owner.
     */
    public boolean hasOwner() {
        lock.lock();
        try {
            return byId.values().stream().anyMatch(account -> account.role() == Role.OWNER);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Register an account, and save.
     *
     * @param username The name.
     * @param publicKey The key they sign in with.
     * @param role Their role.
     * @param pending Whether they wait for an operator.
     * @param clock Gives the time they registered.
     * @return The account.
     * @throws IllegalArgumentException If the name isn't allowed or is taken, or the key has an
     *     account.
     */
    public Account register(
            @NonNull String username,
            byte @NonNull [] publicKey,
            @NonNull Role role,
            boolean pending,
            @NonNull Clock clock) {
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException("Usernames are 3 to 16 letters, digits or _");
        }
        lock.lock();
        try {
            if (byName(username).isPresent()) {
                throw new IllegalArgumentException("The name " + username + " is taken");
            }
            if (byKey(publicKey).isPresent()) {
                throw new IllegalArgumentException("That key already has an account");
            }
            final Account account =
                    new Account(
                            UUID.randomUUID().toString(),
                            username,
                            publicKey.clone(),
                            role,
                            false,
                            pending,
                            clock.millis());
            byId.put(account.id(), account);
            save();
            return account;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replace an account with a changed copy, and save.
     *
     * @param account The account, with an id that exists.
     * @throws IllegalArgumentException If there's no account with that id.
     */
    public void update(@NonNull Account account) {
        lock.lock();
        try {
            if (!byId.containsKey(account.id())) {
                throw new IllegalArgumentException("No account " + account.id());
            }
            byId.put(account.id(), account);
            save();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove an account, as when an operator turns down a pending one, and save.
     *
     * @param id The account's id.
     * @return True if there was one.
     */
    public boolean remove(@NonNull String id) {
        lock.lock();
        try {
            final boolean removed = byId.remove(id) != null;
            if (removed) {
                save();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /** Write the file, if there is one. */
    private void save() {
        if (file == null) {
            return;
        }
        final Node root = new Node();
        root.add(KEY_VERSION, FORMAT_VERSION);
        final Node list = new Node();
        for (Account account : byId.values()) {
            final Node node = new Node();
            node.add(KEY_USERNAME, account.username());
            node.add(KEY_PUBLIC_KEY, Base64.getEncoder().encodeToString(account.publicKey()));
            node.add(KEY_ROLE, account.role().id());
            node.add(KEY_BANNED, account.banned());
            node.add(KEY_PENDING, account.pending());
            node.add(KEY_REGISTERED, account.registered());
            list.add(account.id(), node);
        }
        root.add(KEY_ACCOUNTS, list);
        try {
            Saves.write(root, file);
        } catch (IOException e) {
            log.warn("Could not save the accounts to {}", file, e);
        }
    }
}
