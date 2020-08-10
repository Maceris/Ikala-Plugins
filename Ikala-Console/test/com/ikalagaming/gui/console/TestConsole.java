package com.ikalagaming.gui.console;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ikalagaming.event.EventManager;

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
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestConsole.manager = new EventManager();
	}

	/**
	 * Shuts down the event manager for the console test class.
	 *
	 * @throws Exception If there is an error shutting down the event manager
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestConsole.manager.shutdown();
	}

	private Console console;

	/**
	 * Creates an instance of console to use in the test.
	 *
	 * @throws Exception if an exception occurs when constructing the console
	 */
	@Before
	public void setUp() throws Exception {
		this.console = new Console(TestConsole.manager);
		this.console.onEnable();
	}

	/**
	 * Unloads and dereferences the instance of the console that is created for
	 * each test.
	 *
	 * @throws Exception if an exception is thrown while unloading
	 */
	@After
	public void tearDown() throws Exception {
		this.console.onUnload();
		this.console = null;
	}

	@Test
	public void testAppendMessage() {
		Assert.fail("Not yet implemented"); // TODO
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

	@Test
	public void testDisable() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testEnable() {
		Assert.fail("Not yet implemented"); // TODO
	}

	/**
	 * Tests the getter for height. This depends on {@link #testSetHeight()}, as
	 * the height must be changed to test properly.
	 */
	@Test
	public void testGetHeight() {
		final int TEST_HEIGHT = 450;
		Assert.assertEquals(Console.DEFAULT_HEIGHT, this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT);
		Assert.assertEquals(TEST_HEIGHT, this.console.getHeight(), 0);
	}

	@Test
	public void testGetListeners() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetMaxLineCount() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetName() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetPluginState() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetVersion() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetWidth() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testGetWindowTitle() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testIsEnabled() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testLostOwnership() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnDisable() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnEnable() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnLoad() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnLogErrorEvent() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnLogEvent() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnReportUnknownCommand() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testOnUnload() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testReload() {
		Assert.fail("Not yet implemented"); // TODO
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
		Assert.assertEquals(TEST_HEIGHT_LARGE, this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT_SMALL);
		Assert.assertEquals(TEST_HEIGHT_SMALL, this.console.getHeight(), 0);
		this.console.setHeight(TEST_HEIGHT_NEGATIVE);
		Assert.assertEquals(0, this.console.getHeight(), 0);
	}

	@Test
	public void testSetMaxLineCount() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testSetPluginState() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testSetWidth() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testSetWindowTitle() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowActivated() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowAdapter() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowClosed() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowClosing() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowDeactivated() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowDeiconified() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowGainedFocus() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowIconified() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowLostFocus() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowOpened() {
		Assert.fail("Not yet implemented"); // TODO
	}

	@Test
	public void testWindowStateChanged() {
		Assert.fail("Not yet implemented"); // TODO
	}

}
