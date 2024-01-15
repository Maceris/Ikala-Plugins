package com.ikalagaming.factory.quest;

import lombok.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a quest for players to complete.
 *
 * @author Ches Burks
 *
 * @param name The name of the quest, in unlocalized form.
 * @param description The description of the quest, in unlocalized form.
 * @param main Whether this is a main quest.
 * @param tab Which tab of the quest book to show this on, in unlocalized form.
 * @param prerequisites The prerequisites for this quest to be completed. Null
 *            if there are no prerequisites.
 * @param repeatable Whether this quest can be completed multiple times.
 * @param autoClaim Whether this quest automatically claims the rewards for the
 *            player.
 * @param requirements The requirements for the quest to be considered complete.
 * @param rewards The rewards for a quest once completed. Null if there are no
 *            rewards.
 */
public record Quest(@NonNull String name, @NonNull String description,
	boolean main, @NonNull String tab, Prerequisites prerequisites,
	boolean repeatable, boolean autoClaim, @NonNull Requirement[] requirements,
	Reward[] rewards) {

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}

		Quest other = (Quest) o;
		return this.name.equals(other.name());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public String toString() {
		return String.format(
			"Quest[name=%s, description=%s, tab=%s,"
				+ " prerequisites=%s, repeatable=%b, "
				+ "autoClaim=%b, requirements=%s, rewards=%s]",
			this.name, this.description, this.tab,
			this.prerequisites.toString(), this.repeatable, this.autoClaim,
			Arrays.toString(this.requirements), Arrays.toString(this.rewards));
	}
}
