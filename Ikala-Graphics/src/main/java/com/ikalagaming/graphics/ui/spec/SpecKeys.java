package com.ikalagaming.graphics.ui.spec;

/** The names used in UI spec files, in one place so the loader, values and builder agree. */
public final class SpecKeys {
    // Top level sections

    /** The surface settings. */
    public static final String SURFACE = "surface";

    /** The template definitions. */
    public static final String TEMPLATES = "templates";

    /** The root node. */
    public static final String CONTENT = "content";

    // Templates

    /** Inside {@link #TEMPLATES}: files of templates to import, next to the spec. */
    public static final String IMPORT = "import";

    /** A template's parameter names. */
    public static final String PARAMS = "params";

    /** A template's node. */
    public static final String NODE = "node";

    /** A node that uses a template names it here. */
    public static final String USE = "use";

    /** The parameter values for a template that is used or repeated. */
    public static final String WITH = "with";

    // Node structure

    /** A node's type. */
    public static final String TYPE = "type";

    /** A node's or surface's ID. */
    public static final String ID = "id";

    /** A node's children. */
    public static final String CHILDREN = "children";

    /** Repeats a template once per list item. */
    public static final String REPEAT = "repeat";

    // Repeats

    /** The list a repeat is over. */
    public static final String LIST = "list";

    /** The name each item is known by. */
    public static final String AS = "as";

    /** The template a repeat makes for each item. */
    public static final String TEMPLATE = "template";

    /** Text that identifies an item, so its node is kept when the list changes. */
    public static final String KEY = "key";

    // Properties of nodes and surfaces

    /** Where a node or surface sits in its parent. */
    public static final String ANCHORS = "anchors";

    /** How the width is decided. */
    public static final String WIDTH = "width";

    /** How the height is decided. */
    public static final String HEIGHT = "height";

    /** Space between the edges and the content. */
    public static final String PADDING = "padding";

    /** The font size. */
    public static final String FONT_SIZE = "fontSize";

    /** Whether a node is shown. */
    public static final String VISIBLE = "visible";

    /** Style classes. */
    public static final String CLASSES = "classes";

    /** An inline style. */
    public static final String STYLE = "style";

    // Surface-only properties

    /** How the surface stacks with other windows. */
    public static final String LAYER = "layer";

    /** Whether the user can move the surface. */
    public static final String MOVABLE = "movable";

    /** Whether the surface's background is left out. */
    public static final String TRANSPARENT = "transparent";

    // Sizes

    /** As big as the content, as a size or a size map key. */
    public static final String FIT = "fit";

    /** A share of the leftover space, as a size or a size map key. */
    public static final String GROW = "grow";

    /** The smallest size. */
    public static final String MIN = "min";

    /** The largest size. */
    public static final String MAX = "max";

    // Anchors

    /** Fill the parent, as a preset or with a margin. */
    public static final String FILL = "fill";

    /** Pin to a point of the parent. */
    public static final String AT = "at";

    /** Move a pinned node. */
    public static final String OFFSET = "offset";

    /** Anchor preset: centered. */
    public static final String CENTER = "center";

    /** Anchor preset: top left corner. */
    public static final String TOP_LEFT = "top-left";

    /** Anchor preset: middle of the top edge. */
    public static final String TOP = "top";

    /** Anchor preset: top right corner. */
    public static final String TOP_RIGHT = "top-right";

    /** Anchor preset: middle of the left edge. */
    public static final String LEFT = "left";

    /** Anchor preset: middle of the right edge. */
    public static final String RIGHT = "right";

    /** Anchor preset: bottom left corner. */
    public static final String BOTTOM_LEFT = "bottom-left";

    /** Anchor preset: middle of the bottom edge. */
    public static final String BOTTOM = "bottom";

    /** Anchor preset: bottom right corner. */
    public static final String BOTTOM_RIGHT = "bottom-right";

    /**
     * The path of a key under a path, for messages, like {@code surface.width}.
     *
     * @param path The path.
     * @param key The key.
     * @return The combined path.
     */
    static String at(String path, String key) {
        return path + "." + key;
    }

    /**
     * The message for a missing key, like {@code content: missing 'id'}.
     *
     * @param path Where the key should be.
     * @param key The key.
     * @return The message.
     */
    static String missing(String path, String key) {
        return path + ": missing '" + key + "'";
    }

    /** Constants only. */
    private SpecKeys() {}
}
