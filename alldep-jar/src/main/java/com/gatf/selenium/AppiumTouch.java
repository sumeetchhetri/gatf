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
package com.gatf.selenium;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import com.gatf.selenium.mobile.MobilePlatform;
import com.gatf.selenium.mobile.TouchGestures;
import com.gatf.selenium.mobile.TouchGestures.Box;
import com.gatf.selenium.mobile.TouchGestures.ChainStep;
import com.gatf.selenium.mobile.TouchGestures.Finger;
import com.gatf.selenium.mobile.TouchGestures.Step;

/**
 * H5 (Δ158): the seleasy touch commands on an Appium 2 session. Gestures go out as W3C Perform Actions with
 * {@code pointerType: touch} (built by {@link TouchGestures}, never the removed TouchAction / MultiTouchAction);
 * platform gestures use the drivers' documented {@code mobile:} methods ({@link MobilePlatform}).
 * <p>
 * A session counts as Appium when its {@code platformName} capability is Android or iOS. On any other session
 * (a desktop browser) every touch command is a step error naming the command and its {@code file:line}; nothing is sent,
 * and only that step fails. Errors from the device are reported the same way.
 */
final class AppiumTouch {

	private AppiumTouch() {}

	/** The session's mobile platform, or null for a desktop session. */
	static MobilePlatform platform(WebDriver d) {
		if (!(d instanceof HasCapabilities)) return null;
		try {
			Capabilities c = ((HasCapabilities) d).getCapabilities();
			return c == null ? null : MobilePlatform.of(c.getCapability("platformName"));
		} catch (RuntimeException e) {
			return null;
		}
	}

	static MobilePlatform require(WebDriver d, String step, String where) {
		MobilePlatform p = platform(d);
		if (p == null || !(d instanceof Interactive) || !(d instanceof JavascriptExecutor)) {
			String what = "this session";
			if (d instanceof HasCapabilities) {
				try {
					Capabilities c = ((HasCapabilities) d).getCapabilities();
					what = "this session is " + c.getBrowserName() + (c.getPlatformName() != null ? " on " + String.valueOf(c.getPlatformName()).toLowerCase(Locale.ROOT) : "");
				} catch (RuntimeException ignore) { /* keep the generic text */ }
			} else if (d == null) {
				what = "no session is open";
			}
			throw MobilePlatform.stepError(step, where, step + " needs an Appium session (open appium-android | appium-ios); " + what);
		}
		return p;
	}

	static void tap(WebDriver d, WebElement e, Point p, int durationMs, String where) {
		require(d, "tap", where);
		int[] pt = run("tap", where, () -> TouchGestures.point(e == null ? null : box(e.getRect()),
				p == null ? null : p.getX(), p == null ? null : p.getY()));
		if (pt == null) throw MobilePlatform.stepError("tap", where, "tap needs an element or x y");
		perform(d, "tap", where, TouchGestures.tap(pt, TouchGestures.tapHold(durationMs)));
	}

	static void swipe(WebDriver d, Point from, Point to, int durationMs, String where) {
		require(d, "swipe", where);
		perform(d, "swipe", where, TouchGestures.swipe(new int[] {from.getX(), from.getY()}, new int[] {to.getX(), to.getY()}, durationMs));
	}

	static void zoom(WebDriver d, WebElement e, int x, int y, boolean zoomIn, String where) {
		String step = zoomIn ? "zoom" : "pinch";
		require(d, step, where);
		int[] c = e == null ? new int[] {x, y} : run(step, where, () -> box(e.getRect()).center());
		Box window = null;
		try {
			Dimension s = d.manage().window().getSize();
			window = new Box(0, 0, s.getWidth(), s.getHeight());
		} catch (RuntimeException ignore) { /* unknown size: points are not clamped */ }
		perform(d, step, where, TouchGestures.zoom(c, zoomIn, window));
	}

	/** {@code touch …}: each entry is {action, WebElement or null, Integer x, Integer y, Integer durationMs}. */
	static void chain(WebDriver d, List<Object[]> steps, String where) {
		require(d, "touch", where);
		List<Finger> f = run("touch", where, () -> {
			List<ChainStep> cs = new ArrayList<ChainStep>();
			for (Object[] o : steps) {
				WebElement el = (WebElement) o[1];
				cs.add(new ChainStep((String) o[0], el == null ? null : box(el.getRect()), (Integer) o[2], (Integer) o[3], (Integer) o[4]));
			}
			return TouchGestures.chain(cs);
		});
		perform(d, "touch", where, f);
	}

	static void hideKeypad(WebDriver d, String where) {
		MobilePlatform p = require(d, "hidekeypad", where);
		mobile(d, "hidekeypad", where, p.hideKeypad());
	}

	static void shake(WebDriver d, String where) {
		MobilePlatform p = require(d, "shake", where);
		MobilePlatform.MobileCall call;
		try {
			call = p.shake();
		} catch (UnsupportedOperationException e) {
			throw MobilePlatform.stepError("shake", where, e.getMessage());
		}
		mobile(d, "shake", where, call);
	}

	/** {@code rotate}: portrait ↔ landscape through Appium's orientation endpoint (java-client SupportsRotation). */
	static void rotate(WebDriver d, String where) {
		require(d, "rotate", where);
		if (!(d instanceof io.appium.java_client.remote.SupportsRotation)) {
			throw MobilePlatform.stepError("rotate", where, "rotate needs an Appium Android or iOS driver (open appium-android | appium-ios)");
		}
		io.appium.java_client.remote.SupportsRotation r = (io.appium.java_client.remote.SupportsRotation) d;
		run("rotate", where, () -> {
			String next = MobilePlatform.nextOrientation(r.getOrientation().value());
			r.rotate("PORTRAIT".equals(next) ? ScreenOrientation.PORTRAIT : ScreenOrientation.LANDSCAPE);
			return null;
		});
	}

	// ---- plumbing ---------------------------------------------------------------------------------------------------

	static Box box(Rectangle r) {
		return new Box(r.getX(), r.getY(), r.getWidth(), r.getHeight());
	}

	private static void mobile(WebDriver d, String step, String where, MobilePlatform.MobileCall call) {
		run(step, where, () -> ((JavascriptExecutor) d).executeScript(call.script, call.args));
	}

	private static void perform(WebDriver d, String step, String where, List<Finger> fingers) {
		Collection<Sequence> seq = sequences(fingers);
		run(step, where, () -> {
			((Interactive) d).perform(seq);
			return null;
		});
	}

	/** One Selenium Sequence per finger, one action per {@link Step}; encodes to exactly {@link TouchGestures#toW3c}. */
	static Collection<Sequence> sequences(List<Finger> fingers) {
		List<Sequence> out = new ArrayList<Sequence>();
		for (Finger f : fingers) {
			PointerInput in = new PointerInput(PointerInput.Kind.TOUCH, f.id);
			Sequence s = new Sequence(in, 0);
			for (Step st : f.steps) {
				switch (st.type) {
					case "pointerMove":
						s.addAction(in.createPointerMove(Duration.ofMillis(st.durationMs), PointerInput.Origin.viewport(), st.x, st.y));
						break;
					case "pointerDown":
						s.addAction(in.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
						break;
					case "pointerUp":
						s.addAction(in.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
						break;
					default:
						s.addAction(new Pause(in, Duration.ofMillis(st.durationMs)));
				}
			}
			out.add(s);
		}
		return out;
	}

	interface Call<T> { T get(); }

	/** Runs one device call; any failure becomes this step's error (first line of the reason, original as the cause). */
	private static <T> T run(String step, String where, Call<T> c) {
		try {
			return c.get();
		} catch (MobilePlatform.MobileStepError e) {
			throw e;
		} catch (RuntimeException e) {
			String m = e.getMessage() == null || e.getMessage().trim().isEmpty() ? e.getClass().getSimpleName() : e.getMessage().trim().split("\n")[0];
			RuntimeException se = MobilePlatform.stepError(step, where, m);
			se.initCause(e);
			throw se;
		}
	}
}
