package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for how body parts meet at seams: their outer seams, and which welded parts show caps where
 * an open part sits across.
 *
 * @author Ches Burks
 */
class TestPartSeams {
    /** An arm and the torso it hangs from, joined at seams. */
    private static final String PLAN =
            """
            { "id": "t:plan", "rig": "t:rig", "variants": [{ "id": "male" }],
              "slots": [
                { "id": "torso", "bones": ["chest"], "seams": ["shoulder.L"] },
                { "id": "upperArm.L", "bones": ["upperarm.L"], "seams": ["shoulder.L", "elbow.L"] },
                { "id": "lowerArm.L", "bones": ["forearm.L"], "seams": ["elbow.L", "wrist.L"] },
                { "id": "hand.L", "bones": ["hand.L"], "seams": ["wrist.L"] } ] }
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
     * A body part filling some slots, meeting its neighbors at some seams in some modes.
     *
     * @param name Its name.
     * @param fills The slots it fills, as a JSON list.
     * @param seams Its seam modes, as a JSON object.
     * @return It.
     */
    private static Part part(String name, String fills, String seams) {
        return read(
                        """
                { "id": "t:part/%s", "plan": "t:plan", "kind": "body", "fills": %s,
                  "meshes": { "*": "a.glb#%s" }, "seams": %s }
                """
                        .formatted(name, fills, name, seams),
                Part.class);
    }

    /** A part's outer seams are those of its slots that no other slot it fills shares. */
    @Test
    void testOuterSeams() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part arm = part("arm", "[\"upperArm.L\", \"lowerArm.L\", \"hand.L\"]", "{}");
        Part forearm = part("forearm", "[\"lowerArm.L\"]", "{}");
        Assertions.assertEquals(Set.of("shoulder.L"), arm.outerSeams(plan));
        Assertions.assertEquals(Set.of("elbow.L", "wrist.L"), forearm.outerSeams(plan));
        Assertions.assertEquals(Part.SeamMode.WELD, forearm.seamMode("elbow.L"));
    }

    /** A skeleton arm, open at the shoulder, leaves the torso showing its shoulder cap. */
    @Test
    void testOpenArmCapsTorso() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part torso = part("torso", "[\"torso\"]", "{}");
        Part arm =
                part(
                        "arm",
                        "[\"upperArm.L\", \"lowerArm.L\", \"hand.L\"]",
                        "{ \"shoulder.L\": \"open\" }");
        Assertions.assertEquals(
                Map.of(torso, List.of("shoulder.L")),
                PartAssembler.cappedSeams(List.of(torso, arm), plan));
    }

    /** A skeleton forearm caps the upper arm above it and the hand below it. */
    @Test
    void testOpenForearmCapsBothNeighbors() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part upper = part("upper", "[\"upperArm.L\"]", "{}");
        Part forearm =
                part(
                        "forearm",
                        "[\"lowerArm.L\"]",
                        "{ \"elbow.L\": \"open\", \"wrist.L\": \"open\" }");
        Part hand = part("hand", "[\"hand.L\"]", "{}");
        Map<Part, List<String>> caps =
                PartAssembler.cappedSeams(List.of(upper, forearm, hand), plan);
        Assertions.assertEquals(Map.of(upper, List.of("elbow.L"), hand, List.of("wrist.L")), caps);
    }

    /** Welded neighbors, and open ones meeting open ones, show no caps. */
    @Test
    void testNoCaps() {
        BodyPlan plan = read(PLAN, BodyPlan.class);
        Part upper = part("upper", "[\"upperArm.L\"]", "{}");
        Part forearm = part("forearm", "[\"lowerArm.L\"]", "{}");
        Assertions.assertTrue(PartAssembler.cappedSeams(List.of(upper, forearm), plan).isEmpty());

        Part openUpper = part("open_upper", "[\"upperArm.L\"]", "{ \"elbow.L\": \"open\" }");
        Part openForearm = part("open_forearm", "[\"lowerArm.L\"]", "{ \"elbow.L\": \"open\" }");
        Assertions.assertTrue(
                PartAssembler.cappedSeams(List.of(openUpper, openForearm), plan).isEmpty());
    }

    /** Caps are named after the part's mesh and the seam, apart from its other materials. */
    @Test
    void testCapMeshName() {
        Assertions.assertEquals(
                "mannequin_upperArm.L_cap_elbow.L",
                PartAssembler.capMesh("mannequin_upperArm.L", "elbow.L"));
    }
}
