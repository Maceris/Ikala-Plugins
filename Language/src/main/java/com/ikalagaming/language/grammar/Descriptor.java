package com.ikalagaming.language.grammar;

import lombok.NonNull;

/**
 * <p>
 * Used for pronouns, and descriptors like articles.
 * </p>
 *
 * The types we use are:
 *
 * <ul>
 * <li>"possessive" - Used for possessive pronouns</li>
 * <li>"definite" - Used for definite articles</li>
 * <li>"indefinite" - Used for indefinite articles</li>
 * <li>null - Used for normal pronouns</li>
 * </ul>
 *
 * The owners we use are:
 *
 * <ul>
 * <li>"player" - Owned by the player</li>
 * <li>"non_player" - Not owned by the player, but still possessive</li>
 * <li>Any pronoun, such as "him" - Owned by the target of said pronoun</li>
 * <li>null - No relevant owner, usually used for articles</li>
 * </ul>
 *
 * @author Ches Burks
 *
 * @param gna The Genders, Numbers, and Animate statuses this applies to.
 * @param type The type of descriptor this is.
 * @param owner The owner that this applies to.
 */
public record Descriptor(@NonNull GNA gna, String type, String owner) {}
