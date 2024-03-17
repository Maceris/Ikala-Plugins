package com.ikalagaming.factory.world.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemDefinition;
import com.ikalagaming.factory.kvt.NodeType;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Tests for the item registry.
 *
 * @author Ches Burks
 */
class TestItemRegistry {

    private static MockedStatic<FactoryPlugin> fakePlugin;

    private ItemRegistry itemRegistry;
    private String modName;
    private String itemName;
    private String combinedName;
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
        modName = "mod_name-Complicated1";
        itemName = "item_name-Complicated1";
        combinedName = Item.combineName(modName, itemName);
        attribute = new ItemDefinition.Attribute("charge", NodeType.LONG);
        tags = List.of("throwable");
        itemRegistry = new ItemRegistry();
    }

    @Test
    void testRegister() {
        var definition = new ItemDefinition(modName, itemName, tags, List.of(attribute));

        assertTrue(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidName() {
        var invalidName = "%@#$Kevin?";
        var definition = new ItemDefinition(modName, itemName, tags, List.of(attribute));

        assertFalse(itemRegistry.register(invalidName, definition));
    }

    @Test
    void testRegisterInvalidModName() {
        var invalidName = "%@#$Kevin?";
        var definition = new ItemDefinition(invalidName, itemName, tags, List.of(attribute));

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterInvalidItemName() {
        var invalidName = "%@#$Kevin?";
        var definition = new ItemDefinition(modName, invalidName, tags, List.of(attribute));

        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterDuplicate() {
        var definition = new ItemDefinition(modName, itemName, tags, List.of(attribute));

        assertTrue(itemRegistry.register(combinedName, definition));
        assertFalse(itemRegistry.register(combinedName, definition));
    }

    @Test
    void testRegisterMismatchedNames() {
        var definition = new ItemDefinition(modName, itemName, tags, List.of(attribute));
        var combinedName = Item.combineName(modName, "DifferentItemName");

        assertFalse(itemRegistry.register(combinedName, definition));
    }
}
