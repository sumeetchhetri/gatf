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
package com.gatf.ai.heal;

import java.nio.file.Path;
import java.util.List;

/** H1 (Δ150): {@link RecordedFailure} for tests outside this package (the MCP heal tool). */
public final class HealFixtures {

    public static final List<String> SCRIPT = RecordedFailure.SCRIPT;
    public static final int FAILING_LINE = RecordedFailure.FAILING_LINE;

    private HealFixtures() {}

    /** Records the failing checkout run into {@code dir}; returns {@code dir/checkout-trace.zip} (selFile checkout.sel). */
    public static Path record(Path dir) throws Exception {
        return RecordedFailure.record(dir);
    }
}
