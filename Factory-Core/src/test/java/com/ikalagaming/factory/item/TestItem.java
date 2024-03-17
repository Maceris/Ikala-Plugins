package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

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
class TestItem {

    /** Check that we properly combine names to the fully qualified format. */
    @Test
    void testCombineName() {
        var modName = "mod";
        var itemName = "item_name";

        var combined = Item.combineName(modName, itemName);

        assertTrue(combined.matches(Item.FULLY_QUALIFIED_NAME_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("modNameValidProvider")
    void testModNameValidFormat(String modName) {
        assertTrue(modName.matches(Item.MOD_NAME_FORMAT));
    }

    private static Stream<Arguments> modNameValidProvider() {
        return Stream.of(
                Arguments.of("simple"),
                Arguments.of("A"),
                Arguments.of("abcd1234"),
                Arguments.of("a-1"),
                Arguments.of("a_1"),
                Arguments.of("3ty1"),
                Arguments.of("thisNameHasThirtyTwoCharactersIn"));
    }

    @ParameterizedTest
    @MethodSource("modNameInvalidProvider")
    void testModNameInvalidFormat(String modName) {
        assertFalse(modName.matches(Item.MOD_NAME_FORMAT));
    }

    private static Stream<Arguments> modNameInvalidProvider() {
        return Stream.of(
                Arguments.of("-m"),
                Arguments.of("_m"),
                Arguments.of("K^"),
                Arguments.of("a#"),
                Arguments.of("*"),
                Arguments.of("thisNameHasThirty-ThreeCharacters"));
    }

    @ParameterizedTest
    @MethodSource("itemNameValidProvider")
    void testItemNameValidFormat(String itemName) {
        assertTrue(itemName.matches(Item.ITEM_NAME_FORMAT));
    }

    private static Stream<Arguments> itemNameValidProvider() {
        return Stream.of(
                Arguments.of("simple"),
                Arguments.of("A"),
                Arguments.of("abcd1234"),
                Arguments.of("a-1"),
                Arguments.of("a_1"),
                Arguments.of("3ty1"),
                Arguments.of("thisNameHasSixtyFourCharactersIn-HonestlyWhoNeedsAnyNameThisLong"));
    }

    @ParameterizedTest
    @MethodSource("itemNameInvalidProvider")
    void testItemNameInvalidFormat(String itemName) {
        assertFalse(itemName.matches(Item.ITEM_NAME_FORMAT));
    }

    private static Stream<Arguments> itemNameInvalidProvider() {
        return Stream.of(
                Arguments.of("-m"),
                Arguments.of("_m"),
                Arguments.of("K^"),
                Arguments.of("a#"),
                Arguments.of("*"),
                Arguments.of("thisNameHasSixtyFiveCharacters-SoItShouldThrowAnErrorForItsLength"));
    }
}
