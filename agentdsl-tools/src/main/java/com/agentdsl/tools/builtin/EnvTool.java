package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * 环境感知工具。
 * 获取操作系统和硬件信息。
 */
public class EnvTool {
    private static final Logger log = LoggerFactory.getLogger(EnvTool.class);

    @AgentTool(name = "get_os_info", description = "获取当前操作系统的详细信息（名称、版本、架构）")
    public String getOsInfo() {
        try {
            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");
            return String.format("OS Name: %s\nOS Version: %s\nOS Architecture: %s", osName, osVersion, osArch);
        } catch (Exception e) {
            log.error("获取操作系统信息失败", e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "get_hardware_info", description = "获取当前计算机硬件配置信息（如 CPU 核数、系统负载等）")
    public String getHardwareInfo() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int processors = osBean.getAvailableProcessors();
            double systemLoad = osBean.getSystemLoadAverage();
            
            return String.format("Available Processors (Cores): %d\nSystem Load Average: %.2f", processors, systemLoad);
        } catch (Exception e) {
            log.error("获取硬件信息失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
