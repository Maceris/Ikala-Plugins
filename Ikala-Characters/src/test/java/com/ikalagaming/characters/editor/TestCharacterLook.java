package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.random.SeededRandom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link CharacterLook}.
 *
 * @author Ches Burks
 */
class TestCharacterLook {
    /** The height slider's ID. */
    private static final Id HEIGHT = Id.parse("test:slider/height");

    /**
     * A look with a fixed body, a head with two choices, and a helmet that can be empty, plus a
     * height slider.
     *
     * @return The look.
     */
    private static CharacterLook look() {
        PartSlots slots =
                new PartSlots(
                        List.of(
                                new PartSlots.Slot(
                                        "body", List.of(List.of(0)), List.of("body"), false, 0),
                                new PartSlots.Slot(
                                        "head",
                                        List.of(List.of(1), List.of(2, 3)),
                                        List.of("head 1", "head 2"),
                                        false,
                                        0),
                                new PartSlots.Slot(
                                        "helmet",
                                        List.of(List.of(4), List.of(5)),
                                        List.of("light", "heavy"),
                                        true,
                                        -1)));
        Slider height =
                new Slider(
                        HEIGHT,
                        "body",
                        "Height",
                        false,
                        List.of(),
                        Map.of(Slider.CONTEXT_PLAYER, new Range(-1, 1)));
        BodyPlan.Variant tall =
                new BodyPlan.Variant(
                        "tall",
                        List.of(),
                        Map.of(
                                HEIGHT.toString(),
                                new BodyPlan.SliderSetting(0.5f, new Range(0, 1))),
                        Map.of());
        BodyPlan.Variant plain = new BodyPlan.Variant("plain", List.of(), Map.of(), Map.of());
        return new CharacterLook(slots, List.of(height), List.of(plain, tall));
    }

    /** Choosing and stepping change what is worn, and each change can be undone. */
    @Test
    void testChooseAndUndo() {
        CharacterLook look = look();
        Assertions.assertEquals(List.of(0, 1), look.worn(CharacterLook.NO_PREVIEW, 0));
        Assertions.assertTrue(look.isFixed(0));
        Assertions.assertFalse(look.canUndo());

        look.choose(1, 1);
        // Stepping back from the first helmet choice goes through empty to the last
        look.step(2, 1);
        Assertions.assertEquals(List.of(0, 2, 3, 4), look.worn(CharacterLook.NO_PREVIEW, 0));
        look.step(2, -1);
        look.step(2, -1);
        Assertions.assertEquals(1, look.choice(2));

        // A preview shows another choice without choosing it
        Assertions.assertEquals(List.of(0, 1, 5), look.worn(1, 0));
        Assertions.assertEquals(1, look.choice(1));

        Assertions.assertTrue(look.undo());
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(2));
        Assertions.assertTrue(look.undo());
        Assertions.assertTrue(look.undo());
        Assertions.assertTrue(look.undo());
        Assertions.assertEquals(List.of(0, 1), look.worn(CharacterLook.NO_PREVIEW, 0));
        Assertions.assertFalse(look.undo());
    }

    /** A variant starts its sliders and narrows their ranges, and choosing one can be undone. */
    @Test
    void testVariants() {
        CharacterLook look = look();
        Assertions.assertEquals(0, look.variant());
        Assertions.assertEquals(0, look.values().get(HEIGHT)[0]);

        look.chooseVariant(1);
        Assertions.assertEquals(0.5f, look.values().get(HEIGHT)[0]);
        Slider height = look.sliders().get(0);
        Assertions.assertEquals(new Range(0, 1), look.range(height, Slider.CONTEXT_PLAYER));

        // Randomizing stays in the variant's range
        look.randomize(new SeededRandom(3), Slider.CONTEXT_PLAYER);
        Assertions.assertTrue(look.values().get(HEIGHT)[0] >= 0);
        look.resetSliders();
        Assertions.assertEquals(0.5f, look.values().get(HEIGHT)[0]);

        look.undo();
        look.undo();
        look.undo();
        Assertions.assertEquals(0, look.variant());
        Assertions.assertEquals(0, look.values().get(HEIGHT)[0]);
    }

    /** Channels come from the slots, and colors can be set, dragged, undone and reset. */
    @Test
    void testColors() {
        CharacterLook look = look();
        // The fixed body and head take skin, the helmet the primary color
        Assertions.assertEquals(List.of(Palette.SKIN, Palette.PRIMARY), look.channels());
        Assertions.assertNull(look.color(Palette.SKIN));

        look.setColor(Palette.SKIN, ColorCode.of(3, 6, 40));
        look.push(look.snapshot());
        look.dragColor(Palette.PRIMARY, ColorCode.of(40, 15, 63));
        look.dragColor(Palette.PRIMARY, ColorCode.of(41, 15, 63));
        Assertions.assertEquals(ColorCode.of(41, 15, 63), look.color(Palette.PRIMARY));

        // The drag undoes in one step
        look.undo();
        Assertions.assertNull(look.color(Palette.PRIMARY));
        Assertions.assertEquals(ColorCode.of(3, 6, 40), look.color(Palette.SKIN));

        look.randomize(new SeededRandom(5), Slider.CONTEXT_PLAYER);
        Assertions.assertNotNull(look.color(Palette.PRIMARY));
        look.resetColors();
        Assertions.assertNull(look.color(Palette.SKIN));
        look.undo();
        Assertions.assertNotNull(look.color(Palette.SKIN));
    }

    /** Clearing empties what can be empty and puts the rest back; randomizing stays in range. */
    @Test
    void testClearAndRandomize() {
        CharacterLook look = look();
        look.randomize(new SeededRandom(7), Slider.CONTEXT_PLAYER);
        float height = look.values().get(HEIGHT)[0];
        Assertions.assertTrue(Math.abs(height) <= CharacterLook.RANDOM_SPREAD);
        Assertions.assertNotEquals(0, height);

        look.choose(1, 1);
        look.choose(2, 0);
        look.clear();
        Assertions.assertEquals(0, look.choice(1));
        Assertions.assertEquals(CharacterLook.EMPTY, look.choice(2));
        // Clearing leaves the sliders
        Assertions.assertEquals(height, look.values().get(HEIGHT)[0]);

        look.resetSliders();
        Assertions.assertEquals(0, look.values().get(HEIGHT)[0]);
        look.undo();
        Assertions.assertEquals(height, look.values().get(HEIGHT)[0]);
    }
}
