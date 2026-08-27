package com.gatf.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.TestCase;

/** Java importers — replaces the retired Python references + tools/import/test-importers.sh (G2). */
class ImportersTest {

    private static String res(String name) throws Exception {
        try (InputStream in = ImportersTest.class.getResourceAsStream("/imports/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void playwrightFixture() throws Exception {
        ImportResult r = new PlaywrightImporter().importSource(res("login.spec.ts"), "login");
        assertThat(r.content).contains("subtest \"walk-in registration\"")
                .contains("goto https://demo.gatf.example/login")   // beforeEach inlined into each test
                .contains("type \"Asha\" css@\"input.patient-fname\"")
                .contains("?? css@\"div.toast-success\" text =\"Patient registered\"")
                .contains("??+ text@\"Welcome\"");                   // expect(getByText) no longer mis-parsed
        assertThat(r.stats.get("hooks")).isEqualTo(1);
    }

    @Test
    void playwrightHooksNavAndMsSleep() {
        String src = "test.beforeEach(async ({ page }) => {\n  await page.goto('https://x.test/login');\n});\n"
                + "test('one', async ({ page }) => {\n  await page.goBack();\n  await page.reload();\n"
                + "  await page.waitForTimeout(1500);\n"
                + "  await expect(page.locator('#name')).toHaveValue('bob');\n"
                + "  await expect(page.locator('#agree')).toBeChecked();\n});\n";
        ImportResult r = new PlaywrightImporter().importSource(src, "t");
        assertThat(r.content).contains("goto https://x.test/login\n  back\n  refresh\n  sleep 1500\n")
                .contains("?? id@name attr@value =\"bob\"")
                .contains("?? id@agree attr@checked =\"true\"");
    }

    @Test
    void jmeterFixtureDefaultsInheritedHeadersQueryAndLoad() throws Exception {
        ImportResult r = new JMeterImporter().importSource(res("sample-plan.jmx"), "plan");
        assertThat(r.testCases).hasSize(2);
        TestCase login = r.testCases.get(0), list = r.testCases.get(1);
        assertThat(login.getBaseUrl()).isEqualTo("http://localhost:8880");   // HTTP Request Defaults
        assertThat(login.getMethod()).isEqualTo("POST");
        assertThat(list.getUrl()).isEqualTo("/api/users?page=1");           // GET args → query
        assertThat(list.getHeaders()).containsEntry("Accept", "application/json"); // inherited HeaderManager
        assertThat(r.loadConfig).containsEntry("concurrentUserSimulationNum", 50)
                .containsEntry("concurrentUserRampUpTime", 10000L)
                .containsEntry("loadTestingTime", 60000L);
    }
}
