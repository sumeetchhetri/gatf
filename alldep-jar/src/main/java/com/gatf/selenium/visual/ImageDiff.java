/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.selenium.visual;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M9: pixel comparison of two screenshots, pure Java over {@link BufferedImage} (no native or extra dependency).
 * <p>
 * The per-pixel test follows the pixelmatch approach (as Playwright's {@code toHaveScreenshot} does): the colour
 * distance is measured in YIQ space, so {@code threshold} (0..1, default 0.2) is perceptual, and pixels that differ
 * only because of anti-aliasing are detected from their neighbourhood and not counted (unless {@code includeAA}).
 * Masked rectangles are ignored. The diff image is the expected image faded to grey, with differing pixels in red,
 * anti-aliasing in yellow and masks in magenta.
 */
public final class ImageDiff {

    /** Comparison settings. */
    public static final class Options {
        /** Per-pixel colour threshold, 0 (exact) .. 1 (anything matches). */
        public double threshold = 0.2;
        /** Differing pixels allowed: an absolute count (when {@link #maxDiffRatio} is negative). */
        public long maxDiffPixels = 0;
        /** Differing pixels allowed as a fraction of all pixels (0..1); negative = use {@link #maxDiffPixels}. */
        public double maxDiffRatio = -1;
        /** Count anti-aliased pixels as differences. */
        public boolean includeAA = false;
        /** Regions to ignore, in image pixels. */
        public final List<Rectangle> masks = new ArrayList<Rectangle>();

        public Options threshold(double t) {
            if (!(t >= 0 && t <= 1)) throw new IllegalArgumentException("threshold must be between 0 and 1, got " + t);
            this.threshold = t;
            return this;
        }

        /** {@code "25"} = 25 pixels, {@code "0.5%"} = 0.5% of the pixels; null/blank = none allowed. */
        public Options maxDiff(String spec) {
            if (spec == null || spec.trim().isEmpty()) return this;
            String s = spec.trim();
            try {
                if (s.endsWith("%")) {
                    double pct = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                    if (pct < 0 || pct > 100) throw new NumberFormatException();
                    maxDiffRatio = pct / 100.0;
                } else {
                    long n = Long.parseLong(s);
                    if (n < 0) throw new NumberFormatException();
                    maxDiffPixels = n;
                    maxDiffRatio = -1;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("maxdiff expects a pixel count or a percentage (e.g. 25 or 0.5%), got '" + spec + "'");
            }
            return this;
        }

        public Options includeAA(boolean b) {
            this.includeAA = b;
            return this;
        }

        public Options mask(Rectangle r) {
            if (r != null && r.width > 0 && r.height > 0) masks.add(r);
            return this;
        }
    }

    /** Outcome of one comparison. */
    public static final class Result {
        public final long diffPixels, totalPixels;
        public final boolean sizeMismatch, passed;
        /** The diff image; null when the images are identical (nothing to show). */
        public final BufferedImage diff;
        public final String message;

        Result(long diffPixels, long totalPixels, boolean sizeMismatch, boolean passed, BufferedImage diff, String message) {
            this.diffPixels = diffPixels;
            this.totalPixels = totalPixels;
            this.sizeMismatch = sizeMismatch;
            this.passed = passed;
            this.diff = diff;
            this.message = message;
        }

        public double ratio() { return totalPixels == 0 ? 0 : (double) diffPixels / totalPixels; }
    }

    private static final int RED = 0xffff0000, YELLOW = 0xffffff00, MAGENTA = 0xffff00ff;

    private ImageDiff() {}

    public static Result compare(BufferedImage expected, BufferedImage actual, Options o) {
        int ew = expected.getWidth(), eh = expected.getHeight(), aw = actual.getWidth(), ah = actual.getHeight();
        int w = Math.max(ew, aw), h = Math.max(eh, ah);
        int[] e = argb(expected, w, h), a = argb(actual, w, h);
        long total = (long) w * h;
        boolean sizeMismatch = ew != aw || eh != ah;
        if (!sizeMismatch && o.masks.isEmpty() && java.util.Arrays.equals(e, a)) {
            return new Result(0, total, false, true, null, "images match");   // fast path: identical pixels, no diff image
        }
        boolean[] masked = new boolean[w * h];
        for (Rectangle r : o.masks) {
            for (int y = Math.max(0, r.y); y < Math.min(h, r.y + r.height); y++) {
                for (int x = Math.max(0, r.x); x < Math.min(w, r.x + r.width); x++) masked[y * w + x] = true;
            }
        }
        double maxDelta = 35215 * o.threshold * o.threshold;
        int[] out = new int[w * h];   // filled here, copied into the image once (per-pixel setRGB is slow on full pages)
        long diff = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = y * w + x;
                if (masked[p]) {
                    out[p] = blend(gray(e[p]), MAGENTA, 0.35);
                    continue;
                }
                boolean outside = x >= Math.min(ew, aw) || y >= Math.min(eh, ah);   // only one image has this pixel
                double delta = outside ? Double.MAX_VALUE : colorDelta(e[p], a[p], false);
                if (Math.abs(delta) > maxDelta) {
                    if (!outside && !o.includeAA && (antialiased(e, x, y, w, h, a) || antialiased(a, x, y, w, h, e))) {
                        out[p] = YELLOW;
                    } else {
                        out[p] = RED;
                        diff++;
                    }
                } else {
                    out[p] = gray(e[p]);
                }
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        boolean within = o.maxDiffRatio >= 0 ? (double) diff / total <= o.maxDiffRatio : diff <= o.maxDiffPixels;
        boolean passed = !sizeMismatch && within;
        String msg;
        if (sizeMismatch) {
            msg = String.format(Locale.ROOT, "size differs: expected %dx%d, actual %dx%d", ew, eh, aw, ah);
        } else if (diff == 0) {
            msg = "images match";
        } else {
            msg = String.format(Locale.ROOT, "%d pixel(s) differ (%.3f%% of %dx%d)%s", diff, 100.0 * diff / total, w, h,
                    passed ? ", within the allowed difference" : "");
        }
        return new Result(diff, total, sizeMismatch, passed, img, msg);
    }

    // ---- pixelmatch-style colour metric ----------------------------------------------------------------------------

    /** Pixels as ARGB, padded with transparent pixels to w x h. */
    static int[] argb(BufferedImage img, int w, int h) {
        int iw = img.getWidth(), ih = img.getHeight();
        int[] row = img.getRGB(0, 0, iw, ih, null, 0, iw);
        if (iw == w && ih == h) return row;
        int[] px = new int[w * h];
        for (int y = 0; y < ih; y++) System.arraycopy(row, y * iw, px, y * w, iw);
        return px;
    }

    /** Squared YIQ distance of two ARGB pixels (alpha blended over white); negative when the first is brighter. */
    static double colorDelta(int c1, int c2, boolean yOnly) {
        if (c1 == c2) return 0;
        double a1 = ((c1 >>> 24) & 0xff) / 255.0, a2 = ((c2 >>> 24) & 0xff) / 255.0;
        double r1 = blendWhite((c1 >> 16) & 0xff, a1), g1 = blendWhite((c1 >> 8) & 0xff, a1), b1 = blendWhite(c1 & 0xff, a1);
        double r2 = blendWhite((c2 >> 16) & 0xff, a2), g2 = blendWhite((c2 >> 8) & 0xff, a2), b2 = blendWhite(c2 & 0xff, a2);
        double y1 = rgb2y(r1, g1, b1), y2 = rgb2y(r2, g2, b2);
        double y = y1 - y2;
        if (yOnly) return y;
        double i = rgb2i(r1, g1, b1) - rgb2i(r2, g2, b2);
        double q = rgb2q(r1, g1, b1) - rgb2q(r2, g2, b2);
        double delta = 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q;
        return y1 > y2 ? -delta : delta;
    }

    private static double blendWhite(double c, double a) { return 255 + (c - 255) * a; }
    private static double rgb2y(double r, double g, double b) { return r * 0.29889531 + g * 0.58662247 + b * 0.11448223; }
    private static double rgb2i(double r, double g, double b) { return r * 0.59597799 - g * 0.27417610 - b * 0.32180189; }
    private static double rgb2q(double r, double g, double b) { return r * 0.21147017 - g * 0.52261711 + b * 0.31114694; }

    /**
     * Whether pixel (x1,y1) of {@code img} looks anti-aliased: it has both a darker and a brighter neighbour, at most two
     * identical neighbours, and the extreme neighbour sits in a flat area of both images.
     */
    static boolean antialiased(int[] img, int x1, int y1, int w, int h, int[] img2) {
        int x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0), x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
        int c = img[y1 * w + x1];
        int zeroes = (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) ? 1 : 0;
        double min = 0, max = 0;
        int minX = 0, minY = 0, maxX = 0, maxY = 0;
        for (int x = x0; x <= x2; x++) {
            for (int y = y0; y <= y2; y++) {
                if (x == x1 && y == y1) continue;
                double delta = colorDelta(c, img[y * w + x], true);
                if (delta == 0) {
                    zeroes++;
                    if (zeroes > 2) return false;
                } else if (delta < min) {
                    min = delta; minX = x; minY = y;
                } else if (delta > max) {
                    max = delta; maxX = x; maxY = y;
                }
            }
        }
        if (min == 0 || max == 0) return false;
        return (manySiblings(img, minX, minY, w, h) && manySiblings(img2, minX, minY, w, h))
                || (manySiblings(img, maxX, maxY, w, h) && manySiblings(img2, maxX, maxY, w, h));
    }

    /** More than two identical neighbours (edges count as one). */
    static boolean manySiblings(int[] img, int x1, int y1, int w, int h) {
        int x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0), x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
        int c = img[y1 * w + x1];
        int zeroes = (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) ? 1 : 0;
        for (int x = x0; x <= x2; x++) {
            for (int y = y0; y <= y2; y++) {
                if (x == x1 && y == y1) continue;
                if (img[y * w + x] == c) zeroes++;
                if (zeroes > 2) return true;
            }
        }
        return false;
    }

    /** The pixel's luminance, faded towards white (context for the red / yellow marks). */
    static int gray(int c) {
        double a = ((c >>> 24) & 0xff) / 255.0;
        double yv = rgb2y(blendWhite((c >> 16) & 0xff, a), blendWhite((c >> 8) & 0xff, a), blendWhite(c & 0xff, a));
        int v = (int) Math.round(255 + (yv - 255) * 0.1);
        v = Math.max(0, Math.min(255, v));
        return 0xff000000 | (v << 16) | (v << 8) | v;
    }

    private static int blend(int base, int over, double f) {
        int r = (int) Math.round(((base >> 16) & 0xff) * (1 - f) + ((over >> 16) & 0xff) * f);
        int g = (int) Math.round(((base >> 8) & 0xff) * (1 - f) + ((over >> 8) & 0xff) * f);
        int b = (int) Math.round((base & 0xff) * (1 - f) + (over & 0xff) * f);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
}
