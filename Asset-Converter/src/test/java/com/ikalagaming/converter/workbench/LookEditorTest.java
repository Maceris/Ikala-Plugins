package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MaterialText;
import com.ikalagaming.graphics.graph.Material;

import org.junit.jupiter.api.Test;

/** How the look editor writes a look's keys, and materials only where they differ. */
class LookEditorTest {
    private static final String STONE =
            "{model:\"lotomation:block/cube_all\", texture:\"lotomation:block/stone\","
                    + " material:\"lotomation:block/stone\"}";

    @Test
    void keysAreReplacedAddedAndTakenOutKeepingTheRest() {
        assertEquals(
                "{model:\"lotomation:block/cube_all\", texture:\"lotomation:block/dirt\","
                        + " material:\"lotomation:block/stone\"}",
                LookEditor.withKey(STONE, "texture", "lotomation:block/dirt"));
        assertEquals(
                "{model:\"lotomation:block/cube_all\", texture:\"lotomation:block/stone\"}",
                LookEditor.withKey(STONE, "material", null));
        assertEquals(
                "{texture:\"lotomation:block/stone\", material:\"lotomation:block/stone\"}",
                LookEditor.withKey(STONE, "model", null));
        assertEquals(
                "/* a comment */\n{model:\"a:b\", material:\"a:m\"}\n",
                LookEditor.withKey("/* a comment */\n{model:\"a:b\"}\n", "material", "a:m"));
        assertEquals("{material:\"a:m\"}", LookEditor.withKey("{}", "material", "a:m"));
    }

    @Test
    void onlyChangedKeysAreWrittenSoAFluidStaysOne() {
        final String water =
                "{model:\"fluid\", texture:\"lotomation:block/water\","
                        + " material:\"lotomation:block/water\"}";
        // Read, a fluid's model is the default cube; changing the material leaves it alone
        final BlockContent.Look before =
                new BlockContent.Look(
                        "lotomation:water",
                        BlockContent.DEFAULT_MODEL,
                        "lotomation:block/water",
                        "lotomation:block/water");
        final BlockContent.Look after =
                new BlockContent.Look(
                        "lotomation:water",
                        BlockContent.DEFAULT_MODEL,
                        "lotomation:block/water",
                        "lotomation:block/glass");
        assertEquals(
                "{model:\"fluid\", texture:\"lotomation:block/water\","
                        + " material:\"lotomation:block/glass\"}",
                LookEditor.rewrite(water, before, after));
    }

    @Test
    void materialsWriteOnlyWhatDiffersFromTheDefaults() {
        final Material bronze = new Material().setValues(MaterialText.defaults());
        bronze.setRoughness(0.4f);
        bronze.setMetallic(1);
        assertEquals(
                "roughness: 0.4\nmetallic: 1\n",
                MaterialText.writeDiffering(
                        bronze, new MaterialSection.Textures(null, null, null, null)));
    }
}
