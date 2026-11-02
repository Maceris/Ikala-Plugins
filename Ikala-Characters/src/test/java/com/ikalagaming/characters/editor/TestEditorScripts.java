package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    /** Scripts reach the editor by this name, which capture scripts are written against. */
    @Test
    void globalName() {
        Assertions.assertEquals("characters", EditorScripts.GLOBAL);
    }
}
