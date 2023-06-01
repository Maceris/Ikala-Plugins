package com.ikalagaming.language.grammar;

import lombok.NonNull;

import java.util.List;

/**
 * Stands for Gender, Number, Animation. Specifies which scenarios a word
 * applies to.
 *
 * @author Ches Burks
 *
 */
public class GNA {
	/**
	 * <p>
	 * An compact encoded version of the GNA based on Inform 6's system.
	 * </p>
	 *
	 *
	 * <p>
	 * The formatting is in the table below, with the following meanings:
	 * </p>
	 *
	 * <ul>
	 * <li>a - Animate</li>
	 * <li>i - Inanimate</li>
	 * <li>s - Singular</li>
	 * <li>p - Plural</li>
	 * <li>m - Masculine</li>
	 * <li>f - Feminine</li>
	 * <li>n - Neuter</li>
	 * </ul>
	 *
	 * <pre>
	 * a     i
	 * s  p  s  p
	 * mfnmfnmfnmfn
	 * 000000000000
	 * </pre>
	 */
	private final short encoded;

	/**
	 * Used when we want any GNA to apply to every scenario.
	 */
	public GNA() {
		this.encoded = (short) 0b111111111111;
	}

	/**
	 * Used when we only have specific scenarios.
	 *
	 * @param gnaList The list of GNAs that this applies to.
	 */
	public GNA(@NonNull List<String> gnaList) {
		short encoding = 0;

		for (String gna : gnaList) {

			encoding |= this.encode(gna);
		}

		this.encoded = encoding;
	}

	/**
	 * Encode into the expected bit. Valid GNAs will have 1 bit set to 1, at the
	 * appropriate place. Invalid ones will be 0. The intent of this is so that
	 * we can or together all the GNAs to reach the final string, and use this
	 * to index into it to see if the right bit is set.
	 *
	 * @param gna The format we use in language specification files.
	 * @return A short with the appropriate bit set.
	 */
	private short encode(@NonNull String gna) {
		if (!this.isValid(gna)) {
			return 0;
		}
		short result = 1;
		if (gna.charAt(0) == 'a') {
			result <<= 6;
		}
		// else inanimate

		if (gna.charAt(1) == 's') {
			result <<= 3;
		}
		// else plural

		switch (gna.charAt(2)) {
			case 'm':
				result <<= 2;
				break;
			case 'f':
				result <<= 1;
				break;
			case 'n':
			default:
				// Do nothing
		}
		return result;
	}

	/**
	 * Checks if the given case is included.
	 *
	 * @param gna The format we use in language specification files.
	 * @return Whether this is one of the cases.
	 */
	public boolean includesCase(@NonNull String gna) {
		if (!this.isValid(gna)) {
			return false;
		}
		return (this.encoded & this.encode(gna)) > 0;
	}

	/**
	 * Check if we have a valid GNA format.
	 *
	 * @param gna The string format we use in the language specifications.
	 * @return Whether the GNA is a valid format.
	 */
	private boolean isValid(@NonNull String gna) {
		return !gna.isBlank() && gna.matches("[ai][sp][mfn]");
	}
}
