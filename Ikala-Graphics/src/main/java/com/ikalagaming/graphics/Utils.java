package com.ikalagaming.graphics;

import java.util.List;

/**
 * Graphics related utility methods.
 * 
 * @author Ches Burks
 *
 */
public class Utils {

	/**
	 * Convert a list of floats to an array, since there is no other easy way to
	 * do this in Java.
	 * 
	 * @param list The list of floats.
	 * @return The list as an array.
	 */
	public static float[] listToArray(List<Float> list) {
		if (list == null) {
			return new float[0];
		}
		int size = list.size();
		float[] output = new float[size];
		for (int i = 0; i < size; ++i) {
			output[i] = list.get(i);
		}
		return output;
	}
}
