package com.ikalagaming.language;

import lombok.NonNull;

/**
 * An action that has been parsed from a line of input.
 *
 * @author Ches Burks
 * @param actor The actor that is taking the action.
 * @param action The verb being performed.
 * @param noun The target noun, the direct object. Might not be there if the
 *            verb has no target.
 * @param second A second noun, an indirect object. Might not be there if the
 *            verb only has one target.
 *
 */
public record Action(@NonNull String actor, @NonNull String action, String noun,
	String second) {

}
