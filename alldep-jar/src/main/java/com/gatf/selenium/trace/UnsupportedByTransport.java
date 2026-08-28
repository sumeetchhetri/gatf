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
package com.gatf.selenium.trace;

/**
 * Thrown by a {@link BrowserControl} implementation for a capability it does not yet
 * support (e.g. the BiDi transport before parity). Recorders should guard with
 * {@link BrowserControl#supports(Capability)} and never provoke this in normal flow.
 *
 * @author Sumeet Chhetri
 */
public class UnsupportedByTransport extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public UnsupportedByTransport(String message) { super(message); }
}
