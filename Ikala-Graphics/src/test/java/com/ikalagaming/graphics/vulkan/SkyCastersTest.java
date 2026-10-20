package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for {@link SkyCasters}. */
class SkyCastersTest {

    private static final double EPSILON = 1e-5;

    private SkyCasters casters;

    private final List<double[]> boxes = new ArrayList<>();

    private final Set<MeshHandle> uploaded = new HashSet<>();

    private static Model model(MeshKind kind, Vector3f min, Vector3f max) {
        Model model = new Model("crate");
        MeshData data = new MeshData(min, max, 0, new float[0], new int[0], 0, new byte[0]);
        data.setMesh(new MeshHandle(0, 0, 3, 3, min, max, kind));
        model.getMeshDataList().add(data);
        return model;
    }

    private void change(int slot, boolean alive, Vector3d at, Model model, boolean moving) {
        casters.changed(
                slot,
                alive,
                at,
                new Quaternionf(),
                1,
                model,
                moving,
                uploaded::contains,
                (min, max) ->
                        boxes.add(new double[] {min[0], min[1], min[2], max[0], max[1], max[2]}));
    }

    @BeforeEach
    void setUp() {
        casters = new SkyCasters();
        boxes.clear();
        uploaded.clear();
    }

    @Test
    void placingAndRemovingDrawTheBoxAgain() {
        Model crate = model(MeshKind.STANDARD, new Vector3f(-1, 0, -1), new Vector3f(1, 2, 1));
        uploaded.add(crate.getMeshDataList().getFirst().getMesh());
        change(3, true, new Vector3d(10, 0, 0), crate, false);
        assertEquals(1, boxes.size());
        assertArrayEquals(new double[] {9, 0, -1, 11, 2, 1}, boxes.getFirst(), EPSILON);

        // Moved: where it was and where it is now
        boxes.clear();
        change(3, true, new Vector3d(20, 0, 0), crate, false);
        assertEquals(2, boxes.size());
        assertArrayEquals(new double[] {9, 0, -1, 11, 2, 1}, boxes.get(0), EPSILON);
        assertArrayEquals(new double[] {19, 0, -1, 21, 2, 1}, boxes.get(1), EPSILON);

        boxes.clear();
        change(3, false, new Vector3d(), null, false);
        assertEquals(1, boxes.size(), "Only where it was");
        assertArrayEquals(new double[] {19, 0, -1, 21, 2, 1}, boxes.getFirst(), EPSILON);
    }

    @Test
    void movingInstancesLeaveTheMap() {
        Model crate = model(MeshKind.STANDARD, new Vector3f(0), new Vector3f(1));
        uploaded.add(crate.getMeshDataList().getFirst().getMesh());
        change(0, true, new Vector3d(), crate, false);
        boxes.clear();
        change(0, true, new Vector3d(5, 0, 0), crate, true);
        assertEquals(1, boxes.size(), "Taken out where it was");
        boxes.clear();
        change(0, true, new Vector3d(6, 0, 0), crate, true);
        assertTrue(boxes.isEmpty(), "Not in the map while moving");
        change(0, true, new Vector3d(6, 0, 0), crate, false);
        assertEquals(1, boxes.size(), "Back in once still");
        assertArrayEquals(new double[] {6, 0, 0, 7, 1, 1}, boxes.getFirst(), EPSILON);
    }

    @Test
    void instancesWaitForTheirMeshes() {
        Model crate = model(MeshKind.STANDARD, new Vector3f(0), new Vector3f(1));
        change(0, true, new Vector3d(), crate, false);
        assertTrue(boxes.isEmpty(), "Nothing to draw before the upload");
        assertEquals(1, casters.waitingCount());
        casters.settle(uploaded::contains, (min, max) -> boxes.add(min));
        assertTrue(boxes.isEmpty());

        uploaded.add(crate.getMeshDataList().getFirst().getMesh());
        casters.settle(uploaded::contains, (min, max) -> boxes.add(min));
        assertEquals(1, boxes.size());
        assertEquals(0, casters.waitingCount());
    }

    @Test
    void onlyStandardMeshesCount() {
        Model section = model(MeshKind.BAKED_OPAQUE, new Vector3f(0), new Vector3f(1));
        assertNull(SkyCasters.boxOf(section, new Vector3d(), new Quaternionf(), 1));
        change(0, true, new Vector3d(), section, false);
        assertTrue(boxes.isEmpty());
    }

    @Test
    void boxesFollowRotationAndScale() {
        Model plank = model(MeshKind.STANDARD, new Vector3f(0, 0, 0), new Vector3f(4, 1, 1));
        double[] box =
                SkyCasters.boxOf(
                        plank,
                        new Vector3d(100, 0, 0),
                        new Quaternionf().rotationY((float) Math.toRadians(90)),
                        2);
        // Along x becomes along -z, twice as long
        assertArrayEquals(new double[] {100, 0, -8, 102, 2, 0}, box, EPSILON);
    }
}
