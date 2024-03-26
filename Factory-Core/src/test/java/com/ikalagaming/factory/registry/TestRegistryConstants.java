package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests for items.
 *
 * @author Ches Burks
 */
class TestRegistryConstants {

    private static Stream<Arguments> itemNameInvalidProvider() {
        return Stream.of(
                Arguments.of("-m"),
                Arguments.of("_m"),
                Arguments.of("A"),
                Arguments.of("haA"),
                Arguments.of("K^"),
                Arguments.of("a#"),
                Arguments.of("*"),
                Arguments.of("thisNameHasSixtyFiveCharacters-SoItShouldThrowAnErrorForItsLength"));
    }

    private static Stream<Arguments> itemNameValidProvider() {
        return Stream.of(
                Arguments.of("simple"),
                Arguments.of("foo.bar"),
                Arguments.of("abcd1234"),
                Arguments.of("a-1"),
                Arguments.of("a_1"),
                Arguments.of("3ty1"),
                Arguments.of("thisnamehassixtyfourcharactersin-honestlywhoneedsanynamethislong"));
    }

    private static Stream<Arguments> modNameInvalidProvider() {
        return Stream.of(
                Arguments.of("-m"),
                Arguments.of("_m"),
                Arguments.of("A"),
                Arguments.of("haA"),
                Arguments.of("K^"),
                Arguments.of("a#"),
                Arguments.of("*"),
                Arguments.of("thisnamehasthirty-threecharacters"));
    }

    private static Stream<Arguments> modNameValidProvider() {
        return Stream.of(
                Arguments.of("simple"),
                Arguments.of("foo.bar"),
                Arguments.of("abcd1234"),
                Arguments.of("a-1"),
                Arguments.of("a_1"),
                Arguments.of("3ty1"),
                Arguments.of("thisnamehasthirtytwocharactersin"));
    }

    /** Check that we properly combine names to the fully qualified format. */
    @Test
    void testCombineName() {
        var modName = "mod";
        var itemName = "item_name";

        var combined = RegistryConstants.combineName(modName, itemName);

        assertTrue(combined.matches(RegistryConstants.FULLY_QUALIFIED_NAME_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("itemNameInvalidProvider")
    void testItemNameInvalidFormat(String itemName) {
        assertFalse(itemName.matches(RegistryConstants.RESOURCE_NAME_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("itemNameValidProvider")
    void testItemNameValidFormat(String itemName) {
        assertTrue(itemName.matches(RegistryConstants.RESOURCE_NAME_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("modNameInvalidProvider")
    void testModNameInvalidFormat(String modName) {
        assertFalse(modName.matches(RegistryConstants.MOD_NAME_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("modNameValidProvider")
    void testModNameValidFormat(String modName) {
        assertTrue(modName.matches(RegistryConstants.MOD_NAME_FORMAT));
    }
}
