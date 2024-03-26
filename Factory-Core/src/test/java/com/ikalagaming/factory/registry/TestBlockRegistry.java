package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tests for the item registry.
 *
 * @author Ches Burks
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestBlockRegistry {
    private static MockedStatic<FactoryPlugin> fakePlugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        var bundle =
                ResourceBundle.getBundle(
                        "com.ikalagaming.factory.strings", Localization.getLocale());

        fakePlugin = Mockito.mockStatic(FactoryPlugin.class);
        fakePlugin.when(FactoryPlugin::getResourceBundle).thenReturn(bundle);
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        fakePlugin.close();
    }

    @Mock private TagRegistry tagRegistry;
    @Mock private MaterialRegistry materialRegistry;
    private BlockRegistry blockRegistry;
    private String modName;
    private String blockName;
    private String combinedName;

    private String material;

    private List<String> tags;

    @BeforeEach
    void setup() {
        modName = "mod_name-complicated.1";
        blockName = "block_name-complicated.1";
        combinedName = RegistryConstants.combineName(modName, blockName);
        material = "wood";
        tags = List.of("organic");
        blockRegistry = new BlockRegistry(tagRegistry, materialRegistry);

        given(tagRegistry.tagExists(anyString())).willReturn(true);
        given(materialRegistry.materialExists(anyString())).willReturn(true);
    }

    @Test
    void testFind() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        blockRegistry.register(combinedName, definition);

        Optional<BlockDefinition> result = blockRegistry.find(combinedName);

        assertNotNull(result);
        assertTrue(result.isPresent());
        assertEquals(Optional.of(definition), result);
    }

    @Test
    void testGetNames() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        blockRegistry.register(combinedName, definition);

        List<String> names = blockRegistry.getNames();

        assertNotNull(names);
        assertEquals(1, names.size());
        assertEquals(combinedName, names.get(0));
    }

    @Test
    void testRegister() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        assertTrue(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterDuplicate() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        assertTrue(blockRegistry.register(combinedName, definition));
        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidItemName() {
        var invalidName = "%@#$Kevin?";
        var definition = new BlockDefinition(modName, invalidName, material, tags);

        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidMaterial() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        given(materialRegistry.materialExists(anyString())).willReturn(false);

        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidModName() {
        var invalidName = "%@#$Kevin?";
        var definition = new BlockDefinition(invalidName, blockName, material, tags);

        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidName() {
        var invalidName = "%@#$Kevin?";
        var definition = new BlockDefinition(modName, blockName, material, tags);

        assertFalse(blockRegistry.register(invalidName, definition));
    }

    @Test
    void testRegisterInvalidTag() {
        var definition = new BlockDefinition(modName, blockName, material, tags);

        given(tagRegistry.tagExists(anyString())).willReturn(false);

        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterMismatchedNames() {
        var definition = new BlockDefinition(modName, blockName, material, tags);
        var combinedName = RegistryConstants.combineName(modName, "different-item-name");

        assertFalse(blockRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterNullMaterial() {
        var definition = new BlockDefinition(modName, blockName, null, tags);

        assertTrue(blockRegistry.register(combinedName, definition));
    }
}
