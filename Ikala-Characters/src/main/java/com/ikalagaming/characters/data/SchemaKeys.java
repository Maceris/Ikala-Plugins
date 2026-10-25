package com.ikalagaming.characters.data;

/**
 * JSON Schema keywords and type names, used when generating schemas.
 *
 * @author Ches Burks
 */
public final class SchemaKeys {
    /** The JSON Schema version the schemas use. */
    public static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";

    /** The keyword naming the schema version. */
    public static final String SCHEMA = "$schema";

    /** The keyword holding reusable sub-schemas. */
    public static final String DEFS = "$defs";

    /** The keyword referring to another schema. */
    public static final String REF = "$ref";

    /** What a reference to a reusable sub-schema starts with. */
    public static final String DEFS_REF_PREFIX = "#/" + DEFS + "/";

    /** The keyword for a schema's title. */
    public static final String TITLE = "title";

    /** The keyword for a description. */
    public static final String DESCRIPTION = "description";

    /** The keyword for a value's type. */
    public static final String TYPE = "type";

    /** The keyword for an object's fields. */
    public static final String PROPERTIES = "properties";

    /** The keyword listing an object's required fields. */
    public static final String REQUIRED = "required";

    /** The keyword for fields not listed in the properties. */
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties";

    /** The keyword for an array's items. */
    public static final String ITEMS = "items";

    /** The keyword for the fewest items in an array. */
    public static final String MIN_ITEMS = "minItems";

    /** The keyword for the most items in an array. */
    public static final String MAX_ITEMS = "maxItems";

    /** The keyword listing the allowed values. */
    public static final String ENUM = "enum";

    /** The keyword for a regular expression a string must match. */
    public static final String PATTERN = "pattern";

    /** The keyword for the value used when a field is left out. */
    public static final String DEFAULT = "default";

    /** The keyword requiring at least one of some schemas to match. */
    public static final String ANY_OF = "anyOf";

    /** The keyword requiring all of some schemas to match. */
    public static final String ALL_OF = "allOf";

    /** The keyword requiring exactly one of some schemas to match. */
    public static final String ONE_OF = "oneOf";

    /** The string type. */
    public static final String TYPE_STRING = "string";

    /** The number type. */
    public static final String TYPE_NUMBER = "number";

    /** The whole number type. */
    public static final String TYPE_INTEGER = "integer";

    /** The boolean type. */
    public static final String TYPE_BOOLEAN = "boolean";

    /** The object type. */
    public static final String TYPE_OBJECT = "object";

    /** The array type. */
    public static final String TYPE_ARRAY = "array";

    private SchemaKeys() {}
}
