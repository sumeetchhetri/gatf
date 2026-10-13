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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;

/**
 * H1 (Δ149): the {@code <ai>} block in the executor config: opt-in (absent → null, nothing serialized, no behaviour
 * change), read from XML like every other block, and with no field that could carry a key.
 */
public class AiConfigXmlTest {

    @Test
    void absentByDefaultAndAnExistingConfigSerializesWithoutIt() throws Exception {
        GatfExecutorConfig c = WorkflowContextHandler.XOM.readValue(
                Files.readAllBytes(Paths.get("src/test/resources/codegen-corpus/gatf-config.xml")), GatfExecutorConfig.class);
        assertNull(c.getAi());
        assertFalse(WorkflowContextHandler.XOM.writeValueAsString(c).contains("<ai>"));
        assertFalse(WorkflowContextHandler.OM.writeValueAsString(c).contains("\"ai\""));
        assertFalse(AiClient.open(c.getAi(), System::getenv, AiProviders.all(null, null), null, null).configured());
    }

    @Test
    void anAiBlockIsReadAndHasNoKeyField() throws Exception {
        String xml = "<gatf-execute-config><ai><provider>anthropic</provider><model>claude-x</model>"
                + "<apiKeyEnv>MY_KEY</apiKeyEnv><timeoutMs>5000</timeoutMs><maxTokensPerRun>2000</maxTokensPerRun></ai></gatf-execute-config>";
        GatfExecutorConfig c = WorkflowContextHandler.XOM.readValue(xml, GatfExecutorConfig.class);
        assertEquals("anthropic", c.getAi().getProvider());
        assertEquals("MY_KEY", c.getAi().getApiKeyEnv());
        assertEquals(5000, c.getAi().timeoutMsOr());
        assertEquals(2000, c.getAi().maxTokensOr());
        String back = WorkflowContextHandler.XOM.writeValueAsString(c);
        assertTrue(back.contains("<apiKeyEnv>MY_KEY</apiKeyEnv>"), back);
        for (Class<?> k = AiConfig.class; k != null; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                String n = f.getName().toLowerCase();
                assertFalse(n.equals("apikey") || n.equals("key") || n.equals("token") || n.equals("secret"), "no key-holding field: " + f.getName());
            }
        }
    }
}
