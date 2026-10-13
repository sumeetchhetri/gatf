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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * H1 (Δ149): the opt-in {@code <ai>} block of the executor config. Absent (the default) means AI is not configured
 * and nothing anywhere behaves differently. There is deliberately <b>no key field</b>: the key is read at call time from
 * the environment variable {@link #apiKeyEnv} (default per provider: {@code ANTHROPIC_API_KEY}, {@code OPENAI_API_KEY})
 * or from the file {@link #apiKeyFile}, so it can never be serialized with the config, a report or a distributed
 * run's payload. Cost fields are in whatever currency the prices are given in (per million tokens).
 */
@JacksonXmlRootElement(localName = "ai")
@JsonAutoDetect(getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class AiConfig {

    /** {@code anthropic} | {@code openai} | {@code local} (an OpenAI-compatible endpoint) | a plugin provider's name. */
    private String provider;
    private String model;
    /** Overrides the provider's default endpoint; required for {@code local}. */
    private String endpoint;
    /** Name of the environment variable holding the key. */
    private String apiKeyEnv;
    /** Path of a file holding the key (first line), as an alternative to the environment. */
    private String apiKeyFile;
    private Integer timeoutMs;
    private Integer retries;
    private Integer maxCallsPerRun;
    private Integer maxTokensPerRun;
    private Integer maxOutputTokens;
    private Double maxCostPerRun;
    private Double inputCostPerMTok;
    private Double outputCostPerMTok;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKeyEnv() { return apiKeyEnv; }
    public void setApiKeyEnv(String apiKeyEnv) { this.apiKeyEnv = apiKeyEnv; }
    public String getApiKeyFile() { return apiKeyFile; }
    public void setApiKeyFile(String apiKeyFile) { this.apiKeyFile = apiKeyFile; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }
    public Integer getRetries() { return retries; }
    public void setRetries(Integer retries) { this.retries = retries; }
    public Integer getMaxCallsPerRun() { return maxCallsPerRun; }
    public void setMaxCallsPerRun(Integer maxCallsPerRun) { this.maxCallsPerRun = maxCallsPerRun; }
    public Integer getMaxTokensPerRun() { return maxTokensPerRun; }
    public void setMaxTokensPerRun(Integer maxTokensPerRun) { this.maxTokensPerRun = maxTokensPerRun; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public Double getMaxCostPerRun() { return maxCostPerRun; }
    public void setMaxCostPerRun(Double maxCostPerRun) { this.maxCostPerRun = maxCostPerRun; }
    public Double getInputCostPerMTok() { return inputCostPerMTok; }
    public void setInputCostPerMTok(Double inputCostPerMTok) { this.inputCostPerMTok = inputCostPerMTok; }
    public Double getOutputCostPerMTok() { return outputCostPerMTok; }
    public void setOutputCostPerMTok(Double outputCostPerMTok) { this.outputCostPerMTok = outputCostPerMTok; }

    public int timeoutMsOr() { return clamp(timeoutMs, 30_000, 1_000, 600_000); }
    public int retriesOr() { return clamp(retries, 2, 0, 5); }
    public int maxCallsOr() { return clamp(maxCallsPerRun, 10, 1, 1_000); }
    public int maxTokensOr() { return clamp(maxTokensPerRun, 50_000, 1, 10_000_000); }
    public int maxOutputTokensOr() { return clamp(maxOutputTokens, 1_024, 16, 32_000); }

    static int clamp(Integer v, int dflt, int lo, int hi) {
        if (v == null) return dflt;
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String toString() {
        return "AiConfig[provider=" + provider + ", model=" + model + ", endpoint=" + endpoint + ", apiKeyEnv=" + apiKeyEnv + "]";
    }
}
