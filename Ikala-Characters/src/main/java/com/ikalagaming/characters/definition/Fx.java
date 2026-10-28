package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import java.util.List;

/**
 * An effect characters can wear, like a hand flame or an aura: a graphics particle effect file,
 * which parts and designs place on a bone or socket with {@link FxUse}. The graphics plugin owns
 * what effects can do; this only names one for characters to use.
 *
 * @param id This effect's ID.
 * @param effect The graphics particle effect file, relative to its namespace's content folder.
 * @param tags Tags editors filter by.
 * @author Ches Burks
 */
@Doc(
        "An effect characters can wear, like a hand flame or an aura: a graphics particle effect"
                + " file that parts and designs place on a bone or socket.")
public record Fx(
        @Doc("This effect's ID, like \"ikala:fx/hand_flame\".") Id id,
        @Doc(
                        "The graphics particle effect file, like \"effects/flame.effect.yml\","
                                + " relative to its namespace's content folder.")
                String effect,
        @Doc("Tags that editors filter by, like \"aura\".") @Optional List<String> tags)
        implements Definition {

    /** The field for the effect file. */
    public static final String FIELD_EFFECT = "effect";

    /** What graphics particle effect files end with. */
    public static final String EFFECT_SUFFIX = ".effect.yml";

    @Override
    public void check(Checks checks) {
        if (effect.isBlank()) {
            checks.error(JsonPath.at(FIELD_EFFECT), "An effect needs its file");
        } else if (!effect.endsWith(EFFECT_SUFFIX)) {
            checks.warning(
                    JsonPath.at(FIELD_EFFECT),
                    "Particle effect files are named *" + EFFECT_SUFFIX + ", not " + effect);
        }
    }
}
