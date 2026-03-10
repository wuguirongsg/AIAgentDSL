package com.agentdsl.runtime.autonomous;

/**
 * 用户对执行计划的反馈。
 *
 * @param action    用户动作: APPROVE / MODIFY / REJECT
 * @param userInput 用户附带的文本输入（MODIFY 时为修改建议，其余可为 null）
 */
public record PlanFeedback(Action action, String userInput) {

    public enum Action {
        /** 批准计划，开始执行 */
        APPROVE,
        /** 修改计划，附带修改建议 */
        MODIFY,
        /** 拒绝计划，终止执行 */
        REJECT
    }

    public boolean isApproved() {
        return action == Action.APPROVE;
    }

    public boolean isModify() {
        return action == Action.MODIFY;
    }

    public boolean isRejected() {
        return action == Action.REJECT;
    }
}
