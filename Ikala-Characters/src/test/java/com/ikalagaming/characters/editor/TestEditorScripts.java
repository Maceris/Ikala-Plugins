package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests for what scripts can do with the character editor: finding the slot that offers a part, and
 * calls failing while the editor isn't loaded.
 *
 * @author Ches Burks
 */
class TestEditorScripts {
    /** Two slots: arms offering two parts, legs one. */
    private static final List<List<Id>> OPTIONS =
            List.of(
                    List.of(Id.parse("gen:part/body/mech_arm_l"), Id.parse("gen:part/body/bone_l")),
                    List.of(Id.parse("gen:part/body/robot_leg_l")));

    /** The plugin name the poller tests own their thread as. */
    private static final String POLLER_OWNER = "TestEditorScripts";

    /** A part is found by its full ID, in its slot and option. */
    @Test
    void findsSlotAndOption() {
        Assertions.assertArrayEquals(
                new int[] {0, 1}, CharacterEditor.findOption(OPTIONS, "gen:part/body/bone_l"));
        Assertions.assertArrayEquals(
                new int[] {1, 0}, CharacterEditor.findOption(OPTIONS, "gen:part/body/robot_leg_l"));
    }

    /** A part no slot offers, or an ID without its namespace, isn't found. */
    @Test
    void missingPartIsNotFound() {
        Assertions.assertNull(CharacterEditor.findOption(OPTIONS, "gen:part/body/robot_leg_r"));
        Assertions.assertNull(CharacterEditor.findOption(OPTIONS, "part/body/bone_l"));
    }

    /** Calls fail plainly, rather than hang, while the plugin isn't enabled. */
    @Test
    void callsFailWithoutEditor() {
        EditorScripts.attach(null, null);
        Assertions.assertThrows(IllegalStateException.class, EditorScripts::bare);
        Assertions.assertThrows(
                IllegalStateException.class, () -> EditorScripts.wear("gen:part/body/bone_l"));
    }

    /**
     * The settle poller runs only between starting and stopping it, starting twice doesn't make a
     * second one, and it can start again after it stopped, as when the plugin is enabled again.
     */
    @Test
    void pollerFollowsThePlugin() throws Exception {
        EditorScripts.stopPolling();
        Assertions.assertNull(EditorScripts.poller());
        EditorScripts.startPolling(POLLER_OWNER);
        ScheduledExecutorService first = EditorScripts.poller();
        EditorScripts.startPolling(POLLER_OWNER);
        try {
            Assertions.assertNotNull(first);
            Assertions.assertSame(first, EditorScripts.poller());
            // Its thread is named for the plugin, so a thread dump shows who owns it
            String name =
                    first.schedule(() -> Thread.currentThread().getName(), 0, TimeUnit.SECONDS)
                            .get(5, TimeUnit.SECONDS);
            Assertions.assertTrue(
                    name.startsWith(POLLER_OWNER + "/" + EditorScripts.POLLER_NAME), name);
        } finally {
            EditorScripts.stopPolling();
        }
        Assertions.assertNull(EditorScripts.poller());
        Assertions.assertTrue(first.isTerminated());
        // Stopping twice is harmless, and it starts again when the plugin is enabled again
        EditorScripts.stopPolling();
        EditorScripts.startPolling(POLLER_OWNER);
        try {
            Assertions.assertNotNull(EditorScripts.poller());
            Assertions.assertNotSame(first, EditorScripts.poller());
        } finally {
            EditorScripts.stopPolling();
        }
    }

    /** Settling isn't offered outside a script. */
    @Test
    void settleNeedsAScript() {
        Assertions.assertThrows(IllegalStateException.class, EditorScripts::settle);
    }

    /** Scripts reach the editor by this name, which capture scripts are written against. */
    @Test
    void globalName() {
        Assertions.assertEquals("characters", EditorScripts.GLOBAL);
    }
}
