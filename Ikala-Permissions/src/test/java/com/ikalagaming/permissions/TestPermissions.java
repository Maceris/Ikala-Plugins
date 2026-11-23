package com.ikalagaming.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Tests for declaring nodes, groups, and working out what someone may do. */
class TestPermissions {

    private static final String OWNER = "Test";

    /** Nodes in Bukkit's format, with an inline child. */
    private static final String DECLARED =
            """
            game.command.gamemode:
              description: Change your game mode
              default: op
              children:
                game.command.gamemode.creative: true
                game.command.gamemode.survival: true
            game.command.gamemode.creative:
              default: op
            game.command.gamemode.survival:
              default: op
            game.chat:
              default: true
            game.newcomer:
              default: not op
            game.admin:
              default: op
              children:
                game.command.kick:
                  description: Kick a player
                  default: false
            """;

    /** The default groups a game might use. */
    private static final Group[] DEFAULTS = {
        Group.inheriting("default"),
        Group.inheriting("operator", "default"),
        Group.inheriting("owner", "operator")
    };

    private static PermissionRegistry registry() {
        final PermissionRegistry registry = new PermissionRegistry();
        final Map<?, ?> map = new Yaml().load(DECLARED);
        registry.registerAll(OWNER, map, PermissionDefault.OP);
        return registry;
    }

    private static Subject player(String id) {
        return new Subject(id, false, List.of("default"));
    }

    private static Subject operator(String id) {
        return new Subject(id, true, List.of("operator"));
    }

    /** Defaults follow op, children follow parents, and inline children are declared. */
    @Test
    void testDefaults() {
        final PermissionRegistry registry = registry();
        assertEquals(7, registry.all().size());
        assertEquals(
                "Kick a player", registry.get("game.command.kick").orElseThrow().description());
        final Permissions permissions =
                new Permissions(registry, PermissionStore.inMemory(DEFAULTS));
        final Subject player = player("p");
        final Subject op = operator("o");
        assertTrue(permissions.has(player, "game.chat"));
        assertFalse(permissions.has(player, "game.command.gamemode"));
        assertTrue(permissions.has(player, "game.newcomer"));
        assertTrue(permissions.has(op, "game.command.gamemode.creative"));
        assertFalse(permissions.has(op, "game.newcomer"));
        // game.admin is op and its child follows it, over the child's own default
        assertTrue(permissions.has(op, "game.command.kick"));
        assertFalse(permissions.has(player, "game.unknown"));
        assertTrue(permissions.has(Subject.CONSOLE, "game.command.gamemode"));
    }

    /** Groups inherit, wildcards apply before exact nodes, and players' own settings win. */
    @Test
    void testGroupsAndWildcards() {
        final PermissionStore store = PermissionStore.inMemory(DEFAULTS);
        store.putGroup(
                new Group(
                        "builder",
                        List.of("default"),
                        Map.of("game.command.*", true, "game.command.kick", false)));
        store.putPlayer("b", new PermissionStore.PlayerEntry("Bob", List.of("builder"), Map.of()));
        store.putPlayer(
                "m",
                new PermissionStore.PlayerEntry(
                        "Muted", List.of(), Map.of("game.chat", false, "Game.Extra.Thing", true)));
        store.putGroup(new Group("default", List.of(), Map.of("*", false, "game.chat", true)));
        final Permissions permissions = new Permissions(registry(), store);

        final Subject bob = player("b");
        assertTrue(permissions.has(bob, "game.command.gamemode.survival"));
        assertTrue(permissions.has(bob, "game.command.undeclared.thing"));
        assertFalse(permissions.has(bob, "game.command.kick"));
        assertTrue(permissions.has(bob, "game.chat"));
        assertFalse(permissions.has(bob, "game.newcomer"));

        final Subject muted = player("m");
        assertFalse(permissions.has(muted, "game.chat"));
        assertTrue(permissions.has(muted, "game.extra.thing"));

        // A group that inherits itself doesn't loop
        store.putGroup(Group.inheriting("loop", "loop", "default"));
        assertEquals(List.of("default", "loop"), permissions.linearize(List.of("loop")));
    }

    /** A child set to false follows its parent the opposite way. */
    @Test
    void testNegatedChild() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register(
                OWNER,
                new Permission(
                        "game.peaceful",
                        "No fighting",
                        PermissionDefault.FALSE,
                        Map.of("game.fight", false)));
        registry.register(OWNER, new Permission("game.fight", "", PermissionDefault.TRUE));
        final PermissionStore store = PermissionStore.inMemory(DEFAULTS);
        store.putPlayer(
                "p", new PermissionStore.PlayerEntry("", List.of(), Map.of("game.peaceful", true)));
        final Permissions permissions = new Permissions(registry, store);
        assertFalse(permissions.has(player("p"), "game.fight"));
        assertTrue(permissions.has(player("q"), "game.fight"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        registry.register(
                                "Other", new Permission("game.fight", "", PermissionDefault.TRUE)));
        assertEquals(2, registry.removeAllOwnedBy(OWNER));
    }

    /**
     * The store is written and read back, and missing default groups are added.
     *
     * @param folder A scratch folder.
     */
    @Test
    void testStoreFile(@TempDir Path folder) throws Exception {
        final Path file = folder.resolve("permissions.yml");
        final PermissionStore store = PermissionStore.load(file, DEFAULTS);
        assertTrue(Files.exists(file));
        store.putPlayer(
                "id-1",
                new PermissionStore.PlayerEntry(
                        "Somebody", List.of("builder"), Map.of("game.chat", false)));
        store.putGroup(new Group("builder", List.of("default"), Map.of("game.command.*", true)));
        final PermissionStore loaded = PermissionStore.load(file, DEFAULTS);
        assertEquals(store.player("id-1"), loaded.player("id-1"));
        assertEquals(store.group("builder"), loaded.group("builder"));
        assertTrue(loaded.group("owner").isPresent());

        Files.writeString(file, "groups: [broken\n");
        final PermissionStore broken = PermissionStore.load(file, DEFAULTS);
        assertTrue(broken.group("default").isPresent());
    }
}
