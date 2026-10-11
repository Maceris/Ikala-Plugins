package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** Order independent transparency's sums and resolve. */
class OitMathTest {

    private static final float EPSILON = 1e-4f;

    private record Layer(Vector3f color, float alpha, float depth) {}

    private static Vector3f composite(Vector3f background, Layer... layers) {
        // JOML starts w at 1, and the sums start at nothing
        Vector4f accum = new Vector4f(0, 0, 0, 0);
        float extinction = 0;
        for (Layer layer : layers) {
            extinction =
                    OitMath.add(accum, extinction, layer.color(), layer.alpha(), layer.depth());
        }
        return OitMath.resolve(accum, extinction, new Vector3f(background));
    }

    /** Ordinary back to front alpha blending, the reference. */
    private static Vector3f sorted(Vector3f background, Layer... farToNear) {
        Vector3f result = new Vector3f(background);
        for (Layer layer : farToNear) {
            result.lerp(layer.color(), layer.alpha());
        }
        return result;
    }

    @Test
    void oneLayerIsOrdinaryAlphaBlending() {
        Vector3f background = new Vector3f(0.2f, 0.5f, 0.9f);
        Layer glass = new Layer(new Vector3f(0.8f, 0.3f, 0.1f), 0.4f, 12);
        Vector3f expected = sorted(background, glass);
        Vector3f actual = composite(background, glass);
        assertTrue(expected.equals(actual, EPSILON), expected + " vs " + actual);
    }

    @Test
    void theOrderDrawnDoesNotMatter() {
        Vector3f background = new Vector3f(0.1f, 0.1f, 0.1f);
        Layer a = new Layer(new Vector3f(1, 0, 0), 0.3f, 4);
        Layer b = new Layer(new Vector3f(0, 1, 0), 0.6f, 9);
        Layer c = new Layer(new Vector3f(0, 0, 1), 0.2f, 30);
        Vector3f abc = composite(background, a, b, c);
        Vector3f cab = composite(background, c, a, b);
        Vector3f bca = composite(background, b, c, a);
        assertTrue(abc.equals(cab, EPSILON) && abc.equals(bca, EPSILON));
    }

    @Test
    void theBackgroundShowsThroughExactly() {
        // Black layers, so the result is only how much background gets through
        Vector3f white = new Vector3f(1, 1, 1);
        float[] alphas = {0.1f, 0.5f, 0.25f, 0.7f, 0.05f};
        Layer[] layers = new Layer[alphas.length];
        float through = 1;
        for (int i = 0; i < alphas.length; ++i) {
            layers[i] = new Layer(new Vector3f(), alphas[i], 3 + 7 * i);
            through *= 1 - alphas[i];
        }
        assertEquals(through, composite(white, layers).x, EPSILON);
    }

    @Test
    void nothingDrawnLeavesTheBackground() {
        Vector3f background = new Vector3f(0.3f, 0.6f, 0.9f);
        assertEquals(background, composite(background));
    }

    @Test
    void nearerLayersWeighMore() {
        assertTrue(OitMath.weight(0.5f, 1) > OitMath.weight(0.5f, 50));
        assertTrue(OitMath.weight(0.5f, 50) > OitMath.weight(0.5f, 500));
        assertTrue(OitMath.weight(0.5f, 10_000) > 0, "Clamped, never zero");
    }
}
