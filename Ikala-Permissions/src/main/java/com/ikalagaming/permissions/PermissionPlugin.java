package com.ikalagaming.permissions;

import com.ikalagaming.plugins.Plugin;

/**
 * Lifecycle methods for permissions.
 *
 * @author Ches Burks
 *
 */
public class PermissionPlugin extends Plugin {

	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-Permissions";

	@Override
	public String getName() {
		return PermissionPlugin.PLUGIN_NAME;
	}

}
