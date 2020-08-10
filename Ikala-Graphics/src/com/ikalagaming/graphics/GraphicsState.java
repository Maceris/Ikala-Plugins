package com.ikalagaming.graphics;

import java.util.ArrayList;

/**
 * Contains state information for graphics.
 *
 * @author Ches Burks
 *
 */
class GraphicsState {

	private boolean initialized;

	private ArrayList<Long> windows;

	/**
	 * Creates a new graphics state.
	 */
	public GraphicsState() {
		this.setInitialized(false);
		this.windows = new ArrayList<>();
	}

	/**
	 * Add a window to the list of handles.
	 *
	 * @param handle The window handle to add.
	 */
	public void addWindow(long handle) {
		this.windows.add(handle);
	}

	/**
	 * Clear out the window list.
	 */
	public void clearWindows() {
		this.windows.clear();
	}

	/**
	 * Gets the list of window handles.
	 *
	 * @return All windows that are known about.
	 */
	public ArrayList<Long> getWindowHandles() {
		return this.windows;
	}

	/**
	 * Returns true if the graphics has already been initialized.
	 *
	 * @return True if it has, false if not initialized yet.
	 */
	public boolean isInitialized() {
		return this.initialized;
	}

	/**
	 * Removes a window from the list by handle.
	 *
	 * @param handle The window handle to remove.
	 */
	public void removeWindow(long handle) {
		this.windows.remove(new Long(handle));
	}

	/**
	 * Sets the initialized state.
	 *
	 * @param init the initialized to set
	 */
	public void setInitialized(boolean init) {
		this.initialized = init;
	}

}
