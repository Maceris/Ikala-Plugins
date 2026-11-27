package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.CoreNodes.type;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;

import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * A one-line formula, evaluated per pixel, for whatever the library lacks, like UE's Custom node.
 * It reads the inputs {@code a}, {@code b}, {@code c} and {@code d}; {@code x} and {@code y}, the
 * pixel's middle as a part of the tile; {@code px}, {@code py}, {@code w} and {@code h} in pixels;
 * and {@code pi}. It has {@code + - * / % ^}, comparisons giving 1 or 0, {@code cond ? a : b}, and
 * the functions sin, cos, tan, asin, acos, atan, atan2, abs, sign, floor, ceil, round, fract, sqrt,
 * exp, log, pow, min, max, clamp, lerp (or mix), step and smoothstep. The text is parsed once per
 * evaluation into a tree.
 */
public final class Expression {
    /** The node's type ID. */
    public static final String TYPE = "math/expression";

    /** The formula's parameter. */
    static final String FORMULA = "formula";

    /** The variables a formula reads, by name, to their slot. */
    private static final Map<String, Integer> VARIABLES =
            Map.of(
                    "a", 0, "b", 1, "c", 2, "d", 3, "x", 4, "y", 5, "px", 6, "py", 7, "w", 8, "h",
                    9);

    /** How many variable slots there are. */
    private static final int SLOTS = 10;

    /** A part of a parsed formula. */
    @FunctionalInterface
    interface Term {
        /**
         * Its value.
         *
         * @param vars The variables, by slot.
         * @return The value.
         */
        double value(double[] vars);
    }

    /** The text being parsed. */
    private final String text;

    /** Where parsing is. */
    private int at;

    /**
     * Start parsing.
     *
     * @param text The formula.
     */
    private Expression(String text) {
        this.text = text;
    }

    /**
     * Parse a formula.
     *
     * @param text The formula.
     * @return The tree.
     * @throws TextureNode.NodeException If it can't be parsed, naming where.
     */
    static Term parse(@NonNull String text) {
        final Expression parser = new Expression(text);
        final Term term = parser.ternary();
        parser.skipSpace();
        if (parser.at < text.length()) {
            throw parser.error("Unexpected '" + text.charAt(parser.at) + "'");
        }
        return term;
    }

    /**
     * Add the Expression node to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        library.register(
                type(
                        TYPE,
                        "Expression",
                        CoreNodes.MATH,
                        List.of(
                                pin("a", SCALAR),
                                pin("b", SCALAR),
                                pin("c", SCALAR),
                                pin("d", SCALAR)),
                        List.of(pin("result", SCALAR)),
                        List.of(param(FORMULA, "a"))),
                context -> {
                    final Term term = parse(context.text(FORMULA));
                    final Plane[] inputs = {
                        context.input("a"),
                        context.input("b"),
                        context.input("c"),
                        context.input("d")
                    };
                    final int width = context.getWidth();
                    final int height = context.getHeight();
                    final double[] vars = new double[SLOTS];
                    vars[8] = width;
                    vars[9] = height;
                    final Plane out = context.plane(1);
                    for (int y = 0; y < height; ++y) {
                        for (int x = 0; x < width; ++x) {
                            for (int i = 0; i < 4; ++i) {
                                vars[i] = inputs[i].get(x, y, 0);
                            }
                            vars[4] = (x + 0.5) / width;
                            vars[5] = (y + 0.5) / height;
                            vars[6] = x;
                            vars[7] = y;
                            final double value = term.value(vars);
                            out.set(x, y, 0, Double.isFinite(value) ? (float) value : 0);
                        }
                    }
                    context.output("result", out);
                });
        return library;
    }

    /**
     * A parse error at the current place.
     *
     * @param message What is wrong.
     * @return The exception.
     */
    private TextureNode.NodeException error(String message) {
        return new TextureNode.NodeException(message + " at character " + (at + 1));
    }

    /** Skip spaces. */
    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            ++at;
        }
    }

    /**
     * Take text if it comes next.
     *
     * @param token The text.
     * @return True if taken.
     */
    private boolean take(String token) {
        skipSpace();
        if (text.startsWith(token, at)) {
            at += token.length();
            return true;
        }
        return false;
    }

    /**
     * {@code a ? b : c}, the loosest.
     *
     * @return The term.
     */
    private Term ternary() {
        final Term condition = comparison();
        if (!take("?")) {
            return condition;
        }
        final Term yes = ternary();
        if (!take(":")) {
            throw error("Expected ':'");
        }
        final Term no = ternary();
        return vars -> condition.value(vars) != 0 ? yes.value(vars) : no.value(vars);
    }

    /**
     * Comparisons, giving 1 or 0.
     *
     * @return The term.
     */
    private Term comparison() {
        Term left = sum();
        while (true) {
            final Term a = left;
            if (take("<=")) {
                final Term b = sum();
                left = vars -> a.value(vars) <= b.value(vars) ? 1 : 0;
            } else if (take(">=")) {
                final Term b = sum();
                left = vars -> a.value(vars) >= b.value(vars) ? 1 : 0;
            } else if (take("==")) {
                final Term b = sum();
                left = vars -> a.value(vars) == b.value(vars) ? 1 : 0;
            } else if (take("!=")) {
                final Term b = sum();
                left = vars -> a.value(vars) != b.value(vars) ? 1 : 0;
            } else if (take("<")) {
                final Term b = sum();
                left = vars -> a.value(vars) < b.value(vars) ? 1 : 0;
            } else if (take(">")) {
                final Term b = sum();
                left = vars -> a.value(vars) > b.value(vars) ? 1 : 0;
            } else {
                return left;
            }
        }
    }

    /**
     * Sums and differences.
     *
     * @return The term.
     */
    private Term sum() {
        Term left = product();
        while (true) {
            final Term a = left;
            if (take("+")) {
                final Term b = product();
                left = vars -> a.value(vars) + b.value(vars);
            } else if (take("-")) {
                final Term b = product();
                left = vars -> a.value(vars) - b.value(vars);
            } else {
                return left;
            }
        }
    }

    /**
     * Products, quotients and remainders.
     *
     * @return The term.
     */
    private Term product() {
        Term left = unary();
        while (true) {
            final Term a = left;
            if (take("*")) {
                final Term b = unary();
                left = vars -> a.value(vars) * b.value(vars);
            } else if (take("/")) {
                final Term b = unary();
                left = vars -> a.value(vars) / b.value(vars);
            } else if (take("%")) {
                final Term b = unary();
                left =
                        vars -> {
                            final double m = b.value(vars);
                            final double v = a.value(vars);
                            return v - m * Math.floor(v / m);
                        };
            } else {
                return left;
            }
        }
    }

    /**
     * A sign in front.
     *
     * @return The term.
     */
    private Term unary() {
        if (take("-")) {
            final Term inner = unary();
            return vars -> -inner.value(vars);
        }
        if (take("+")) {
            return unary();
        }
        return power();
    }

    /**
     * Powers, binding right to left.
     *
     * @return The term.
     */
    private Term power() {
        final Term base = atom();
        if (take("^")) {
            final Term exponent = unary();
            return vars -> Math.pow(base.value(vars), exponent.value(vars));
        }
        return base;
    }

    /**
     * A number, variable, function call or bracketed term.
     *
     * @return The term.
     */
    private Term atom() {
        skipSpace();
        if (take("(")) {
            final Term inner = ternary();
            if (!take(")")) {
                throw error("Expected ')'");
            }
            return inner;
        }
        if (at >= text.length()) {
            throw error("Expected a value");
        }
        final char first = text.charAt(at);
        if (Character.isDigit(first) || first == '.') {
            final int start = at;
            while (at < text.length()
                    && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                ++at;
            }
            try {
                final double number = Double.parseDouble(text.substring(start, at));
                return vars -> number;
            } catch (NumberFormatException e) {
                throw error("Not a number");
            }
        }
        if (!Character.isLetter(first)) {
            throw error("Unexpected '" + first + "'");
        }
        final int start = at;
        while (at < text.length() && Character.isLetterOrDigit(text.charAt(at))) {
            ++at;
        }
        final String name = text.substring(start, at);
        if (take("(")) {
            return call(name, start);
        }
        if ("pi".equals(name)) {
            return vars -> Math.PI;
        }
        final Integer slot = VARIABLES.get(name);
        if (slot == null) {
            at = start;
            throw error("No variable called " + name);
        }
        final int index = slot;
        return vars -> vars[index];
    }

    /**
     * A function call, after its opening bracket.
     *
     * @param name The function.
     * @param start Where its name started, for errors.
     * @return The term.
     */
    private Term call(String name, int start) {
        final java.util.List<Term> args = new java.util.ArrayList<>();
        if (!take(")")) {
            do {
                args.add(ternary());
            } while (take(","));
            if (!take(")")) {
                throw error("Expected ')' or ','");
            }
        }
        final int count = args.size();
        final Term a = count > 0 ? args.get(0) : null;
        final Term b = count > 1 ? args.get(1) : null;
        final Term c = count > 2 ? args.get(2) : null;
        final int wanted =
                switch (name) {
                    case "sin",
                                    "cos",
                                    "tan",
                                    "asin",
                                    "acos",
                                    "atan",
                                    "abs",
                                    "sign",
                                    "floor",
                                    "ceil",
                                    "round",
                                    "fract",
                                    "sqrt",
                                    "exp",
                                    "log" ->
                            1;
                    case "atan2", "pow", "min", "max", "step" -> 2;
                    case "clamp", "lerp", "mix", "smoothstep" -> 3;
                    default -> {
                        at = start;
                        throw error("No function called " + name);
                    }
                };
        if (count != wanted) {
            at = start;
            throw error(name + " takes " + wanted + (wanted == 1 ? " value" : " values"));
        }
        return switch (name) {
            case "sin" -> vars -> Math.sin(a.value(vars));
            case "cos" -> vars -> Math.cos(a.value(vars));
            case "tan" -> vars -> Math.tan(a.value(vars));
            case "asin" -> vars -> Math.asin(a.value(vars));
            case "acos" -> vars -> Math.acos(a.value(vars));
            case "atan" -> vars -> Math.atan(a.value(vars));
            case "abs" -> vars -> Math.abs(a.value(vars));
            case "sign" -> vars -> Math.signum(a.value(vars));
            case "floor" -> vars -> Math.floor(a.value(vars));
            case "ceil" -> vars -> Math.ceil(a.value(vars));
            case "round" -> vars -> Math.rint(a.value(vars));
            case "fract" ->
                    vars -> {
                        final double v = a.value(vars);
                        return v - Math.floor(v);
                    };
            case "sqrt" -> vars -> Math.sqrt(a.value(vars));
            case "exp" -> vars -> Math.exp(a.value(vars));
            case "log" -> vars -> Math.log(a.value(vars));
            case "atan2" -> vars -> Math.atan2(a.value(vars), b.value(vars));
            case "pow" -> vars -> Math.pow(a.value(vars), b.value(vars));
            case "min" -> vars -> Math.min(a.value(vars), b.value(vars));
            case "max" -> vars -> Math.max(a.value(vars), b.value(vars));
            case "step" -> vars -> b.value(vars) >= a.value(vars) ? 1 : 0;
            case "clamp" -> vars -> Math.min(Math.max(a.value(vars), b.value(vars)), c.value(vars));
            case "lerp", "mix" ->
                    vars -> {
                        final double from = a.value(vars);
                        return from + (b.value(vars) - from) * c.value(vars);
                    };
            default ->
                    vars -> {
                        final double low = a.value(vars);
                        final double high = b.value(vars);
                        final double t =
                                Math.min(1, Math.max(0, (c.value(vars) - low) / (high - low)));
                        return t * t * (3 - 2 * t);
                    };
        };
    }
}
