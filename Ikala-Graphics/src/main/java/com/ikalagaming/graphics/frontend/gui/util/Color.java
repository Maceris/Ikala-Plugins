package com.ikalagaming.graphics.frontend.gui.util;

import org.joml.Vector4f;

public class Color {

    public static final int WHITE = rgba(255, 255, 255, 255);

    public static int rgba(int r, int g, int b, int a) {
        return intToColor(r, g, b, a);
    }

    public static int rgb(int r, int g, int b) {
        return intToColor(r, g, b, 255);
    }

    public static int rgba(float r, float g, float b, float a) {
        return intToColor(
                (int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f), (int) (a * 255.0f));
    }

    public static int rgb(float r, float g, float b) {
        return intToColor((int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f), 255);
    }

    public static int rgba(String hex) {
        return rgbaToColor(hex);
    }

    public static int rgb(String hex) {
        return rgbToColor(hex);
    }

    public static int rgba(Vector4f color) {
        return rgba(color.x, color.y, color.z, color.w);
    }

    public static int rgb(Vector4f color) {
        return rgb(color.x, color.y, color.z);
    }

    public static int hsla(float h, float s, float l, float a) {
        return hslToColor(h, s, l, a);
    }

    public static int hsl(float h, float s, float l) {
        return hslToColor(h, s, l, 1.0f);
    }

    public static int hsla(int h, int s, int l, int a) {
        return hslToColor(h / 360.0f, s / 100.0f, l / 100.0f, a);
    }

    public static int hsl(int h, int s, int l) {
        return hslToColor(h / 360.0f, s / 100.0f, l / 100.0f, 1.0f);
    }

    private static int intToColor(int r, int g, int b, int a) {
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int floatToColor(float r, float g, float b, float a) {
        return intToColor(
                Math.round(r * 255.0f),
                Math.round(g * 255.0f),
                Math.round(b * 255.0f),
                Math.round(a * 255.0f));
    }

    private static int rgbToColor(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        int a = 255;
        return intToColor(r, g, b, a);
    }

    private static int rgbaToColor(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        int a = Integer.parseInt(hex.substring(7, 9), 16);
        return intToColor(r, g, b, a);
    }

    private static int hslToColor(float h, float s, float l, float a) {
        float r;
        float g;
        float b;
        if (s == 0.0f) {
            r = l;
            g = l;
            b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue2rgb(p, q, h + (1f / 3));
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - (1f / 3));
        }
        return floatToColor(r, g, b, a);
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0.0f) {
            t += 1;
        }
        if (t > 1.0f) {
            t -= 1;
        }
        if (t < (1f / 6)) {
            return p + (q - p) * 6 * t;
        }
        if (t < (1f / 2)) {
            return q;
        }
        if (t < (2f / 3)) {
            return p + (q - p) * ((2f / 3) - t) * 6;
        }
        return p;
    }
}
