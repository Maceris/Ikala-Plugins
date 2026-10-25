package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A reusable color theme, by palette channel, that designs refer to or copy.
 *
 * @param id This palette's ID.
 * @param colors Colors by channel.
 * @author Ches Burks
 */
@Doc("A reusable color theme, by palette channel, that designs refer to or copy.")
public record Palette(
        @Doc("This palette's ID, like \"ikala:palette/hero_blue\".") Id id,
        @Doc("Colors by channel, like {\"primary\": \"#2a4d8f\"}.") Map<String, Color> colors)
        implements Definition {

    /** The field for the colors. */
    public static final String FIELD_COLORS = "colors";

    /** The channels every plan has. Plans can add more. */
    public static final Set<String> STANDARD_CHANNELS =
            Set.of("skin", "eyes", "hair", "primary", "secondary", "tertiary", "accent");

    @Override
    public void check(Checks checks) {
        for (String channel : colors.keySet()) {
            if (STANDARD_CHANNELS.contains(channel)) {
                continue;
            }
            boolean planChannel =
                    checks.all(BodyPlan.class).stream()
                            .anyMatch(plan -> plan.channels().contains(channel));
            if (!planChannel) {
                checks.warning(
                        JsonPath.field(JsonPath.at(FIELD_COLORS), channel),
                        "No plan has the channel '"
                                + channel
                                + "'; the standard ones are "
                                + new TreeSet<>(STANDARD_CHANNELS));
            }
        }
    }
}
