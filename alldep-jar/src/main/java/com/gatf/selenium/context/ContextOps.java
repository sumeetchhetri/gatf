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
package com.gatf.selenium.context;

import java.util.List;
import java.util.Map;

/**
 * H3 (Δ153): the four protocol operations behind a per-test browser context, over CDP
 * ({@code Target.createBrowserContext}) or WebDriver BiDi ({@code browser.createUserContext}). Built by
 * {@link ContextCalls}; obtained from a transport through {@code BrowserControl.contextOps()} (D-2: feature code
 * never talks to a protocol directly). Implementations throw on a protocol error; {@link ContextScope} catches.
 */
public interface ContextOps {

    /** One raw protocol command: CDP {@code executeCdpCommand} or a raw BiDi command. */
    interface Sender {
        Map<String, Object> send(String method, Map<String, Object> params);
    }

    /** {@code cdp} or {@code bidi}. */
    String protocol();

    /** A new, empty context (own cookies, storage, cache, permissions); its id. */
    String create();

    /** A new blank tab inside {@code context}; the protocol's page id (CDP target id / BiDi browsing-context id). */
    String openPage(String context);

    /** Dispose {@code context}; the browser closes every tab in it. */
    void remove(String context);

    /** Ids of the non-default contexts alive in the browser now (leak counting). */
    List<String> list();

    /** Release any connection these operations opened (script end). */
    default void close() { }
}
