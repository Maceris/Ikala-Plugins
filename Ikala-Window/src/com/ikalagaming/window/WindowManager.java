package com.ikalagaming.window;

import com.ikalagaming.event.Listener;

import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Tracks and manages the windows.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class WindowManager implements Listener {

	/**
	 * A mapping of heights to windows on that height.
	 */
	private static Map<Integer, ArrayList<Window>> windowTable =
		new ConcurrentSkipListMap<>();

	private static HashMap<Window, Integer> heightMap = new HashMap<>();
	private static HashMap<String, Window> nameMap = new HashMap<>();
	/**
	 * The default/base height for windows.
	 */
	public static final int BASE_HEIGHT = 0;
	/**
	 * A height representing an invalid or non-existent height.
	 */
	public static final int ERROR_HEIGHT = Integer.MIN_VALUE;

	/**
	 * Returns the window mapped to the given name. If none exists, it returns
	 * null.
	 *
	 * @param name The name to look for
	 * @return The window that has the given name.
	 */
	@Synchronized
	public static Window getByName(@NonNull final String name) {
		return WindowManager.nameMap.get(name);
	}

	/**
	 * Returns the height of the given window, or {@link #ERROR_HEIGHT} if that
	 * window is not registered.
	 *
	 * @param window The window to check for.
	 * @return The height of the window or {@link #ERROR_HEIGHT}.
	 */
	@Synchronized
	public static int getHeight(@NonNull final Window window) {
		if (WindowManager.heightMap.containsKey(window)) {
			return WindowManager.heightMap.get(window);
		}
		return WindowManager.ERROR_HEIGHT;
	}

	/**
	 * Returns the name that is mapped to the given window. If none exists, it
	 * returns null. Note that this is slow because it must check each name to
	 * see if it is assigned to the given window, and there isn't really a
	 * guarantee that a window won't have multiple names, so this is just the
	 * first one it finds (assume its a random from the set of possible names,
	 * which is hopefully only 1 name).
	 *
	 * @param window The window to look for.
	 * @return An optional containing the name that maps to that window if it
	 *         exists, otherwise an empty optional.
	 */
	@Synchronized
	public static Optional<String> getName(@NonNull final Window window) {
		return WindowManager.nameMap.entrySet().stream()
			.filter(entry -> window.equals(entry.getValue()))
			.map(Entry<String, Window>::getKey).findFirst();
	}

	/**
	 * Registers a window with a given height. <b>Note:</b> Windows should have
	 * a name associated with them, as otherwise they will be basically
	 * anonymous, and cannot be searched for by name.
	 *
	 * @param window The window to register
	 * @param height The windows height.
	 */
	@Synchronized
	public static void registerWin(@NonNull final Window window,
		final int height) {
		if (WindowManager.heightMap.containsKey(window)) {
			// The window already exists somewhere
			// TODO decide what to do here
			log.warn("Registering a window that already exists");
		}
		else {
			// The window is not registered yet
			WindowManager.heightMap.put(window, height);
			ArrayList<Window> heightEntry =
				WindowManager.windowTable.get(height);
			if (heightEntry == null) {
				// that height doesn't exist, so create one
				heightEntry = new ArrayList<>();
				WindowManager.windowTable.put(height, heightEntry);
			}
			// now there is an entry
			// add the window to the window table
			heightEntry.add(window);
		}
	}

	/**
	 * Registers a window with a given height and name. The name should be
	 * unique to the window.
	 *
	 * @param window The window to register
	 * @param height The windows height.
	 * @param name The name of the window
	 */
	@Synchronized
	public static void registerWin(@NonNull final Window window,
		final int height, @NonNull final String name) {
		boolean containsKey = WindowManager.heightMap.containsKey(window);
		if (containsKey) {
			// The window already exists somewhere
			// TODO decide what to do here
			log.warn("Registering a window ({}) that already exists", name);
		}
		else {
			WindowManager.registerWin(window, height);
			if (WindowManager.nameMap.containsKey(name)) {
				// that name is already registered
				// TODO decide what to do here
				log.warn("Registering a window ({}) that already exists", name);
			}
			else {
				WindowManager.nameMap.put(name, window);
			}
		}
	}

	/**
	 * Updates the height of the window if it currently exists, or sets it if it
	 * does not.
	 *
	 * @param window The window to modify.
	 * @param height The new height to use.
	 */
	@Synchronized
	public static void setHeight(@NonNull final Window window,
		final int height) {
		ArrayList<Window> heightEntry = null;
		if (WindowManager.heightMap.containsKey(window)) {
			final int oldHeight;
			// get the height of the window, swap in the new one

			oldHeight = WindowManager.heightMap.get(window);
			WindowManager.heightMap.put(window, height);

			// grab the height entry map for the old height
			heightEntry = WindowManager.windowTable.get(oldHeight);
			if (heightEntry == null) {
				// the old height doesn't exist, undo changes to height
				// map
				WindowManager.heightMap.put(window, oldHeight);
				return;
			}

			// remove the old mapping
			heightEntry.remove(window);
		}
		else {
			WindowManager.heightMap.put(window, height);
		}
		// grab the height entry map for the new height
		heightEntry = WindowManager.windowTable.get(height);
		if (heightEntry == null) {
			heightEntry = new ArrayList<>();
			WindowManager.windowTable.put(height, heightEntry);
		}
		// remove the old mapping
		heightEntry.add(window);

	}

	/**
	 * Sets the name of the window if it currently exists
	 *
	 * @param window The window to modify.
	 * @param name The new name to use.
	 */
	@Synchronized
	public static void setName(@NonNull final Window window,
		@NonNull final String name) {
		if (!WindowManager.heightMap.containsKey(window)) {
			return;
		}
		String oldName = null;
		for (Entry<String, Window> entry : WindowManager.nameMap.entrySet()) {
			if (entry.getValue() == window) {
				// only should be assigned once
				oldName = entry.getKey();
				break;
			}
		}
		if (oldName != null) {
			WindowManager.nameMap.remove(oldName);
		}
		WindowManager.nameMap.put(name, window);
	}

	/**
	 * Unregisters and clears up all windows.
	 */
	@Synchronized
	public static void unregisterAll() {
		List<Window> allWindows = new ArrayList<>();
		WindowManager.windowTable
			.forEach((height, list) -> allWindows.addAll(list));
		for (Window win : allWindows) {
			WindowManager.unregisterWin(win);
			win.delete();
		}
	}

	/**
	 * Unregisters the given window
	 *
	 * @param window The window to unregister.
	 */
	@Synchronized
	public static void unregisterWin(@NonNull final Window window) {
		if (WindowManager.heightMap.containsKey(window)) {
			int height = WindowManager.heightMap.get(window);

			// grab the height entry map for the given height
			ArrayList<Window> heightEntry =
				WindowManager.windowTable.get(height);
			if (heightEntry != null) {
				// remove the window from the height table
				heightEntry.remove(window);
			}
			// remove the window from the height map
			WindowManager.heightMap.remove(window);
		}

		// remove the window from the name map
		WindowManager.nameMap.values().remove(window);
		window.delete();
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private WindowManager() {}
}
