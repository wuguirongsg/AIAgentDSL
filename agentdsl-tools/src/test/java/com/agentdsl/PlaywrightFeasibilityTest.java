package com.agentdsl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import org.junit.jupiter.api.Test;

public class PlaywrightFeasibilityTest {
    @Test
    void runTest() {
        System.out.println("Starting Playwright Feasibility Test...");
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            System.out.println("Navigating to https://example.com ...");
            page.navigate("https://example.com");
            System.out.println("Page Title: " + page.title());
            System.out.println("Body Text snippet: "
                    + page.innerText("body").substring(0, Math.min(page.innerText("body").length(), 50)));
            browser.close();
            System.out.println("Test completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Test failed: " + e.getMessage());
        }
    }
}
