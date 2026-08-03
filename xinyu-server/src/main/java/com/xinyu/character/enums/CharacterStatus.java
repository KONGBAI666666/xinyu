package com.xinyu.character.enums;

/**
 * 角色状态机
 *
 * <p>流转规则（M2.2 简化版, 不做审核流程）:
 * <ul>
 *   <li>用户创建角色 → {@link #DRAFT}（仅创建者可见、可聊）</li>
 *   <li>用户发布 → {@link #PUBLISHED}（广场可见, M2.3 接入）</li>
 *   <li>用户下架 → {@link #OFFLINE}（广场不可见, 已有会话仍可聊）</li>
 *   <li>{@link #PENDING} 预留给 M4 管理后台审核, M2.2 不使用</li>
 * </ul>
 *
 * <p>创建会话的可见性校验: OFFICIAL 角色 PUBLISHED 可聊; 用户自建角色 DRAFT/PUBLISHED/OFFLINE 创建者均可聊。
 */
public enum CharacterStatus {
    /** 草稿: 仅创建者可见 */
    DRAFT,
    /** 待审核: M4 管理后台接入, M2.2 不使用 */
    PENDING,
    /** 已发布: 广场可见 */
    PUBLISHED,
    /** 已下架: 广场不可见, 已有会话仍可聊 */
    OFFLINE
}
