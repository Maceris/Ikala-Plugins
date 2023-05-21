package com.ikalagaming.language;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * A plugin that provides a natural language system for interacting with the
 * game.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class LanguagePlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Language";

	/**
	 * The resource bundle for the Language plugin.
	 *
	 * @return The bundle.
	 * @param resourceBundle The new bundle to use.
	 */
	@Getter
	@Setter
	private static ResourceBundle resourceBundle;

	private Set<Listener> listeners = new HashSet<>();

	@Override
	public Set<Listener> getListeners() {
		return this.listeners;
	}

	@Override
	public String getName() {
		return LanguagePlugin.PLUGIN_NAME;
	}

	@Override
	public boolean onEnable() {
		if (!TagManager.load()) {
			return false;
		}
		return true;
	}

	@Override
	public boolean onLoad() {
		try {
			LanguagePlugin.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.language.runtime", Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			LanguagePlugin.log.warn("Locale not found for {} in onLoad()",
				LanguagePlugin.PLUGIN_NAME);
		}
		return true;
	}

}
