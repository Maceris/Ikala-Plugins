package com.ikalagaming.language;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * Tests for the tag system.
 * 
 * @author Ches Burks
 *
 */
class TestTag {

	/**
	 * Test the creation of tags.
	 */
	@Test
	@SuppressWarnings("unused")
	void testTagCreation() {
		try {
			Tag parent = new Tag("parent");
			Tag child = new Tag("child", parent);
		}
		catch (Exception e) {
			fail("Failed to create tags", e);
		}
	}

}
