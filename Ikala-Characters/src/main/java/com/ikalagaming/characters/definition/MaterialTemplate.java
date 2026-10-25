package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Range;

import java.util.Map;

/**
 * A kind of material, like skin, cloth or metal, and the parameters parts and designs can set on
 * it. Its textures (base color, normal, ORM and a tint mask whose channels take palette colors)
 * come with each part's mesh.
 *
 * @param id This template's ID.
 * @param params The parameters it takes.
 * @author Ches Burks
 */
@Doc(
        "A kind of material, like skin, cloth or metal, and the parameters that can be set on it."
                + " Textures, including the tint mask whose channels take palette colors, come with"
                + " each part's mesh.")
public record MaterialTemplate(
        @Doc("This template's ID, like \"ikala:mat/metal\".") Id id,
        @Doc("The parameters it takes, by name, like \"roughness\".") @Optional
                Map<String, Param> params)
        implements Definition {

    /** What a parameter holds. */
    public enum ParamType {
        /** A number. */
        NUMBER,
        /** A color. */
        COLOR,
        /** On or off. */
        BOOLEAN
    }

    /**
     * One parameter.
     *
     * @param type What it holds.
     * @param range For numbers, the values allowed.
     */
    @Doc("One parameter.")
    public record Param(
            @Doc("What it holds.") ParamType type,
            @Doc("For numbers, the values allowed, as [min, max].") @Optional Range range) {
        /** The field for the range. */
        public static final String FIELD_RANGE = "range";
    }

    /** The field for the parameters. */
    public static final String FIELD_PARAMS = "params";

    @Override
    public void check(Checks checks) {
        params.forEach(
                (name, param) -> {
                    if (param.range() != null && param.type() != ParamType.NUMBER) {
                        checks.error(
                                JsonPath.field(
                                        JsonPath.field(JsonPath.at(FIELD_PARAMS), name),
                                        Param.FIELD_RANGE),
                                "Only numbers have a range");
                    }
                });
    }
}
