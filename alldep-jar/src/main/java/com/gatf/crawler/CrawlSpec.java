/*
    Copyright 2013-2019, Sumeet Chhetri

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
 * Configuration for a {@link PortalCrawler} run. Mirrors the flags of the reference
 * implementation tools/crawler/portal_crawler.py.
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
