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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M9: ImageDiff + VisualSnapshots on fixture PNGs (src/test/resources/visual). */
class ImageDiffTest {

    @TempDir
    File tmp;

    private static BufferedImage png(String name) throws IOException {
        return ImageIO.read(ImageDiffTest.class.getResourceAsStream("/visual/" + name));
    }

    @Test
    void identicalImagesMatch() throws Exception {
        ImageDiff.Result r = ImageDiff.compare(png("base.png"), png("base.png"), new ImageDiff.Options());
        assertThat(r.passed).isTrue();
        assertThat(r.diffPixels).isEqualTo(0);
        assertThat(r.message).isEqualTo("images match");
    }

    @Test
    void aChangedAreaFailsAndIsRedInTheDiff() throws Exception {
        ImageDiff.Result r = ImageDiff.compare(png("base.png"), png("changed.png"), new ImageDiff.Options());
        assertThat(r.passed).isFalse();
        assertThat(r.diffPixels).isBetween(70L, 100L);   // the 10x10 square, less the anti-aliased line pixels it covers
        assertThat(r.diff.getRGB(85, 55)).isEqualTo(0xffff0000);
        assertThat(r.diff.getRGB(5, 5) & 0xffffff).isNotEqualTo(0xff0000);   // unchanged pixels are faded, not red
        assertThat(r.message).contains("pixel(s) differ");
    }

    @Test
    void allowedDifferenceAndMasks() throws Exception {
        assertThat(ImageDiff.compare(png("base.png"), png("changed.png"), new ImageDiff.Options().maxDiff("100")).passed).isTrue();
        assertThat(ImageDiff.compare(png("base.png"), png("changed.png"), new ImageDiff.Options().maxDiff("1.2%")).passed).isTrue();
        assertThat(ImageDiff.compare(png("base.png"), png("changed.png"), new ImageDiff.Options().maxDiff("0.5%")).passed).isFalse();
        ImageDiff.Result masked = ImageDiff.compare(png("base.png"), png("changed.png"), new ImageDiff.Options().mask(new Rectangle(78, 48, 14, 14)));
        assertThat(masked.passed).isTrue();
        assertThat(masked.diffPixels).isEqualTo(0);
        assertThatThrownBy(() -> new ImageDiff.Options().maxDiff("lots")).hasMessageContaining("pixel count or a percentage");
        assertThatThrownBy(() -> new ImageDiff.Options().threshold(2)).hasMessageContaining("between 0 and 1");
    }

    @Test
    void antiAliasingIsToleratedUnlessIncluded() throws Exception {
        ImageDiff.Result strict = ImageDiff.compare(png("base.png"), png("aa-shift.png"), new ImageDiff.Options().threshold(0.05).includeAA(true));
        ImageDiff.Result lenient = ImageDiff.compare(png("base.png"), png("aa-shift.png"), new ImageDiff.Options().threshold(0.05));
        assertThat(strict.diffPixels).isGreaterThan(0);
        assertThat(lenient.diffPixels).isLessThan(strict.diffPixels);
    }

    @Test
    void sizeMismatchFails() throws Exception {
        ImageDiff.Result r = ImageDiff.compare(png("base.png"), png("wider.png"), new ImageDiff.Options().maxDiff("100%"));
        assertThat(r.passed).isFalse();
        assertThat(r.sizeMismatch).isTrue();
        assertThat(r.message).isEqualTo("size differs: expected 120x80, actual 130x80");
        assertThat(r.diff.getWidth()).isEqualTo(130);
    }

    @Test
    void thresholdIsPerceptual() {
        BufferedImage a = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), b = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) { a.setRGB(x, y, 0xff808080); b.setRGB(x, y, 0xff848484); }
        assertThat(ImageDiff.compare(a, b, new ImageDiff.Options().threshold(0.1)).passed).isTrue();    // near-identical grey
        assertThat(ImageDiff.compare(a, b, new ImageDiff.Options().threshold(0)).passed).isFalse();     // exact
    }

    // ---- baseline store ----------------------------------------------------------------------------------------

    @Test
    void firstRunWritesTheBaselineAndCiFailsWithoutOne() throws Exception {
        File tc = new File(tmp, "tests"), out = new File(tmp, "out");
        VisualSnapshots.Outcome ci = VisualSnapshots.check(tc, "chrome", "ui/home", "home", png("base.png"), new ImageDiff.Options(), false, true, out);
        assertThat(ci.passed).isFalse();
        assertThat(ci.status).isEqualTo(VisualSnapshots.Status.MISSING);
        assertThat(ci.message).contains("--update-snapshots");
        assertThat(new File(out, "chrome-ui-home-home-actual.png")).matches(File::isFile);

        VisualSnapshots.Outcome first = VisualSnapshots.check(tc, "chrome", "ui/home", "home", png("base.png"), new ImageDiff.Options(), false, false, out);
        assertThat(first.status).isEqualTo(VisualSnapshots.Status.CREATED);
        assertThat(new File(tc, "__screenshots__/chrome/ui/home/home.png")).matches(File::isFile);
        assertThat(VisualSnapshots.check(tc, "chrome", "ui/home", "home", png("base.png"), new ImageDiff.Options(), false, true, out).status)
                .isEqualTo(VisualSnapshots.Status.MATCH);
    }

    @Test
    void aPixelChangeFailsWithADiffImageAndUpdateAcceptsIt() throws Exception {
        File tc = new File(tmp, "tests"), out = new File(tmp, "out");
        VisualSnapshots.check(tc, "firefox", "cart", "cart page", png("base.png"), new ImageDiff.Options(), false, false, out);
        VisualSnapshots.Outcome o = VisualSnapshots.check(tc, "firefox", "cart", "cart page", png("changed.png"), new ImageDiff.Options(), false, false, out);
        assertThat(o.passed).isFalse();
        assertThat(o.status).isEqualTo(VisualSnapshots.Status.MISMATCH);
        File diff = new File(out, "firefox-cart-cart_page-diff.png");
        assertThat(o.attachments).containsExactly(diff.getAbsolutePath(), new File(out, "firefox-cart-cart_page-expected.png").getAbsolutePath(),
                new File(out, "firefox-cart-cart_page-actual.png").getAbsolutePath());
        assertThat(ImageIO.read(diff).getRGB(85, 55)).isEqualTo(0xffff0000);
        assertThat(o.message).contains("does not match its baseline").contains("diff: ");

        VisualSnapshots.Outcome up = VisualSnapshots.check(tc, "firefox", "cart", "cart page", png("changed.png"), new ImageDiff.Options(), true, true, out);
        assertThat(up.status).isEqualTo(VisualSnapshots.Status.UPDATED);
        assertThat(VisualSnapshots.check(tc, "firefox", "cart", "cart page", png("changed.png"), new ImageDiff.Options(), false, true, out).passed).isTrue();
    }

    @Test
    void twoScriptsKeepSeparateBaselinesForTheSameName() throws Exception {
        File tc = new File(tmp, "tests"), out = new File(tmp, "out");
        VisualSnapshots.check(tc, "chrome", "a", "home", png("base.png"), new ImageDiff.Options(), false, false, out);
        VisualSnapshots.Outcome b = VisualSnapshots.check(tc, "chrome", "b", "home", png("changed.png"), new ImageDiff.Options(), false, false, out);
        assertThat(b.status).isEqualTo(VisualSnapshots.Status.CREATED);   // b.sel's own baseline, not a.sel's
        assertThat(VisualSnapshots.baselineFile(tc, "chrome", "../../x/y", "n").getPath().replace('\\', '/')).endsWith("__screenshots__/chrome/x/y/n.png");
        assertThat(ImageDiff.compare(png("base.png"), png("base.png"), new ImageDiff.Options()).diff).isNull();   // identical: no diff image
    }

    @Test
    void namesAreSafeFileNames() {
        assertThat(VisualSnapshots.safeName("checkout/step 2.png")).isEqualTo("checkout-step_2");
        assertThat(VisualSnapshots.safeName("../../etc")).isEqualTo("-..-etc".replaceAll("^[.]+", ""));
        assertThat(VisualSnapshots.baselineFile(new File("/t"), "chrome-hdl", null, "a").getPath().replace('\\', '/')).isEqualTo("/t/__screenshots__/chrome-hdl/a.png");
        assertThatThrownBy(() -> VisualSnapshots.safeName(" ")).hasMessageContaining("needs a name");
    }
}
