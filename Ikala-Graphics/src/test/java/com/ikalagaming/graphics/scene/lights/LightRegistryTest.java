package com.ikalagaming.graphics.scene.lights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.LightHandle;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Owners, generations and slots of the scene's lights. */
class LightRegistryTest {

    private static final String OWNER = "owner#1";
    private static final String OTHER = "other#2";
    private static final Vector3f WHITE = new Vector3f(1, 1, 1);

    private static LightHandle point(LightRegistry registry, String owner, double x) {
        return registry.addPoint(owner, new Vector3d(x, 0, 0), WHITE, 1, Float.NaN);
    }

    @Test
    void aRemovedLightsHandleGoesStale() {
        LightRegistry registry = new LightRegistry();
        LightHandle light = point(registry, OWNER, 0);
        assertTrue(registry.isValid(light));
        assertTrue(registry.remove(OWNER, light));
        assertFalse(registry.isValid(light));
        assertFalse(registry.move(OWNER, light, new Vector3d(1, 2, 3)), "Stale");
        assertFalse(registry.remove(OWNER, light), "Already removed");
        assertEquals(0, registry.getLightCount());
    }

    @Test
    void aFreedSlotIsReusedWithANewGeneration() {
        LightRegistry registry = new LightRegistry();
        LightHandle first = point(registry, OWNER, 0);
        registry.remove(OWNER, first);
        LightHandle second = point(registry, OWNER, 1);
        assertEquals(first.slot(), second.slot());
        assertNotEquals(first.generation(), second.generation());
        assertFalse(registry.isValid(first));
        assertTrue(registry.isValid(second));

        Vector3d position = new Vector3d();
        assertFalse(registry.getPosition(first, position), "The old handle reads nothing");
        assertTrue(registry.getPosition(second, position));
        assertEquals(1, position.x);
    }

    @Test
    void anotherOwnerCantChangeOrRemoveALight() {
        LightRegistry registry = new LightRegistry();
        LightHandle light = point(registry, OWNER, 0);
        assertFalse(registry.move(OTHER, light, new Vector3d(5, 5, 5)));
        assertFalse(registry.setIntensity(OTHER, light, 9));
        assertFalse(registry.remove(OTHER, light));
        assertTrue(registry.isValid(light));
        assertEquals(OWNER, registry.ownerOf(light));

        Vector3d position = new Vector3d();
        registry.getPosition(light, position);
        assertEquals(new Vector3d(0, 0, 0), position, "Unmoved");
    }

    @Test
    void removingAnOwnersLightsLeavesTheRest() {
        LightRegistry registry = new LightRegistry();
        List<LightHandle> mine = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            mine.add(point(registry, OWNER, i));
        }
        LightHandle theirs = point(registry, OTHER, 10);

        assertEquals(3, registry.removeAllOwnedBy(OWNER));
        mine.forEach(light -> assertFalse(registry.isValid(light)));
        assertTrue(registry.isValid(theirs));
        assertEquals(1, registry.getLightCount());
        assertNull(registry.ownerOf(mine.getFirst()));
    }

    @Test
    void visitSeesEveryLiveLight() {
        LightRegistry registry = new LightRegistry();
        LightHandle a = point(registry, OWNER, 1);
        point(registry, OWNER, 2);
        registry.addSpot(OTHER, new Vector3d(3, 0, 0), new Vector3f(0, -2, 0), WHITE, 1, 5, 10, 30);
        registry.remove(OWNER, a);

        List<Double> xs = new ArrayList<>();
        List<LightType> types = new ArrayList<>();
        int visited =
                registry.visit(
                        light -> {
                            xs.add(light.position().x());
                            types.add(light.type());
                        });
        assertEquals(2, visited);
        assertEquals(List.of(2.0, 3.0), xs);
        assertEquals(List.of(LightType.POINT, LightType.SPOT), types);
    }

    @Test
    void aSpotlightsDirectionIsNormalizedAndItsConeStoredAsCosines() {
        LightRegistry registry = new LightRegistry();
        registry.addSpot(OWNER, new Vector3d(), new Vector3f(0, -2, 0), WHITE, 1, Float.NaN, 0, 60);
        registry.visit(
                light -> {
                    assertEquals(new Vector3f(0, -1, 0), light.direction());
                    assertEquals(1, light.cosInner(), 1e-6);
                    assertEquals(0.5, light.cosOuter(), 1e-6);
                });
    }

    @Test
    void badSpotlightsAreRefused() {
        LightRegistry registry = new LightRegistry();
        Vector3d origin = new Vector3d();
        Vector3f down = new Vector3f(0, -1, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.addSpot(OWNER, origin, new Vector3f(), WHITE, 1, 5, 0, 30),
                "No direction");
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.addSpot(OWNER, origin, down, WHITE, 1, 5, 40, 30),
                "Inner past outer");
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.addSpot(OWNER, origin, down, WHITE, 1, 5, 0, 180),
                "A half turn isn't a cone");
        assertEquals(0, registry.getLightCount());
    }

    @Test
    void theRangeFollowsTheIntensityUntilSet() {
        LightRegistry registry = new LightRegistry();
        LightHandle light = point(registry, OWNER, 0);
        assertEquals(10, registry.getRange(light), 1e-5);
        registry.setIntensity(OWNER, light, 4);
        assertEquals(20, registry.getRange(light), 1e-5);
        registry.setRange(OWNER, light, 12);
        registry.setIntensity(OWNER, light, 1);
        assertEquals(12, registry.getRange(light), "Set ranges stay");
        registry.setRange(OWNER, light, Float.NaN);
        assertEquals(10, registry.getRange(light), 1e-5, "Following the intensity again");
    }
}
