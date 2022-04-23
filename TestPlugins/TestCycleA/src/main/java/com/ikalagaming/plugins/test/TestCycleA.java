package com.ikalagaming.plugins.test;

import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.test.TestCycleB;

/**
 * A plugin with a cyclic dependency.
 * 
 * @author Ches Burks
 *
 */
public class TestCycleA extends Plugin {
	private TestCycleB otherPlugin;
	
	@Override
	public boolean onLoad() {
		this.otherPlugin = new TestCycleB();
		return true;
	}
	
	@Override
	public boolean onUnload() {
		this.otherPlugin = null;
		return true;
	}
}
