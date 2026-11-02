package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link AccessoryRests}: accessories move out to rest on the body parts and clothing
 * worn under them.
 *
 * @author Ches Burks
 */
class TestAccessoryRests {
    /** How near two floats must be to count as equal. */
    private static final float EPSILON = 1e-5f;

    /** Floats per vertex in a part. */
    private static final int STRIDE = 14;

    /** A torso with a jacket and a cape over it, and a back socket resting on it. */
    private static final String PLAN =
            """
            { "id": "t:plan", "rig": "t:rig", "variants": [{ "id": "male" }],
              "slots": [ { "id": "torso", "bones": ["chest"] }, { "id": "head", "bones": ["head"] } ],
              "layers": [
                { "id": "jacket", "covers": ["torso"], "order": 20, "standoff": 0.02 },
                { "id": "cape", "covers": ["torso"], "order": 40, "standoff": 0.01 },
                { "id": "hood", "covers": ["head"], "order": 40, "standoff": 0.03 } ],
              "forms": [ { "id": "tight", "offset": 0 }, { "id": "bulky", "offset": 0.06 } ],
              "sockets": [
                { "id": "back", "bone": "socket.back", "type": "back.large",
                  "rests": { "slot": "torso", "out": [0, 0, -2] } },
                { "id": "grip.R", "bone": "hand.R", "type": "grip.1h" } ] }
            """;

    /** Collects the problems reading reports. */
    private static final class Collect implements Report {
        /** The problems reported. */
        final List<String> problems = new ArrayList<>();

        @Override
        public void error(String path, String message) {
            problems.add(path + ": " + message);
        }

        @Override
        public void warning(String path, String message) {
            problems.add(path + ": " + message);
        }
    }

    /**
     * Read a definition that must be valid.
     *
     * @param text Its JSON.
     * @param type Its record class.
     * @param <T> Its type.
     * @return It.
     */
    private static <T extends Record> T read(String text, Class<T> type) {
        Collect report = new Collect();
        T read =
                DataFormat.readDefinition(
                        JsonText.parse(text, error -> Assertions.fail(error.toString())),
                        type,
                        report);
        Assertions.assertTrue(report.problems.isEmpty(), report.problems.toString());
        return read;
    }

    /**
     * A part.
     *
     * @param name Its name.
     * @param fields Its other fields, as JSON members.
     * @return It.
     */
    private static Part part(String name, String fields) {
        return read(
                        """
                { "id": "t:part/%s", "plan": "t:plan", "meshes": { "*": "a.glb#%s" }, %s }
                """
                        .formatted(name, name, fields),
                Part.class);
    }

    /** A plain torso. */
    private static final Part TORSO = part("torso", "\"kind\": \"body\", \"fills\": [\"torso\"]");

    /** A bulky robot torso. */
    private static final Part ROBOT =
            part("robot", "\"kind\": \"body\", \"fills\": [\"torso\"], \"form\": \"bulky\"");

    /** A backpack on the back socket. */
    private static final Part PACK = part("pack", "\"kind\": \"accessory\", \"fits\": [\"back\"]");

    /** A jacket. */
    private static final Part JACKET =
            part("jacket", "\"kind\": \"layer\", \"fills\": [\"jacket\"]");

    /** The back socket. */
    private static BodyPlan.Socket back(BodyPlan plan) {
        return plan.socket("back");
    }

    /** On the bare body, nothing moves. */
    @Test
    void bare() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Vector3f push = AccessoryRests.push(plan, back(plan), List.of(TORSO, PACK));
        Assertions.assertEquals(0, push.length(), EPSILON);
    }

    /** A bulky body part moves it out by its form, along the resting direction made unit. */
    @Test
    void bulkyBody() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Vector3f push = AccessoryRests.push(plan, back(plan), List.of(ROBOT, PACK));
        Assertions.assertEquals(-0.06f, push.z, EPSILON);
        Assertions.assertEquals(0, push.x, EPSILON);
    }

    /** A body part's own standoff takes the place of its form's offset. */
    @Test
    void ownStandoff() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part slim =
                part(
                        "slim",
                        "\"kind\": \"body\", \"fills\": [\"torso\"], \"form\": \"bulky\","
                                + " \"standoff\": 0.035");
        Assertions.assertEquals(
                0.035f, AccessoryRests.standoff(plan, "torso", List.of(slim)), EPSILON);
    }

    /** Clothing sits over the body part's form, so the two add, the thickest layer winning. */
    @Test
    void clothingOverForm() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part cape = part("cape", "\"kind\": \"layer\", \"fills\": [\"cape\"]");
        Assertions.assertEquals(
                0.02f, AccessoryRests.standoff(plan, "torso", List.of(TORSO, JACKET)), EPSILON);
        Assertions.assertEquals(
                0.08f,
                AccessoryRests.standoff(plan, "torso", List.of(ROBOT, JACKET, cape)),
                EPSILON);
        Part puffy =
                part("puffy", "\"kind\": \"layer\", \"fills\": [\"jacket\"], \"standoff\": 0.05");
        Assertions.assertEquals(
                0.05f, AccessoryRests.standoff(plan, "torso", List.of(TORSO, puffy)), EPSILON);
    }

    /** Clothing on other slots, or hidden by another part, doesn't count. */
    @Test
    void otherAndHiddenClothing() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part hood = part("hood", "\"kind\": \"layer\", \"fills\": [\"hood\"]");
        Assertions.assertEquals(
                0, AccessoryRests.standoff(plan, "torso", List.of(TORSO, hood)), EPSILON);
        Part bare =
                part(
                        "harness",
                        "\"kind\": \"accessory\", \"fits\": [\"back\"], \"hidesLayers\":"
                                + " [\"jacket\"]");
        Assertions.assertEquals(
                0, AccessoryRests.standoff(plan, "torso", List.of(TORSO, JACKET, bare)), EPSILON);
    }

    /** A socket with no resting place, or none at all, leaves it where it is. */
    @Test
    void noRest() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Assertions.assertEquals(
                0,
                AccessoryRests.push(plan, plan.socket("grip.R"), List.of(ROBOT)).length(),
                EPSILON);
        Assertions.assertEquals(0, AccessoryRests.push(plan, null, List.of(ROBOT)).length());
    }

    /** An accessory goes on the first socket it fits, by name or type. */
    @Test
    void socketOf() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Assertions.assertEquals("back", AccessoryRests.socketOf(plan, PACK).id());
        Part sword = part("sword", "\"kind\": \"accessory\", \"fits\": [\"grip.1h\"]");
        Assertions.assertEquals("grip.R", AccessoryRests.socketOf(plan, sword).id());
        Assertions.assertEquals("back", plan.socketOnBone("socket.back").id());
    }

    /** Moving a mesh moves its vertices and bounds; no move gives the same mesh. */
    @Test
    void moved() {
        float[] vertices = new float[2 * STRIDE];
        vertices[STRIDE] = 1;
        ImportedCharacter.Part mesh =
                new ImportedCharacter.Part(
                        "pack",
                        vertices,
                        new int[8],
                        new float[8],
                        new int[0],
                        -1,
                        new Vector3f(),
                        new Vector3f(1, 0, 0));
        Assertions.assertSame(mesh, AccessoryRests.moved(mesh, new Vector3f()));
        ImportedCharacter.Part moved = AccessoryRests.moved(mesh, new Vector3f(0, 0, -0.06f));
        Assertions.assertEquals(-0.06f, moved.vertices()[2], EPSILON);
        Assertions.assertEquals(1, moved.vertices()[STRIDE], EPSILON);
        Assertions.assertEquals(-0.06f, moved.vertices()[STRIDE + 2], EPSILON);
        Assertions.assertEquals(-0.06f, moved.min().z, EPSILON);
        // The original is left as it was
        Assertions.assertEquals(0, mesh.vertices()[2]);
    }
}
