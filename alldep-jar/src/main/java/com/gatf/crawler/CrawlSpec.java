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
package com.gatf.crawler;

/**
 * Configuration for a {@link PortalCrawler} run (CLI flags / JSON body).
 */
public class CrawlSpec {
    private String seed;
    private int maxPages = 25;
    private int maxDepth = 3;
    private boolean sameOrigin = true;
    private boolean submitForms = false;
    private String outDir = ".";
    private String suiteName = "crawl-suite";

    // Optional credentialed crawl — the emitted suite logs in first. Selectors may be given explicitly
    // (seleasy form e.g. id@username) or auto-discovered from the login page's password form.
    private String loginUrl;
    private String username;
    private String password;
    private String userSel;
    private String passSel;
    private String submitSel;

    // ---- SPA crawler (G3) ----
    /** Links matching this are never followed (keeps a credentialed session alive, avoids destructive GETs). */
    public static final String DEFAULT_EXCLUDE = "(?i)(log-?out|sign-?out|/logoff|/delete|/remove|/destroy)";
    private String exclude;
    private long settleMs = 5000;   // max wait for a page to finish rendering
    private long pollMs = 300;      // DOM-stability poll interval
    private boolean headless = true;
    private String browser = "chrome";   // G9: chrome | firefox | edge

    public String getExclude() { return exclude; }
    public void setExclude(String exclude) { this.exclude = exclude; }
    public long getSettleMs() { return settleMs; }
    public void setSettleMs(long settleMs) { this.settleMs = settleMs; }
    public long getPollMs() { return pollMs; }
    public void setPollMs(long pollMs) { this.pollMs = pollMs; }
    public String getBrowser() { return browser; }
    public void setBrowser(String b) {
        String v = b == null ? "" : b.trim().toLowerCase(java.util.Locale.ROOT);
        this.browser = v.equals("firefox") || v.equals("edge") ? v : "chrome";
    }
    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }

    public boolean isCredentialed() {
        return loginUrl != null && username != null && password != null;
    }

    public String getSeed() { return seed; }
    public void setSeed(String seed) { this.seed = seed; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public boolean isSameOrigin() { return sameOrigin; }
    public void setSameOrigin(boolean sameOrigin) { this.sameOrigin = sameOrigin; }
    public boolean isSubmitForms() { return submitForms; }
    public void setSubmitForms(boolean submitForms) { this.submitForms = submitForms; }
    public String getOutDir() { return outDir; }
    public void setOutDir(String outDir) { this.outDir = outDir; }
    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }
    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getUserSel() { return userSel; }
    public void setUserSel(String userSel) { this.userSel = userSel; }
    public String getPassSel() { return passSel; }
    public void setPassSel(String passSel) { this.passSel = passSel; }
    public String getSubmitSel() { return submitSel; }
    public void setSubmitSel(String submitSel) { this.submitSel = submitSel; }
}
