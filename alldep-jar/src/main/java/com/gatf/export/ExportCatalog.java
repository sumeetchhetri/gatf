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
package com.gatf.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Δ160 — the export decision for every seleasy statement keyword in docs/seleasy/commands.json: MAPPED, PARTIAL
 * (mapped, with a report note), STRUCTURAL (comments and braces) or UNSUPPORTED (exported as a
 * {@code // TODO(gatf-export): <line>} comment and listed in the report). {@code ExportCatalogDriftTest} fails when
 * commands.json gains a keyword with no decision here, or loses one this table still lists. The class name is copied
 * from commands.json so the parser-parity test can match each parsed statement to the real parser's Command class.
 * Backend-neutral: a second backend (Δ161) reuses these decisions or overrides them per keyword. Pure: no Selenium types.
 */
public final class ExportCatalog {

    public enum Decision { MAPPED, PARTIAL, STRUCTURAL, UNSUPPORTED }

    public static final class Entry {
        public final String keyword;
        /** The backing Command class named in commands.json. */
        public final String commandClass;
        public final Decision decision;
        /** What a PARTIAL mapping leaves out, or a hint for an UNSUPPORTED one; may be empty. */
        public final String note;

        Entry(String keyword, String commandClass, Decision decision, String note) {
            this.keyword = keyword;
            this.commandClass = commandClass;
            this.decision = decision;
            this.note = note;
        }
    }

    private static final Decision MAPPED = Decision.MAPPED, PARTIAL = Decision.PARTIAL,
            STRUCTURAL = Decision.STRUCTURAL, UNSUPPORTED = Decision.UNSUPPORTED;
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();

    private static void d(String keyword, String commandClass, Decision decision, String note) {
        if (ENTRIES.put(keyword, new Entry(keyword, commandClass, decision, note)) != null) {
            throw new IllegalStateException("duplicate export decision for '" + keyword + "'");
        }
    }

    static {
        d("chord", "TypeCommand", PARTIAL, "characters only (unicode escapes decoded); special keys are not translated");
        d("click", "ClickCommand", MAPPED, "");
        d("randomize", "TypeCommand", UNSUPPORTED, "");
        d("sendkeys", "TypeCommand", MAPPED, "");
        d("type", "TypeCommand", MAPPED, "");
        d("#", "ScopedLoopCommand", UNSUPPORTED, "");
        d("##", "ScopedLoopCommand", UNSUPPORTED, "");
        d("#c", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#counter", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#file", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#j", "JavaControlCommand", UNSUPPORTED, "");
        d("#mongo", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#o", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#optional", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#p", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#p-sf", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#provider", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#provider-sf", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#sql", "ProviderLoopCommand", UNSUPPORTED, "");
        d("#tp", "TransientProviderCommand", UNSUPPORTED, "");
        d("#transient-provider", "TransientProviderCommand", UNSUPPORTED, "");
        d("#transient-variable", "TransientVariableCommand", MAPPED, "");
        d("#tv", "TransientVariableCommand", MAPPED, "");
        d("/*", "MultiCommentCommand", STRUCTURAL, "");
        d("//", "CommentCommand", STRUCTURAL, "");
        d(":", "ElseCommand", MAPPED, "plain locator conditions only; others are TODO");
        d(":?", "ElseIfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d(":?!", "ElseIfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("<<<", "CodeCommand", UNSUPPORTED, "");
        d("<<<(", "CodeCommand", UNSUPPORTED, "");
        d(">>>", "ValueCommand", UNSUPPORTED, "");
        d("?", "IfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("?!", "IfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("??", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("??+", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("??-", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("??~", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("@call", "ExecSubTestCommand", MAPPED, "");
        d("[", "ValueListCommand", UNSUPPORTED, "");
        d("]", "ValueCommand", UNSUPPORTED, "");
        d("^", "ValueCommand", UNSUPPORTED, "");
        d("a11y", "A11yCommand", UNSUPPORTED, "");
        d("actions", "ActionsCommand", PARTIAL, "draganddrop / dragdrop (+1 variants) only");
        d("afterAll", "SubTestCommand", MAPPED, "");
        d("afterEach", "SubTestCommand", MAPPED, "");
        d("alert", "AlertCommand", UNSUPPORTED, "");
        d("back", "BackCommand", MAPPED, "");
        d("barcode", "ReadQRCodeBarCodeCommand", UNSUPPORTED, "");
        d("beforeAll", "SubTestCommand", MAPPED, "");
        d("beforeEach", "SubTestCommand", MAPPED, "");
        d("begin code in", "CodeCommand", UNSUPPORTED, "");
        d("break", "BreakCommand", UNSUPPORTED, "");
        d("canvas", "CanvasCommand", UNSUPPORTED, "");
        d("clear", "ClearCommand", MAPPED, "");
        d("clock", "ClockCommand", PARTIAL, "install, set, fastforward, resume; pause and uninstall have no Playwright equivalent");
        d("close", "CloseCommand", PARTIAL, "bare close only; close N / close others are TODO");
        d("config", "ConfigPropsCommand", UNSUPPORTED, "");
        d("confirm", "ConfirmCommand", UNSUPPORTED, "");
        d("continue", "ContinueCommand", UNSUPPORTED, "");
        d("curl:", "NlpExecSubTestCommand", UNSUPPORTED, "");
        d("dblclick", "DoubleClickCommand", MAPPED, "");
        d("doubleclick", "DoubleClickCommand", MAPPED, "");
        d("ds query", "DSQueryCommand", UNSUPPORTED, "");
        d("dsq", "DSQueryCommand", UNSUPPORTED, "");
        d("dynprops", "DynPropsCommand", UNSUPPORTED, "");
        d("ele-screenshot", "EleScreenshotCommand", MAPPED, "");
        d("else", "ElseCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("else if", "ElseIfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("else ifnot", "ElseIfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("emulate", "EmulateCommand", PARTIAL, "viewport, geolocation, permissions, colorscheme, offline, online");
        d("end code", "ValueCommand", UNSUPPORTED, "");
        d("exec", "ExecCommand", UNSUPPORTED, "");
        d("execjs", "ExecJsCommand", MAPPED, "");
        d("execjsfile", "ExecJsFileCommand", UNSUPPORTED, "");
        d("fail", "FailCommand", MAPPED, "");
        d("filevar", "FileVarCommand", UNSUPPORTED, "");
        d("fixme", "SubTestCommand", MAPPED, "");
        d("forward", "ForwardCommand", MAPPED, "");
        d("frame", "FrameCommand", UNSUPPORTED, "");
        d("func", "SubTestCommand", MAPPED, "");
        d("goto", "GotoCommand", MAPPED, "");
        d("hidekeypad", "MHideKeyPadCommand", UNSUPPORTED, "");
        d("hover", "HoverCommand", MAPPED, "");
        d("hoverclick", "HoverAndClickCommand", MAPPED, "");
        d("http", "HttpCommand", UNSUPPORTED, "");
        d("if", "IfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("ifnot", "IfCommand", MAPPED, "plain locator conditions only; others are TODO");
        d("imgsave", "SaveImageCommand", UNSUPPORTED, "");
        d("import", "ImportCommand", UNSUPPORTED, "");
        d("include", "IncludeCommand", UNSUPPORTED, "");
        d("jsvar", "JsVarCommand", MAPPED, "");
        d("layer", "LayerCommand", UNSUPPORTED, "");
        d("loop over", "ScopedLoopCommand", UNSUPPORTED, "");
        d("mail", "MailCommand", UNSUPPORTED, "");
        d("maximize", "MaximizeCommand", PARTIAL, "emitted as a comment: the viewport comes from playwright.config");
        d("mode", "ModeCommand", UNSUPPORTED, "");
        d("netapix", "NetworkAPIInterceptCommand", UNSUPPORTED, "");
        d("nowait", "Command", MAPPED, "");
        d("ocr", "ReadImageTextCommand", UNSUPPORTED, "");
        d("only", "SubTestCommand", MAPPED, "");
        d("open", "BrowserCommand", PARTIAL, "the first open is a comment (the Playwright project picks the browser); a second open is TODO");
        d("pass", "PassCommand", MAPPED, "");
        d("pinch", "MPinchCommand", UNSUPPORTED, "");
        d("printpdf", "PrintPDFCommand", UNSUPPORTED, "");
        d("qrcode", "ReadQRCodeBarCodeCommand", UNSUPPORTED, "");
        d("readfile", "ReadFileCommand", UNSUPPORTED, "");
        d("readimgtext", "ReadImageTextCommand", UNSUPPORTED, "");
        d("readqrbar", "ReadQRCodeBarCodeCommand", UNSUPPORTED, "");
        d("refresh", "RefreshCommand", MAPPED, "");
        d("require", "RequireCommand", UNSUPPORTED, "");
        d("robot", "RobotCommand", UNSUPPORTED, "");
        d("rotate", "MRotateCommand", UNSUPPORTED, "");
        d("route", "RouteCommand", MAPPED, "");
        d("screenshot", "ScreenshotCommand", MAPPED, "");
        d("screenshot compare", "ScreenshotCompareCommand", PARTIAL, "name, masks and maxdiff mapped; threshold is noted");
        d("scroll", "ScrollCommand", MAPPED, "");
        d("scrolldown", "RobotCommand", UNSUPPORTED, "");
        d("scrollpagedown", "RobotCommand", UNSUPPORTED, "");
        d("scrollpageup", "RobotCommand", UNSUPPORTED, "");
        d("scrollup", "RobotCommand", UNSUPPORTED, "");
        d("select", "SelectCommand", MAPPED, "");
        d("setting", "SettingCommand", UNSUPPORTED, "");
        d("shake", "MShakeCommand", UNSUPPORTED, "");
        d("skip", "SubTestCommand", MAPPED, "");
        d("sleep", "SleepCommand", MAPPED, "");
        d("state", "StateCommand", PARTIAL, "state save only; state load is a context option in Playwright");
        d("submit", "SubmitCommand", MAPPED, "");
        d("subtest", "SubTestCommand", MAPPED, "");
        d("swipe", "MSwipeCommand", UNSUPPORTED, "");
        d("tab", "TabCommand", UNSUPPORTED, "");
        d("tap", "MTapCommand", UNSUPPORTED, "");
        d("timeout", "TimeoutCommand", PARTIAL, "first value only (page.setDefaultTimeout)");
        d("touch", "MTouchActionCommand", UNSUPPORTED, "");
        d("unroute", "RouteCommand", MAPPED, "");
        d("upload", "UploadCommand", MAPPED, "");
        d("var", "VarCommand", PARTIAL, "literal values and @var only; plugin and Java values are TODO");
        d("wait for", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("wait till visible", "WaitTillElementVisibleOrInvisibleCommand", MAPPED, "");
        d("waitready", "WaitForReady", MAPPED, "");
        d("warn", "WarnCommand", MAPPED, "");
        d("window", "WindowCommand", UNSUPPORTED, "");
        d("wopensave", "WindowOpenSaveInterceptJsCommand", UNSUPPORTED, "");
        d("zoom", "MZoomCommand", UNSUPPORTED, "");
        d("{", "StartCommand", STRUCTURAL, "");
        d("}", "EndCommand", STRUCTURAL, "");
    }

    private ExportCatalog() {}

    /** Every keyword with its decision, in commands.json order. */
    public static Map<String, Entry> all() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /** The entry for a keyword, or null when the keyword is not a catalog keyword (plugins, unknown words). */
    public static Entry get(String keyword) {
        return keyword == null ? null : ENTRIES.get(keyword);
    }
}
