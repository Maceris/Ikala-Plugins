package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for measuring how an accessory sits on a head, and for saving its placement.
 *
 * @author Ches Burks
 */
class TestAccessoryFit {
    /** How close floats must be. */
    private static final float EPSILON = 1e-5f;

    /** Where saved files go. */
    @TempDir Path temp;

    /**
     * A loaded part holding some points, with no triangles.
     *
     * @param points Three floats per point.
     * @return The part.
     */
    private static ImportedCharacter.Part points(float... points) {
        return part(points, new int[0]);
    }

    /**
     * A loaded part from positions and triangles.
     *
     * @param positions Three floats per vertex.
     * @param indices Three indices per triangle.
     * @return The part.
     */
    private static ImportedCharacter.Part part(float[] positions, int[] indices) {
        int count = positions.length / 3;
        float[] vertices = new float[count * 14];
        for (int v = 0; v < count; ++v) {
            System.arraycopy(positions, v * 3, vertices, v * 14, 3);
        }
        return new ImportedCharacter.Part(
                "part",
                vertices,
                new int[count * 4],
                new float[count * 4],
                indices,
                -1,
                new Vector3f(),
                new Vector3f());
    }

    /**
     * A flat square lid at a height, 0.2 m across, centered on x.
     *
     * @param height Its height.
     * @param x Its middle in x.
     * @return The lid.
     */
    private static ImportedCharacter.Part lid(float height, float x) {
        return part(
                new float[] {
                    x - 0.1f, height, -0.1f,
                    x + 0.1f, height, -0.1f,
                    x + 0.1f, height, 0.1f,
                    x - 0.1f, height, 0.1f
                },
                new int[] {0, 1, 2, 0, 2, 3});
    }

    /** A point under a lid is covered at the lid's height; one beside it isn't. */
    @Test
    void testDistanceUp() {
        AccessoryFit.Mesh lid = AccessoryFit.mesh(List.of(lid(2, 0)), null);
        Assertions.assertEquals(0.5f, AccessoryFit.distanceUp(lid, 0.05f, 1.5f, 0.05f), EPSILON);
        Assertions.assertTrue(Float.isNaN(AccessoryFit.distanceUp(lid, 0.3f, 1.5f, 0)));
        // Above the lid, nothing of it is straight up
        Assertions.assertTrue(Float.isNaN(AccessoryFit.distanceUp(lid, 0, 2.1f, 0)));
    }

    /** Head points under the lid count toward the gap; ones above it show through. */
    @Test
    void testSeat() {
        AccessoryFit.Mesh head =
                AccessoryFit.mesh(
                        List.of(points(0, 1.9f, 0, 0.05f, 1.95f, 0.05f, 0, 2.05f, 0)), null);
        AccessoryFit.Seat seat = AccessoryFit.seat(head, AccessoryFit.mesh(List.of(lid(2, 0)), null));
        Assertions.assertEquals(0.05f, seat.gap(), EPSILON);
        Assertions.assertEquals(1, seat.showingCount());
        Assertions.assertEquals(2.05f, seat.showing()[1], EPSILON);

        // Raised clear of the head, nothing shows
        AccessoryFit.Mesh raised =
                AccessoryFit.mesh(List.of(lid(2, 0)), new Matrix4f().translate(0, 0.1f, 0));
        AccessoryFit.Seat clear = AccessoryFit.seat(head, raised);
        Assertions.assertEquals(0, clear.showingCount());
        Assertions.assertEquals(0.05f, clear.gap(), EPSILON);
    }

    /** The middle side to side counts only what is above the height. */
    @Test
    void testMiddleAbove() {
        AccessoryFit.Mesh mesh =
                AccessoryFit.mesh(List.of(points(-1, 0, 0, 0.1f, 2, 0, 0.3f, 2, 0)), null);
        Assertions.assertEquals(0.2f, AccessoryFit.middleAbove(mesh, 1), EPSILON);
        Assertions.assertTrue(Float.isNaN(AccessoryFit.middleAbove(mesh, 3)));
    }

    /** Saving adds the placement and keeps the comment lines at the top. */
    @Test
    void testWritePlacement() throws IOException {
        Path file = temp.resolve("hat.json");
        Files.writeString(
                file,
                """
                // a hat, batch one
                // Written by partgen.py
                [
                  {"id": "gen:part/head_top/hat", "plan": "ikala:biped", "kind": "accessory",
                   "fits": ["head_top"], "placement": {"female": {"scale": 0.9}}}
                ]
                """,
                StandardCharsets.UTF_8);
        Id id = Id.parse("gen:part/head_top/hat");
        AccessoryAligner.writePlacement(
                file, id, Part.ANY_VARIANT, new Part.Placement(new Vec3(0, 0.01f, 0), null, 1.1f));

        String text = Files.readString(file, StandardCharsets.UTF_8);
        Assertions.assertTrue(text.startsWith("// a hat, batch one\n// Written by partgen.py\n"));
        JsonObject definition = AccessoryAligner.definitionIn(JsonText.parse(text, e -> {}), id);
        Assertions.assertNotNull(definition);
        JsonObject placements = definition.getAsJsonObject(Part.FIELD_PLACEMENT);
        Assertions.assertEquals(0.9f, placements.getAsJsonObject("female").get("scale").getAsFloat());
        JsonObject any = placements.getAsJsonObject(Part.ANY_VARIANT);
        Assertions.assertEquals(1.1f, any.get("scale").getAsFloat(), EPSILON);
        Assertions.assertEquals(0.01f, any.getAsJsonArray("offset").get(1).getAsFloat(), EPSILON);
        Assertions.assertFalse(any.has("rotate"));
    }

    /** A file with comments inside is left alone, as writing it again would drop them. */
    @Test
    void testCommentsInsideRefused() throws IOException {
        Path file = temp.resolve("commented.json");
        String original =
                """
                {
                  // the iron helmet
                  "id": "test:part/helmet", "kind": "accessory"
                }
                """;
        Files.writeString(file, original, StandardCharsets.UTF_8);
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        AccessoryAligner.writePlacement(
                                file, Id.parse("test:part/helmet"), "*", Part.Placement.NONE));
        Assertions.assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }
}
