package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Report;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * A decal a design wears: which art, the plan's place it goes on, and the palette channel each of
 * its colors takes.
 *
 * @param decal The art.
 * @param place The plan's decal place, like "chest".
 * @param channels The palette channel each mask channel ("r", "g") takes; a channel left out shows
 *     the decal's default color.
 * @author Ches Burks
 */
@Doc("A decal a design wears: the art, where it goes, and the palette channels its colors take.")
public record DecalUse(
        @Doc("The art, like \"ikala:decal/star\".") Id decal,
        @Doc("The plan's decal place, like \"chest\".") String place,
        @Doc(
                        "The palette channel each of its colors takes, like {\"r\": \"accent\","
                                + " \"g\": \"primary\"}. One left out shows the decal's default.")
                @Optional
                Map<String, String> channels) {

    /** The field for the decal. */
    public static final String FIELD_DECAL = "decal";

    /** The field for the place. */
    public static final String FIELD_PLACE = "place";

    /** The field for the channels. */
    public static final String FIELD_CHANNELS = "channels";

    /**
     * Check the decal against its plan.
     *
     * @param body The plan.
     * @param find Finds a decal by ID and the path it is at, reporting it if missing.
     * @param report Where problems go.
     * @param path Where the decal use is.
     */
    public void check(
            BodyPlan body, BiFunction<Id, String, Decal> find, Report report, String path) {
        find.apply(decal, JsonPath.field(path, FIELD_DECAL));
        if (body.decalPlace(place) == null) {
            report.error(
                    JsonPath.field(path, FIELD_PLACE),
                    "The plan has no decal place '" + place + "'");
        }
        String channelsPath = JsonPath.field(path, FIELD_CHANNELS);
        channels.forEach(
                (mask, channel) -> {
                    String at = JsonPath.field(channelsPath, mask);
                    if (!Decal.CHANNELS.contains(mask)) {
                        report.error(
                                at,
                                "Decals take colors in \""
                                        + Decal.FIRST
                                        + "\" and \""
                                        + Decal.SECOND
                                        + "\" only");
                    } else if (!body.hasChannel(channel)) {
                        report.error(at, "The plan has no palette channel '" + channel + "'");
                    }
                });
    }
}
