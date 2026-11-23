package com.ikalagaming.graphics.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.enums.Key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Tests for key bindings and the {@link Controls} registry. */
class TestControls {

    private static final String OWNER = "Test#1";
    private static final InputAction JUMP =
            new InputAction("test:jump", "Movement", "Jump", KeyBinding.of(Key.SPACE));
    private static final InputAction DEBUG =
            new InputAction("test:debug", "Interface", "Debug", KeyBinding.of(Key.F3));
    private static final InputAction CHUNKS =
            new InputAction(
                    "test:chunks", "Interface", "Chunk borders", KeyBinding.parse("ctrl+f3"));

    /**
     * Keys held.
     *
     * @param keys The keys.
     * @return A state with those held.
     */
    private static KeyState holding(Key... keys) {
        final Set<Key> held =
                keys.length == 0 ? EnumSet.noneOf(Key.class) : EnumSet.of(keys[0], keys);
        return held::contains;
    }

    /** Bindings read back from their text, in any case, and refuse junk. */
    @Test
    void testParse() {
        for (String text :
                List.of("w", "f3", "mouse_right", "ctrl+shift+f3", "alt+left_ctrl", "none")) {
            assertEquals(text, KeyBinding.parse(text).toString());
        }
        assertEquals(new KeyBinding(Key.F3, true, false, false), KeyBinding.parse(" Ctrl + F3 "));
        assertEquals(
                "Ctrl+Shift+Page Down", KeyBinding.parse("ctrl+shift+page_down").displayName());
        assertThrows(IllegalArgumentException.class, () -> KeyBinding.parse("ctrl+ctrl+a"));
        assertThrows(IllegalArgumentException.class, () -> KeyBinding.parse("hyper+a"));
        assertThrows(IllegalArgumentException.class, () -> KeyBinding.parse("nokey"));
        assertThrows(IllegalArgumentException.class, () -> KeyBinding.parse("mod_ctrl"));
        assertThrows(IllegalArgumentException.class, () -> KeyBinding.parse("gamepad_start"));
    }

    /** Actions are held, pressed once and released once. */
    @Test
    void testHeldAndPressed() {
        final Controls controls = new Controls();
        controls.register(OWNER, JUMP);
        controls.update(holding(Key.SPACE));
        assertTrue(controls.isDown(JUMP.id()));
        assertTrue(controls.wasPressed(JUMP.id()));
        controls.update(holding(Key.SPACE));
        assertTrue(controls.isDown(JUMP.id()));
        assertFalse(controls.wasPressed(JUMP.id()));
        controls.update(holding());
        assertFalse(controls.isDown(JUMP.id()));
        assertTrue(controls.wasReleased(JUMP.id()));
        assertFalse(controls.isDown("test:unknown"));
    }

    /** A binding with no modifiers ignores held modifiers; with more modifiers, a binding wins. */
    @Test
    void testModifiers() {
        final Controls controls = new Controls();
        controls.register(OWNER, JUMP);
        controls.register(OWNER, DEBUG);
        controls.register(OWNER, CHUNKS);
        controls.update(holding(Key.SPACE, Key.MOD_CTRL));
        assertTrue(controls.isDown(JUMP.id()));
        controls.update(holding(Key.F3, Key.MOD_CTRL));
        assertTrue(controls.wasPressed(CHUNKS.id()));
        assertFalse(controls.isDown(DEBUG.id()));
        controls.update(holding());
        controls.update(holding(Key.F3));
        assertTrue(controls.wasPressed(DEBUG.id()));
        assertFalse(controls.isDown(CHUNKS.id()));
    }

    /**
     * A key still held when a text field gives the keyboard back, like the Enter that sent a chat
     * line, isn't a new press; pressing it again is.
     */
    @Test
    void testHeldAcrossKeyboardCapture() {
        final InputAction chat =
                new InputAction("test:chat", "Interface", "Chat", KeyBinding.of(Key.ENTER));
        final Controls controls = new Controls();
        controls.register(OWNER, chat);
        final KeyState typing =
                new KeyState() {
                    @Override
                    public boolean isDown(Key key) {
                        return false;
                    }

                    @Override
                    public boolean keyboardBlocked() {
                        return true;
                    }
                };
        controls.update(typing);
        controls.update(holding(Key.ENTER));
        assertTrue(controls.isDown(chat.id()));
        assertFalse(controls.wasPressed(chat.id()));
        controls.update(holding());
        controls.update(holding(Key.ENTER));
        assertTrue(controls.wasPressed(chat.id()));
    }

    /** Suspended controls read as up. */
    @Test
    void testSuspended() {
        final Controls controls = new Controls();
        controls.register(OWNER, JUMP);
        controls.setSuspended(true);
        controls.update(holding(Key.SPACE));
        assertFalse(controls.isDown(JUMP.id()));
        controls.setSuspended(false);
        controls.update(holding(Key.SPACE));
        assertTrue(controls.wasPressed(JUMP.id()));
    }

    /** Rebinding, conflicts, resets and ownership. */
    @Test
    void testRebind() {
        final Controls controls = new Controls();
        controls.register(OWNER, JUMP);
        controls.register(OWNER, DEBUG);
        controls.rebind(DEBUG.id(), KeyBinding.of(Key.SPACE));
        assertTrue(controls.isChanged(DEBUG.id()));
        assertEquals(List.of(JUMP.id()), controls.conflicts(DEBUG.id()));
        controls.rebind(DEBUG.id(), KeyBinding.of(Key.F3));
        assertFalse(controls.isChanged(DEBUG.id()));
        controls.rebind(JUMP.id(), KeyBinding.NONE);
        controls.update(holding(Key.SPACE));
        assertFalse(controls.isDown(JUMP.id()));
        controls.resetAll();
        assertEquals(KeyBinding.of(Key.SPACE), controls.binding(JUMP.id()));
        assertThrows(IllegalArgumentException.class, () -> controls.register("Other#2", JUMP));
        assertEquals(2, controls.removeAllOwnedBy(OWNER));
        assertTrue(controls.actions().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new InputAction("Jump", "Movement", "Jump", KeyBinding.NONE));
    }

    /** Actions are listed by category in the order categories first appear. */
    @Test
    void testOrder() {
        final Controls controls = new Controls();
        controls.register(OWNER, DEBUG);
        controls.register(OWNER, JUMP);
        controls.register(OWNER, CHUNKS);
        assertEquals(List.of(DEBUG, CHUNKS, JUMP), controls.actions());
    }

    /**
     * Bindings that differ from the defaults are saved and read back, including ones for actions
     * nobody registered.
     *
     * @param folder A scratch folder.
     */
    @Test
    void testSaveAndLoad(@TempDir Path folder) throws Exception {
        final Path file = folder.resolve("settings").resolve("controls.yml");
        final Controls controls = new Controls();
        controls.open(file);
        controls.register(OWNER, JUMP);
        controls.save();
        assertFalse(Files.exists(file), "nothing to save");
        controls.rebind(JUMP.id(), KeyBinding.parse("ctrl+j"));
        controls.rebind("mod:jetpack", KeyBinding.of(Key.J));

        final Controls loaded = new Controls();
        loaded.open(file);
        assertEquals(KeyBinding.parse("ctrl+j"), loaded.binding(JUMP.id()));
        assertEquals(KeyBinding.of(Key.J), loaded.binding("mod:jetpack"));

        Files.writeString(file, "bindings:\n  test:jump: nokey\n  test:debug: f4\n");
        final Controls broken = new Controls();
        broken.open(file);
        broken.register(OWNER, JUMP);
        assertEquals(KeyBinding.of(Key.SPACE), broken.binding(JUMP.id()));
        assertEquals(KeyBinding.of(Key.F4), broken.binding(DEBUG.id()));
    }
}
