package com.ikalagaming.example;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ExamplePlugin example plugin}.
 *
 * @author Ches Burks
 *
 */
class TestExamplePlugin {

	/**
	 * Set up before all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		EventManager.getInstance();
		PluginManager.getInstance();
	}

	/**
	 * Tear down after all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@AfterAll
	static void tearDownAfterClass() throws Exception {
		PluginManager.destoryInstance();
		EventManager.destoryInstance();
	}

	/**
	 * Test that the listeners gives back a list that is not null.
	 */
	@Test
	void testGetListeners() {
		ExamplePlugin plugin = new ExamplePlugin();
		Assertions.assertNotNull(plugin.getListeners());
	}

}
