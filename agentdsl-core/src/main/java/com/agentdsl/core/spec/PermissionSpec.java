package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具权限声明规范。
 * 声明工具需要的外部访问能力（网络、文件系统、数据库等），
 * 用于安全审计和沙箱策略。
 */
public class PermissionSpec {

    private List<String> networkPatterns = new ArrayList<>();
    private List<String> filePatterns = new ArrayList<>();
    private List<String> databases = new ArrayList<>();

    // --- Getters & Setters ---

    public List<String> getNetworkPatterns() {
        return networkPatterns;
    }

    public void setNetworkPatterns(List<String> networkPatterns) {
        this.networkPatterns = networkPatterns;
    }

    public void addNetworkPattern(String pattern) {
        this.networkPatterns.add(pattern);
    }

    public List<String> getFilePatterns() {
        return filePatterns;
    }

    public void setFilePatterns(List<String> filePatterns) {
        this.filePatterns = filePatterns;
    }

    public void addFilePattern(String pattern) {
        this.filePatterns.add(pattern);
    }

    public List<String> getDatabases() {
        return databases;
    }

    public void setDatabases(List<String> databases) {
        this.databases = databases;
    }

    public void addDatabase(String database) {
        this.databases.add(database);
    }

    @Override
    public String toString() {
        return "PermissionSpec{" +
                "network=" + networkPatterns.size() +
                ", files=" + filePatterns.size() +
                ", databases=" + databases.size() +
                '}';
    }
}
