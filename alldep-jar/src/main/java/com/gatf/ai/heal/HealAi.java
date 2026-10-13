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
import java.util.function.Function;

import com.gatf.ai.AiClient;
import com.gatf.ai.AiConfig;
import com.gatf.ai.AiProviders;
import com.gatf.ai.HttpSender;

/**
 * H1 (Δ150): the AI client a heal uses: built-in providers plus plugin providers from {@code plugins}
 * ({@code PluginRegistry.classLoader()}), with a relative {@code <apiKeyFile>} resolved against the config file's
 * directory. No {@code <ai>} block (or any other gap) gives a client that is not configured, whose status says why;
 * deterministic healing works the same either way. A fresh client per heal, so each heal has the run budget.
 */
public final class HealAi {

    private HealAi() {}

    public static AiClient open(AiConfig cfg, Path configDir, ClassLoader plugins, Function<String, String> env, HttpSender http, List<String> warnings) {
        if (cfg == null) return AiClient.notConfigured("no <ai> block in the config");
        try {
            return AiClient.open(cfg, configDir, env, AiProviders.all(plugins, warnings), http, null);
        } catch (RuntimeException | LinkageError e) {   // never let a broken plugin cost the deterministic heal
            return AiClient.notConfigured("AI setup failed (" + e.getClass().getSimpleName() + ")");
        }
    }
}
