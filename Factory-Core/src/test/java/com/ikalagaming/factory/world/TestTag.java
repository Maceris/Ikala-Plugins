package com.ikalagaming.factory.world;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for tags.
 *
 * @author Ches Burks
 *
 */
class TestTag {

	/**
	 * Test the creation of tags.
	 */
	@Test
	void testTagCreation() {
		try {
			Tag parent = new Tag("parent");
			Assertions.assertDoesNotThrow(() -> new Tag("child", parent));
			Assertions.assertDoesNotThrow(() -> new Tag("noParent", null));
		}
		catch (Exception e) {
			Assertions.fail("Failed to create tags", e);
		}
	}

}
