package com.xinyu.memory.enums;

/**
 * 记忆状态
 */
public enum MemoryStatus {
    /** 启用: 参与注入 */
    ACTIVE,
    /** 停用: 不参与注入, 但保留记录（用户可重新启用） */
    DISABLED
}
