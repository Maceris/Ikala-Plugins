package com.ikalagaming.factory.quest;

import lombok.NonNull;

/**
 * Specifies a single requirement for a quest to be completed.
 *
 * @author Ches Burks
 * @param type The type of requirement.
 * @param value The string value of the requirement. The full name of a block,
 *            item, or entity, (unlocalized) text to show next to a checkbox, or
 *            details about the location.
 */
public record Requirement(@NonNull RequirementType type,
	@NonNull String value) {}