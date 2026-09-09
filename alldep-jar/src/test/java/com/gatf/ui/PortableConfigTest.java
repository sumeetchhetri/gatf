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
package com.gatf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.glassfish.grizzly.http.server.Request;
import org.junit.jupiter.api.Test;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;

/** Config saves must never pin the server's own root dir as an absolute base path (project portability). */
class PortableConfigTest {

    static GatfConfigToolMojoInt mojo(String root) {
        return new GatfConfigToolMojoInt() {
            public void setContext(AcceptanceTestContext context) {}
            public AcceptanceTestContext getContext() { return null; }
            public String getRootDir() { return root; }
            public void setRootDir(String rootDir) {}
            public void setAuthTestCase(TestCase tc) {}
            public boolean isAuthenticated(Request request) { return true; }
        };
    }

    static String save(GatfExecutorConfig c, String root) throws Exception {
        File f = new File(root, "gatf-config.json");
        GatfConfigToolUtil.sanitizeAndSaveGatfConfig(c, mojo(root), true, f);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void nullBasePathsDefaultInMemoryButAreNotPersisted() throws Exception {
        Path root = Files.createTempDirectory("gatf-portable");
        GatfExecutorConfig c = new GatfExecutorConfig();
        String json = save(c, root.toString());
        assertThat(c.getTestCasesBasePath()).isEqualTo(root.toString());          // runtime still has it
        assertThat(c.getOutFilesBasePath()).isEqualTo(root.toString());
        assertThat(json).doesNotContain("testCasesBasePath").doesNotContain("outFilesBasePath");
    }

    @Test
    void alreadyPinnedRootDirIsStrippedOnSave() throws Exception {
        Path root = Files.createTempDirectory("gatf-portable");
        GatfExecutorConfig c = new GatfExecutorConfig();
        c.setTestCasesBasePath(root.toString());
        c.setOutFilesBasePath(root.toString() + File.separator + "." );            // same dir, different spelling
        String json = save(c, root.toString());
        assertThat(json).doesNotContain("testCasesBasePath").doesNotContain("outFilesBasePath");
        assertThat(c.getTestCasesBasePath()).isEqualTo(root.toString());          // restored after the write
    }

    @Test
    void aDeliberatelyDifferentBasePathIsKept() throws Exception {
        Path root = Files.createTempDirectory("gatf-portable");
        Path other = Files.createTempDirectory("gatf-elsewhere");
        GatfExecutorConfig c = new GatfExecutorConfig();
        c.setTestCasesBasePath(other.toString());
        String json = save(c, root.toString());
        assertThat(json).contains("testCasesBasePath").contains(other.toString().replace("\\", "\\\\"));
    }
}
