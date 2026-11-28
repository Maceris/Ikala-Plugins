package com.ikalagaming.converter.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.converter.texgraph.GraphParameter;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.FieldKind;

import org.junit.jupiter.api.Test;

/** Graph parameters as schema fields, and their values between text and type. */
class GraphValuesTest {
    @Test
    void eachKindBecomesItsField() {
        final Field wear =
                GraphValues.field(
                        GraphParameter.of("wear", GraphParameter.Kind.SCALAR).withValue("0.4"));
        assertEquals(FieldKind.NUMBER, wear.kind());
        assertEquals(0.4, wear.defaultValue());
        final Field tint =
                GraphValues.field(
                        GraphParameter.of("tint", GraphParameter.Kind.COLOR).withValue("#C8B48A"));
        assertEquals(FieldKind.COLOR, tint.kind());
        assertEquals("#c8b48a", tint.defaultValue());
        assertEquals(
                FieldKind.FLAG,
                GraphValues.field(GraphParameter.of("on", GraphParameter.Kind.SWITCH)).kind());
        assertEquals(
                FieldKind.INTEGER,
                GraphValues.field(GraphParameter.of("seed", GraphParameter.Kind.SEED)).kind());
    }

    @Test
    void valuesGoBackToTheTextFilesWrite() {
        final GraphParameter wear = GraphParameter.of("wear", GraphParameter.Kind.SCALAR);
        assertEquals("0.8", GraphValues.text(GraphValues.typed(wear, "0.8")));
        assertEquals("2", GraphValues.text(GraphValues.typed(wear, "2")));
        final GraphParameter seed = GraphParameter.of("seed", GraphParameter.Kind.SEED);
        assertEquals("7", GraphValues.text(GraphValues.typed(seed, "7")));
        final GraphParameter on = GraphParameter.of("on", GraphParameter.Kind.SWITCH);
        assertEquals("true", GraphValues.text(GraphValues.typed(on, "true")));
    }
}
