package com.agentdsl.runtime.autonomous;

/**
 * 用户交互接口。
 * 解耦 CLI / Web / 测试等不同场景下的用户确认交互。
 */
public interface UserInteraction {

    /**
     * Plan 模式下展示执行计划并获取用户反馈。
     *
     * @param plan 格式化的执行计划文本
     * @return 用户反馈（批准/修改/拒绝）
     */
    PlanFeedback confirmPlan(String plan);

    /**
     * 超过 max_steps 后询问用户是否继续执行。
     *
     * @param currentStep     当前已执行步骤数
     * @param maxSteps        配置的最大步骤数
     * @param progressSummary 当前进度摘要
     * @return true = 继续执行, false = 终止
     */
    boolean confirmContinue(int currentStep, int maxSteps, String progressSummary);

    /**
     * 输出自主执行过程中的实时日志信息。
     *
     * @param message 要显示的消息
     */
    void showProgress(String message);
}
