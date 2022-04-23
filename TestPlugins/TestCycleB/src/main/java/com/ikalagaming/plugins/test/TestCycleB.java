package com.ikalagaming.plugins.test;

import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.test.TestCycleA;

/**
 * A plugin with a cyclic dependency.
 * 
 * @author Ches Burks
 *
 */
public class TestCycleB extends Plugin {

	private TestCycleA otherPlugin;
	
	@Override
	public boolean onLoad() {
		this.otherPlugin = new TestCycleA();
		return true;
	}
	
	@Override
	public boolean onUnload() {
		this.otherPlugin = null;
		return true;
	}
}
