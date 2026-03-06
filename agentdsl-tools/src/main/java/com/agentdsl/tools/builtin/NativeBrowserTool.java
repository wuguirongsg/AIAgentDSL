package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * 原生 Playwright 浏览器工具。
 * 封装基础的网页访问、点击、输入等操作，供大模型控制无头浏览器。
 */
public class NativeBrowserTool implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final Page page;

    public NativeBrowserTool() {
        this(false); // 默认无头模式
    }

    public NativeBrowserTool(boolean headless) {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        this.page = browser.newPage();
    }

    @AgentTool(description = "在浏览器中打开指定的网页 URL")
    public String navigate(@ToolParam(description = "完整的 URL 地址，例如 https://www.google.com") String url) {
        page.navigate(url);
        return "已成功导航至: " + page.title();
    }

    @AgentTool(description = "点击当前网页上的特定元素")
    public String click(@ToolParam(description = "CSS 选择器或 XPath，例如 #login-btn 或 text=登录") String selector) {
        page.click(selector);
        return "已成功点击元素: " + selector;
    }

    @AgentTool(description = "在当前网页的选定输入框中填入文本")
    public String fill(
            @ToolParam(description = "输入框的 CSS 选择器或 XPath") String selector,
            @ToolParam(description = "要填入的文本内容") String text) {
        page.fill(selector, text);
        return "已向 " + selector + " 填入文本: " + text;
    }

    @AgentTool(description = "获取当前网页的纯文本内容，用于阅读和分析")
    public String getPageText() {
        return page.innerText("body");
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
