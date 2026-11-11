package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.pose.WingMotion;

import com.google.gson.JsonObject;
import lombok.NonNull;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link WingPoser}: the handles' turns become the angles the wing moves with, and saved
 * poses land in the part's definition.
 *
 * @author Ches Burks
 */
class TestWingPoser {
    /** How close quaternion dot products must be to 1. */
    private static final float CLOSE = 1e-5f;

    /** Fails on any problem. */
    private static final Report FAIL =
            new Report() {
                @Override
                public void error(@NonNull String path, @NonNull String message) {
                    Assertions.fail(path + ": " + message);
                }

                @Override
                public void warning(@NonNull String path, @NonNull String message) {
                    Assertions.fail(path + ": " + message);
                }
            };

    /** A folder for the files written. */
    @TempDir Path temp;

    /**
     * Assert two turns are the same.
     *
     * @param expected The turn expected.
     * @param actual The turn.
     */
    private static void assertSameTurn(Quaternionf expected, Quaternionf actual) {
        Assertions.assertEquals(
                1,
                Math.abs(expected.dot(actual)),
                CLOSE,
                "Expected " + expected + " but was " + actual);
    }

    /**
     * A turn of the handles goes on top of the bone's turn, in model space, on either side: the
     * wing moves with the angles as it showed while dragged.
     */
    @Test
    void testTurnedComposes() {
        Vec3 start = new Vec3(-10, 26, -79);
        Quaternionf by = new Quaternionf().rotationAxis(0.4f, 0.3f, 0.8f, -0.5f);
        for (boolean mirrored : new boolean[] {false, true}) {
            Quaternionf before = WingMotion.turn(List.of(start), 0, mirrored, new Quaternionf());
            Vec3 turned = WingPoser.turned(start, mirrored, by);
            Quaternionf after = WingMotion.turn(List.of(turned), 0, mirrored, new Quaternionf());
            assertSameTurn(new Quaternionf(by).mul(before), after);
        }
    }

    /** Angles read back from a turn give that turn, for a right wing too. */
    @Test
    void testAnglesRoundTrip() {
        Vec3 start = new Vec3(20, -35, 60);
        for (boolean mirrored : new boolean[] {false, true}) {
            Quaternionf turn = WingMotion.turn(List.of(start), 0, mirrored, new Quaternionf());
            Vec3 back = WingMotion.angles(turn, mirrored);
            Assertions.assertEquals(start.x(), back.x(), 1e-3f);
            Assertions.assertEquals(start.y(), back.y(), 1e-3f);
            Assertions.assertEquals(start.z(), back.z(), 1e-3f);
        }
    }

    /** Saving writes the poses as the part's own wing field, keeping the file's top comments. */
    @Test
    void testWriteWing() throws IOException {
        Path file = temp.resolve("wings.json");
        Files.writeString(
                file,
                """
                // wings, batch one
                [
                  {"id": "gen:part/wing/bat_wing_l", "plan": "ikala:biped", "kind": "accessory",
                   "extension": "wing.L", "pair": "gen:part/wing/bat_wing_r"}
                ]
                """,
                StandardCharsets.UTF_8);
        Id id = Id.parse("gen:part/wing/bat_wing_l");
        BodyPlan.WingPoses poses =
                new BodyPlan.WingPoses(
                        List.of(new Vec3(-5, 30, -70), new Vec3(0, 0, 12)),
                        List.of(new Vec3(0, 0, 10)),
                        List.of(35f, 12f),
                        2,
                        0.08f,
                        4,
                        0.4f,
                        new Vec3(0.04f, 0.07f, -0.03f));
        AccessoryAligner.writeDefinition(
                file, id, definition -> definition.add(Part.FIELD_WING, DataFormat.write(poses)));

        String text = Files.readString(file, StandardCharsets.UTF_8);
        Assertions.assertTrue(text.startsWith("// wings, batch one\n"));
        JsonObject definition = AccessoryAligner.definitionIn(JsonText.parse(text, e -> {}), id);
        Assertions.assertNotNull(definition);
        BodyPlan.WingPoses back =
                DataFormat.readDefinition(
                        definition.get(Part.FIELD_WING), BodyPlan.WingPoses.class, FAIL);
        Assertions.assertEquals(poses, back);
    }
}
