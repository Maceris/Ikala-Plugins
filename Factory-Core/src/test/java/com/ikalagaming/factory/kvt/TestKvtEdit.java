package com.ikalagaming.factory.kvt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenSchemas;
import com.ikalagaming.factory.world.gen.data.WorldgenTypes;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Editing KVT files in place: only what changed is written. */
class TestKvtEdit {
    /** Lotomation's surface height, laid out by hand over several lines. */
    private static final String HEIGHT =
            """
            /* The surface height in blocks */
            {type:"add", args:[N;
              {type:"spline", interp:"cubic", arg:{type:"parameter", id:"lotomation:continentalness"},
               points:[N;{x:-1.0, y:-120.0}, {x:-0.1, y:-4.0}, {x:1.0, y:96.0}]},
              {type:"range_mask", min:0.0, max:2.0, falloff:0.3, arg:{type:"parameter", id:"lotomation:erosion"}}]}
            """;

    private static final Field ROOT = WorldgenSchemas.root(Kind.DENSITY);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String text) {
        WorldgenTypes.ensureRegistered();
        final Schema file = Schema.of("file", ROOT);
        final Node root = TreeStringSerialization.fromString(text).orElseThrow();
        final Schema.Values values = file.read(Map.of(ROOT.name(), KvtSchemas.raw(root)));
        assertEquals(List.of(), values.problems());
        return (Map<String, Object>) values.get(ROOT.name());
    }

    /** A deep copy of a value, so a change doesn't touch the old one. */
    @SuppressWarnings("unchecked")
    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put((String) k, copy(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            final List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(copy(v)));
            return out;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> root, Object... path) {
        Object at = root;
        for (Object step : path) {
            at =
                    step instanceof Integer i
                            ? ((List<Object>) at).get(i)
                            : ((Map<String, Object>) at).get(step);
        }
        return (Map<String, Object>) at;
    }

    private static String edit(Map<String, Object> before, Map<String, Object> after) {
        return KvtSchemas.edit(HEIGHT, ROOT, before, after, NodeType.DOUBLE);
    }

    @Test
    void aChangedNumberChangesOnlyItsText() {
        final Map<String, Object> before = read(HEIGHT);
        final Map<String, Object> after = (Map<String, Object>) copy(before);
        at(after, "args", 0, "points", 1).put("y", -5.0);
        assertEquals(HEIGHT.replace("y:-4.0", "y:-5.0"), edit(before, after));
    }

    @Test
    void aKeyIsTakenOutWithItsComma() {
        final Map<String, Object> before = read(HEIGHT);
        final Map<String, Object> after = (Map<String, Object>) copy(before);
        at(after, "args", 1).remove("falloff");
        assertEquals(HEIGHT.replace(", falloff:0.3", ""), edit(before, after));
    }

    @Test
    void aNewTypeRewritesOnlyItsNode() {
        final Map<String, Object> before = read(HEIGHT);
        final Map<String, Object> after = (Map<String, Object>) copy(before);
        final List<Object> args = (List<Object>) after.get("args");
        args.set(1, Map.of("type", "constant", "value", 2.0));
        assertEquals(
                HEIGHT.replace(
                        "{type:\"range_mask\", min:0.0, max:2.0, falloff:0.3, arg:{type:\"parameter\","
                                + " id:\"lotomation:erosion\"}}",
                        "{type:\"constant\", value:2.0}"),
                edit(before, after));
    }

    @Test
    void aLongerListIsRewrittenInPlace() {
        final Map<String, Object> before = read(HEIGHT);
        final Map<String, Object> after = (Map<String, Object>) copy(before);
        final List<Object> points = (List<Object>) at(after, "args", 0).get("points");
        points.add(Map.of("x", 2.0, "y", 100.0));
        assertEquals(
                HEIGHT.replace("{x:1.0, y:96.0}]", "{x:1.0, y:96.0}, {x:2.0, y:100.0}]"),
                edit(before, after));
    }

    @Test
    void flatFilesKeepTheirCommentAndAddKeysAtTheEnd() {
        final String text = "/* A rock */\n{material:\"stone\", tags:[T;\"solid\"]}\n";
        final Schema.Values values =
                KvtSchemas.read(
                        TreeStringSerialization.fromString(text).orElseThrow(),
                        ContentSchemas.BLOCK);
        final Map<String, Object> after = new LinkedHashMap<>(values.given());
        after.put("collision", "none");
        after.remove("material");
        assertEquals(
                "/* A rock */\n{tags:[T;\"solid\"], collision:\"none\"}\n",
                KvtSchemas.edit(
                        text, ContentSchemas.BLOCK, values.given(), after, NodeType.DOUBLE));
    }

    @Test
    void numbersKeepTheirStyle() {
        assertEquals("0.25F", KvtSchemas.numberText(0.25, "0.5F"));
        assertEquals("3", KvtSchemas.numberText(3.0, "2"));
        assertEquals("2.5", KvtSchemas.numberText(2.5, "2"));
        assertEquals("-5.0", KvtSchemas.numberText(-5.0, "-4.0"));
    }
}
