package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How another skeleton's bones map onto a rig, so meshes and clips made for it can be imported.
 * Several source bones can map to one rig bone, which merges their weights, like four fingers into
 * one mitten chain.
 *
 * @param id This map's ID.
 * @param rig The rig it maps onto.
 * @param source A description of the source skeleton.
 * @param bones Rig bones by source bone.
 * @param drop Source bones that are left out.
 * @param scale How much to scale the source by.
 * @param height The standing height to scale the source to.
 * @author Ches Burks
 */
@Doc(
        "How another skeleton's bones map onto a rig, so its meshes and clips can be imported."
                + " Several source bones can map to one rig bone, which merges their weights.")
public record RigMap(
        @Doc("This map's ID, like \"ikala:rig_map/rpg_human\".") Id id,
        @Doc("The rig it maps onto.") Id rig,
        @Doc("A description of the source skeleton, for people.") @Optional String source,
        @Doc("Rig bones by source bone, like {\"palm_L\": \"hand.L\"}.") Map<String, String> bones,
        @Doc("Source bones that are left out. Every source bone is mapped or dropped.") @Optional
                List<String> drop,
        @Doc("How much to scale the source by, when height isn't given.") @Default("1") float scale,
        @Doc("The standing height in meters to scale the source to, which overrides scale.")
                @Optional
                Float height)
        implements Definition {

    /** The field for the rig it maps onto. */
    public static final String FIELD_RIG = "rig";

    /** The field for the scale. */
    public static final String FIELD_SCALE = "scale";

    /** The field for the height. */
    public static final String FIELD_HEIGHT = "height";

    /** The field for the bone mapping. */
    public static final String FIELD_BONES = "bones";

    @Override
    public void check(Checks checks) {
        Rig target = checks.require(Rig.class, rig, JsonPath.at(FIELD_RIG));
        if (scale <= 0) {
            checks.error(JsonPath.at(FIELD_SCALE), "The scale must be above 0");
        }
        if (height != null && height <= 0) {
            checks.error(JsonPath.at(FIELD_HEIGHT), "The height must be above 0");
        }
        Set<String> dropped = new HashSet<>(drop);
        bones.forEach(
                (from, to) -> {
                    if (dropped.contains(from)) {
                        checks.error(
                                JsonPath.field(JsonPath.at(FIELD_BONES), from),
                                "'" + from + "' is also dropped");
                    }
                    if (target != null && target.bone(to) == null) {
                        checks.error(
                                JsonPath.field(JsonPath.at(FIELD_BONES), from),
                                "The rig has no bone named '" + to + "'");
                    }
                });
    }
}
