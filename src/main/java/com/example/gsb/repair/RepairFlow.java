package com.example.gsb.repair;

import java.util.Objects;
import java.util.function.Function;

/**
 * 一个完整的"检测→决策→修复→复核"流程定义。
 *
 * <ul>
 *   <li>{@code detect}：检测步骤（必填）</li>
 *   <li>{@code precondition}：修复前置条件（可选，缺省恒满足）</li>
 *   <li>{@code repair}：幂等修复步骤（必填）</li>
 *   <li>{@code verify}：修复后复核步骤（必填）</li>
 *   <li>{@code rollback}：回滚步骤（可选，策略为 ROLLBACK 时必填）</li>
 *   <li>{@code verifyFailurePolicy}：复核不通过的处理策略（缺省 ROLLBACK）</li>
 * </ul>
 */
public final class RepairFlow<T> {

    private final String name;
    private final Function<T, String> targetIdExtractor;
    private final DetectStep<T> detect;
    private final Precondition<T> precondition;
    private final RepairStep<T> repair;
    private final VerifyStep<T> verify;
    private final RollbackStep<T> rollback;
    private final VerifyFailurePolicy verifyFailurePolicy;

    private RepairFlow(Builder<T> builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.targetIdExtractor = Objects.requireNonNull(builder.targetIdExtractor, "targetIdExtractor");
        this.detect = Objects.requireNonNull(builder.detect, "detect");
        this.precondition = builder.precondition != null
                ? builder.precondition
                : (t, ctx) -> PreconditionResult.ok();
        this.repair = Objects.requireNonNull(builder.repair, "repair");
        this.verify = Objects.requireNonNull(builder.verify, "verify");
        this.rollback = builder.rollback;
        this.verifyFailurePolicy = builder.verifyFailurePolicy;
        if (verifyFailurePolicy == VerifyFailurePolicy.ROLLBACK && rollback == null) {
            throw new IllegalStateException("复核失败策略为 ROLLBACK 时必须提供 rollback 步骤");
        }
    }

    public static <T> Builder<T> builder(Class<T> targetType, String name,
                                         Function<T, String> targetIdExtractor) {
        return new Builder<>(name, targetIdExtractor);
    }

    public String name() {
        return name;
    }

    public String targetIdOf(T target) {
        return targetIdExtractor.apply(target);
    }

    public DetectStep<T> detect() {
        return detect;
    }

    public Precondition<T> precondition() {
        return precondition;
    }

    public RepairStep<T> repair() {
        return repair;
    }

    public VerifyStep<T> verify() {
        return verify;
    }

    public RollbackStep<T> rollback() {
        return rollback;
    }

    public VerifyFailurePolicy verifyFailurePolicy() {
        return verifyFailurePolicy;
    }

    /** {@link RepairFlow} 构建器。 */
    public static final class Builder<T> {

        private final String name;
        private final Function<T, String> targetIdExtractor;
        private DetectStep<T> detect;
        private Precondition<T> precondition;
        private RepairStep<T> repair;
        private VerifyStep<T> verify;
        private RollbackStep<T> rollback;
        private VerifyFailurePolicy verifyFailurePolicy = VerifyFailurePolicy.ROLLBACK;

        private Builder(String name, Function<T, String> targetIdExtractor) {
            this.name = name;
            this.targetIdExtractor = targetIdExtractor;
        }

        public Builder<T> detect(DetectStep<T> step) {
            this.detect = step;
            return this;
        }

        public Builder<T> precondition(Precondition<T> precondition) {
            this.precondition = precondition;
            return this;
        }

        public Builder<T> repair(RepairStep<T> step) {
            this.repair = step;
            return this;
        }

        public Builder<T> verify(VerifyStep<T> step) {
            this.verify = step;
            return this;
        }

        public Builder<T> rollback(RollbackStep<T> step) {
            this.rollback = step;
            return this;
        }

        public Builder<T> verifyFailurePolicy(VerifyFailurePolicy policy) {
            this.verifyFailurePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public RepairFlow<T> build() {
            return new RepairFlow<>(this);
        }
    }
}
