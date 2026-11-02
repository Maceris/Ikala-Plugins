package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Art a design can wear projected onto a body plan's decal place, like an emblem on the chest (see
 * {@link BodyPlan.DecalPlace} and {@link DecalUse}). Its image is a mask like a tint mask: red
 * takes the first color the design picks, green the second, and alpha is how much it covers.
 *
 * @param id This decal's ID.
 * @param image The art, a PNG relative to its namespace's content folder.
 * @param defaults The colors red and green show while the design leaves their channels uncolored,
 *     by mask channel ("r", "g").
 * @param tags Tags editors filter by.
 * @author Ches Burks
 */
@Doc(
        "Art a design can wear projected onto a decal place, like an emblem on the chest. Its"
                + " image is a mask: red takes the first color, green the second, alpha covers.")
public record Decal(
        @Doc("This decal's ID, like \"ikala:decal/star\".") Id id,
        @Doc(
                        "The art, like \"decals/star.png\", relative to its namespace's content"
                                + " folder: red takes the first color, green the second, alpha"
                                + " is how much it covers.")
                String image,
        @Doc(
                        "The colors red and green show while the design leaves their palette"
                                + " channels uncolored, like {\"r\": \"#ffd040\"}.")
                @Optional
                Map<String, Color> defaults,
        @Doc("Tags that editors filter by, like \"emblem\".") @Optional List<String> tags)
        implements Definition {

    /** The field for the image. */
    public static final String FIELD_IMAGE = "image";

    /** The field for the default colors. */
    public static final String FIELD_DEFAULTS = "defaults";

    /** The mask channel the first color takes. */
    public static final String FIRST = "r";

    /** The mask channel the second color takes. */
    public static final String SECOND = "g";

    /** The mask channels that take colors. */
    public static final Set<String> CHANNELS = Set.of(FIRST, SECOND);

    /** What decal images end with. */
    public static final String IMAGE_SUFFIX = ".png";

    @Override
    public void check(Checks checks) {
        if (image.isBlank()) {
            checks.error(JsonPath.at(FIELD_IMAGE), "A decal needs its image");
        } else if (!image.endsWith(IMAGE_SUFFIX)) {
            checks.error(JsonPath.at(FIELD_IMAGE), "Decal images are PNGs, not " + image);
        }
        String defaultsPath = JsonPath.at(FIELD_DEFAULTS);
        for (String mask : defaults.keySet()) {
            if (!CHANNELS.contains(mask)) {
                checks.error(
                        JsonPath.field(defaultsPath, mask),
                        "Decals take colors in \"" + FIRST + "\" and \"" + SECOND + "\" only");
            }
        }
    }
}
