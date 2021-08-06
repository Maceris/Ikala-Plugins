package com.ikalagaming.graphics;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.EventManager;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.events.GraphicsTick;
import com.ikalagaming.graphics.events.WindowDestroyed;

/**
 * Handles the API requests to control graphics.
 *
 * @author Ches Burks
 *
 */
public class ControlListener implements Listener {

	/**
	 * Updated the windows, destroying if necessary.
	 * 
	 * @param event The tick.
	 */
	@EventHandler
	public void onGraphicsTick(GraphicsTick event) {
		for (long window : GraphicsManager.getWindows()) {
			if (!GraphicsManager.tick(window)) {
				EventManager.getInstance()
					.fireEvent(new WindowDestroyed(window));
			}
		}
	}

}
