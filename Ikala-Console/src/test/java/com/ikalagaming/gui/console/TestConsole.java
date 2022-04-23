package com.ikalagaming.gui.console;

import com.ikalagaming.event.EventManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the functionality of the console class.
 *
 * @author Ches Burks
 *
 */
public class TestConsole {

	private static EventManager manager;

	/**
	 * Creates an event manager for the console test class.
	 *
	 * @throws Exception if there is an exception setting up the console.
	 */
	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		TestConsole.manager = new EventManager();
	}

	/**
	 * Shuts down the event manager for the console test class.
	 *
	 * @throws Exception If there is an error shutting down the event manager
	 */
	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		TestConsole.manager.shutdown();
	}

	private Console console;

	/**
	 * Creates an instance of console to use in the test.
	 *
	 * @throws Exception if an exception occurs when constructing the console
	 */
	@BeforeEach
	public void setUp() throws Exception {
		this.console = new Console(TestConsole.manager);
		this.console.init();
	}

	/**
	 * Unloads and dereferences the instance of the console that is created for
	 * each test.
	 *
	 * @throws Exception if an exception is thrown while unloading
	 */
	@AfterEach
	public void tearDown() throws Exception {
		this.console = null;
	}

	/**
	 * Tests the consoles constructor.
	 */
	@Test
	public void testConsole() {
		// it is just testing the constructor so it shouldn't be used
		@SuppressWarnings("unused")
		Console consoleTest = new Console(TestConsole.manager);
	}

	/**
	 * Tests the getter for height. This depends on {@link #testSetHeight()}, as
	 * the height must be changed to test properly.
	 */
	@Test
	public void testGetHeight() {
		final int TEST_HEIGHT = 450;
		Assertions.assertEquals(Console.DEFAULT_HEIGHT,
			this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT);
		Assertions.assertEquals(TEST_HEIGHT, this.console.getHeight(), 0);
	}

	/**
	 * Tests the getter for height. This depends on {@link #testGetHeight()}, as
	 * the height must be checked to test properly.
	 */
	@Test
	public void testSetHeight() {
		final int TEST_HEIGHT_LARGE = 450;
		final int TEST_HEIGHT_SMALL = 10;
		final int TEST_HEIGHT_NEGATIVE = -100;
		this.console.setHeight(TEST_HEIGHT_LARGE);
		Assertions.assertEquals(TEST_HEIGHT_LARGE, this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT_SMALL);
		Assertions.assertEquals(TEST_HEIGHT_SMALL, this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT_NEGATIVE);
		Assertions.assertEquals(0, this.console.getHeight(), 0);
	}

}
