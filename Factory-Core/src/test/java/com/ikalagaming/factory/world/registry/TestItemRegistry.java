package com.ikalagaming.factory.world.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.kvt.NodeType;
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
import java.util.ResourceBundle;

/**
 * Tests for the item registry.
 *
 * @author Ches Burks
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestItemRegistry {

    private static MockedStatic<FactoryPlugin> fakePlugin;

    @Mock private TagRegistry tagRegistry;
    @Mock private MaterialRegistry materialRegistry;

    private ItemRegistry itemRegistry;
    private String modName;
    private String itemName;
    private String combinedName;
    private String material;
    private List<String> tags;
    private ItemDefinition.Attribute attribute;

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

    @BeforeEach
    void setup() {
        modName = "mod_name-complicated.1";
        itemName = "item_name-complicated.1";
        combinedName = RegistryConstants.combineName(modName, itemName);
        attribute = new ItemDefinition.Attribute("charge", NodeType.LONG);
        material = "iron";
        tags = List.of("throwable");
        itemRegistry = new ItemRegistry(tagRegistry, materialRegistry);

        given(tagRegistry.tagExists(anyString())).willReturn(true);
        given(materialRegistry.materialExists(anyString())).willReturn(true);
    }

    @Test
    void testRegister() {
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));

        assertTrue(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterNullMaterial() {
        var definition = new ItemDefinition(modName, itemName, null, tags, List.of(attribute));

        assertTrue(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidName() {
        var invalidName = "%@#$Kevin?";
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));

        assertFalse(itemRegistry.register(invalidName, definition));
    }

    @Test
    void testRegisterInvalidModName() {
        var invalidName = "%@#$Kevin?";
        var definition =
                new ItemDefinition(invalidName, itemName, material, tags, List.of(attribute));

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidItemName() {
        var invalidName = "%@#$Kevin?";
        var definition =
                new ItemDefinition(modName, invalidName, material, tags, List.of(attribute));

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterDuplicate() {
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));

        assertTrue(itemRegistry.register(combinedName, definition));
        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterMismatchedNames() {
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));
        var combinedName = RegistryConstants.combineName(modName, "different-item-name");

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidTag() {
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));

        given(tagRegistry.tagExists(anyString())).willReturn(false);

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidMaterial() {
        var definition = new ItemDefinition(modName, itemName, material, tags, List.of(attribute));

        given(materialRegistry.materialExists(anyString())).willReturn(false);

        assertFalse(itemRegistry.register(combinedName, definition));
    }
}
