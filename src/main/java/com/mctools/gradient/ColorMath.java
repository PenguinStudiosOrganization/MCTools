package com.mctools.gradient;

/**
 * Color space conversions and distance functions.
 * Supports sRGB, CIELAB, OkLAB, and HSL color spaces.
 */
public final class ColorMath {

    // D65 white point for XYZ
    private static final double XN = 0.95047;
    private static final double YN = 1.00000;
    private static final double ZN = 1.08883;

    private ColorMath() {}

    // ─── Hex ↔ RGB ──────────────────────────────────────────────────

    public static int[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0)
                     + hex.charAt(1) + hex.charAt(1)
                     + hex.charAt(2) + hex.charAt(2);
        }
        try {
            return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
            };
        } catch (Exception e) {
            return new int[]{128, 128, 128};
        }
    }

    public static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x",
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }

    // ─── sRGB linearization ─────────────────────────────────────────

    private static double linearize(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // ─── RGB → CIELAB ───────────────────────────────────────────────

    public static double[] rgbToLab(int r, int g, int b) {
        double rl = linearize(r / 255.0);
        double gl = linearize(g / 255.0);
        double bl = linearize(b / 255.0);

        double x = (rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375) / XN;
        double y = (rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750) / YN;
        double z = (rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041) / ZN;

        x = labF(x);
        y = labF(y);
        z = labF(z);

        return new double[]{
            116.0 * y - 16.0,   // L
            500.0 * (x - y),    // a
            200.0 * (y - z)     // b
        };
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t) + 16.0 / 116.0;
    }

    // ─── RGB → OkLAB ────────────────────────────────────────────────

    public static double[] rgbToOklab(int r, int g, int b) {
        double rl = linearize(r / 255.0);
        double gl = linearize(g / 255.0);
        double bl = linearize(b / 255.0);

        double l = 0.4122214708 * rl + 0.5363325363 * gl + 0.0514459929 * bl;
        double m = 0.2119034982 * rl + 0.6806995451 * gl + 0.1073969566 * bl;
        double s = 0.0883024619 * rl + 0.2817188376 * gl + 0.6299787005 * bl;

        double lc = Math.cbrt(l);
        double mc = Math.cbrt(m);
        double sc = Math.cbrt(s);

        return new double[]{
            0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc,
            1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc,
            0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc
        };
    }

    // ─── RGB → HSL ──────────────────────────────────────────────────
    // Returns {H in degrees (mapped to L slot), S*100 (a slot), L*100 (b slot)}
    // to keep the same 3-component format as LAB/OkLAB

    public static double[] rgbToHsl(int r, int g, int b) {
        double rd = r / 255.0, gd = g / 255.0, bd = b / 255.0;
        double max = Math.max(rd, Math.max(gd, bd));
        double min = Math.min(rd, Math.min(gd, bd));
        double h, s, l = (max + min) / 2.0;

        if (max == min) {
            h = s = 0;
        } else {
            double d = max - min;
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
            if (max == rd) {
                h = ((gd - bd) / d + (gd < bd ? 6 : 0)) / 6.0;
            } else if (max == gd) {
                h = ((bd - rd) / d + 2) / 6.0;
            } else {
                h = ((rd - gd) / d + 4) / 6.0;
            }
        }
        return new double[]{h * 360.0, s * 100.0, l * 100.0};
    }

    // ─── RGB passthrough (for RGB interpolation mode) ────────────────

    public static double[] rgbToRgbSpace(int r, int g, int b) {
        return new double[]{r, g, b};
    }

    // ─── Distance / Hue functions ───────────────────────────────────

    public static double deltaE(double[] lab1, double[] lab2) {
        double dL = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dL * dL + da * da + db * db);
    }

    public static double getHueAngle(double[] lab) {
        return Math.toDegrees(Math.atan2(lab[2], lab[1]));
    }

    public static double hueDifference(double h1, double h2) {
        double diff = Math.abs(h1 - h2);
        if (diff > 180) diff = 360 - diff;
        return diff;
    }

    public static double getChroma(double[] lab) {
        return Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
    }

    // ─── Perceived luminance ────────────────────────────────────────

    /** ITU-R BT.709 relative luminance (0-1) */
    public static double getPerceivedLuminance(int r, int g, int b) {
        return 0.2126 * (r / 255.0) + 0.7152 * (g / 255.0) + 0.0722 * (b / 255.0);
    }

    // ─── Color mixing ───────────────────────────────────────────────

    public static int[] mixRgb(int[] c1, int[] c2, double t) {
        return new int[]{
            (int) Math.round(c1[0] + (c2[0] - c1[0]) * t),
            (int) Math.round(c1[1] + (c2[1] - c1[1]) * t),
            (int) Math.round(c1[2] + (c2[2] - c1[2]) * t)
        };
    }

    public static double[] mixInColorSpace(double[] c1, double[] c2, double t) {
        return new double[]{
            c1[0] + (c2[0] - c1[0]) * t,
            c1[1] + (c2[1] - c1[1]) * t,
            c1[2] + (c2[2] - c1[2]) * t
        };
    }

    // ─── Grayscale detection ────────────────────────────────────────

    public static boolean isGrayscaleColor(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int range = max - min;

        // Pink/magenta check
        boolean isPinkish = r > g + 5 && Math.abs(r - b) < 30;
        if (isPinkish && range > 10) return false;

        double avg = (r + g + b) / 3.0;
        double rDiff = Math.abs(r - avg);
        double gDiff = Math.abs(g - avg);
        double bDiff = Math.abs(b - avg);

        double brightness = avg;
        if (brightness < 60) {
            if (rDiff > 8 || gDiff > 8 || bDiff > 8) return false;
            return range < 15;
        }
        if (brightness < 120) {
            if (rDiff > 10 || gDiff > 10 || bDiff > 10) return false;
            return range < 20;
        }
        if (rDiff > 12 || gDiff > 12 || bDiff > 12) return false;
        return range < 25;
    }

    public static double getColorSaturation(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == 0) return 0;
        return (double)(max - min) / max;
    }

    // ─── Color space converter interface ────────────────────────────

    @FunctionalInterface
    public interface ColorSpaceConverter {
        double[] convert(int r, int g, int b);
    }

    public static ColorSpaceConverter getConverter(String mode) {
        return switch (mode.toLowerCase()) {
            case "lab" -> ColorMath::rgbToLab;
            case "rgb" -> ColorMath::rgbToRgbSpace;
            case "hsl" -> ColorMath::rgbToHsl;
            default -> ColorMath::rgbToOklab; // "oklab" is default
        };
    }
}
