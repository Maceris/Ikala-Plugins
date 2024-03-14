package com.ikalagaming.factory.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.util.SafeResourceLoader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ResourceBundle;

/**
 * Tests for reward type.
 *
 * @author Ches Burks
 */
class TestRewardType {

    private static MockedStatic<FactoryPlugin> fakePlugin;
    private static MockedStatic<SafeResourceLoader> fakeLoader;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        var bundle =
                ResourceBundle.getBundle(
                        "com.ikalagaming.factory.strings", Localization.getLocale());

        fakePlugin = Mockito.mockStatic(FactoryPlugin.class);
        fakePlugin.when(FactoryPlugin::getResourceBundle).thenReturn(bundle);

        fakeLoader = Mockito.mockStatic(SafeResourceLoader.class);
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        fakePlugin.close();
        fakeLoader.close();
    }

    @ParameterizedTest
    @EnumSource(RewardType.class)
    void testToString(RewardType type) {
        var expectedString = "foo";
        fakeLoader
                .when(() -> SafeResourceLoader.getString(any(), any(ResourceBundle.class)))
                .thenReturn(expectedString);

        assertEquals(expectedString, type.toString());
    }
}
