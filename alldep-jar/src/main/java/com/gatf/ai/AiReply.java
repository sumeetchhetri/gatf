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
package com.gatf.ai;

/** H1 (Δ149): a model reply: its text (untrusted, like page text) and the token usage it reported (0 when absent). */
public final class AiReply {
    public final String text;
    public final long inputTokens;
    public final long outputTokens;

    public AiReply(String text, long inputTokens, long outputTokens) {
        this.text = text == null ? "" : text;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
    }
}
