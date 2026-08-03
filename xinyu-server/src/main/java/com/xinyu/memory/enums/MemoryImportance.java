package com.xinyu.memory.enums;

/**
 * 记忆重要度（注入时 HIGH 优先）
 */
public enum MemoryImportance {
    /** 核心: 姓名/职业/重要关系等长期身份信息 */
    HIGH(3),
    /** 中等: 偏好/习惯/风格 */
    MEDIUM(2),
    /** 次要: 偶发事实 */
    LOW(1);

    private final int weight;

    MemoryImportance(int weight) {
        this.weight = weight;
    }

    /** 排序权重, 数值越大越优先注入 */
    public int weight() {
        return weight;
    }
}
