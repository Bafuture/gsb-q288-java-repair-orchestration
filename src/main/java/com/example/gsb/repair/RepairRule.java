package com.example.gsb.repair;

import java.util.Objects;

/**
 * 修复规则：将一类问题绑定到其修复、复核（必填）、前置条件（可选）、
 * 回滚（可选，取决于复核失败策略）以及复核失败处置策略（可覆盖全局默认）。
 */
public final class RepairRule {

    private final String issueType;
    private final RepairAction action;
    private final RecheckAction recheck;
    private final RollbackAction rollback;
    private final RepairPrecondition precondition;
    private final RecheckFailurePolicy failurePolicy;

    private RepairRule(Builder builder) {
        this.issueType = builder.issueType;
        this.action = builder.action;
        this.recheck = builder.recheck;
        this.rollback = builder.rollback;
        this.precondition = builder.precondition;
        this.failurePolicy = builder.failurePolicy;
    }

    public static Builder builder(String issueType) {
        return new Builder(issueType);
    }

    public String issueType() {
        return issueType;
    }

    public RepairAction action() {
        return action;
    }

    public RecheckAction recheck() {
        return recheck;
    }

    public RollbackAction rollback() {
        return rollback;
    }

    public RepairPrecondition precondition() {
        return precondition;
    }

    /** 规则级策略覆盖；null 表示沿用编排器全局默认。 */
    public RecheckFailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public static final class Builder {

        private final String issueType;
        private RepairAction action;
        private RecheckAction recheck;
        private RollbackAction rollback;
        private RepairPrecondition precondition;
        private RecheckFailurePolicy failurePolicy;

        private Builder(String issueType) {
            this.issueType = Objects.requireNonNull(issueType, "issueType");
        }

        public Builder action(RepairAction action) {
            this.action = action;
            return this;
        }

        public Builder recheck(RecheckAction recheck) {
            this.recheck = recheck;
            return this;
        }

        public Builder rollback(RollbackAction rollback) {
            this.rollback = rollback;
            return this;
        }

        public Builder precondition(RepairPrecondition precondition) {
            this.precondition = precondition;
            return this;
        }

        public Builder failurePolicy(RecheckFailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
            return this;
        }

        public RepairRule build() {
            Objects.requireNonNull(action, "repair action");
            Objects.requireNonNull(recheck, "recheck action");
            return new RepairRule(this);
        }
    }
}
