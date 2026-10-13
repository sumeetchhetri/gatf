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

/**
 * H1 (Δ149): an AI call that did not produce a usable reply. {@link #code} is a fixed word callers can show; the
 * message is built by GATF and already redacted, never a provider body verbatim.
 */
public final class AiException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Code { NOT_CONFIGURED, BUDGET, TIMEOUT, HTTP, BAD_REPLY, INTERRUPTED, IO }

    public final Code code;

    public AiException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /** A short word for reports: {@code not-configured}, {@code budget}, {@code timeout}, … */
    public String word() { return code.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
}
