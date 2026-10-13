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
package com.gatf.executor.distributed;

/**
 * H8 (Δ162): a worker's reference to one functional trace it wrote locally ({@code <script>-trace.zip}). It rides
 * back to the coordinator inside {@link DistributedTestStatus} (JSON); the zip bytes follow on the same channel as a
 * frame (see {@link DistributedTraces}). On the coordinator every field is untrusted input: it is validated and
 * sanitised before use, and never used as a path. Holds no configuration values, so no secrets.
 */
public class TraceRef implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** The worker as the coordinator addressed it (informational: the coordinator names files from its own node). */
    private String workerId;
    /** The worker's run: {@code DRun-<index>-<user>-<runNum>}. */
    private String runId;
    /** The script (the {@code .sel} the trace belongs to). */
    private String script;
    /** Browser key + session name of the trace, as in the report. */
    private String session;
    /** Path of the zip relative to the worker's out dir, {@code /}-separated. */
    private String relPath;
    /** Byte size of the zip. */
    private long size;
    /** Lower-case hex SHA-256 of the zip; null when the worker did not send it (over the size cap). */
    private String sha256;

    public TraceRef() {}

    public TraceRef(String workerId, String runId, String script, String session, String relPath, long size, String sha256) {
        this.workerId = workerId;
        this.runId = runId;
        this.script = script;
        this.session = session;
        this.relPath = relPath;
        this.size = size;
        this.sha256 = sha256;
    }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }
    public String getRelPath() { return relPath; }
    public void setRelPath(String relPath) { this.relPath = relPath; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
