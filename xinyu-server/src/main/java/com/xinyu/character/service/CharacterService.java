package com.xinyu.character.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.character.entity.AiCharacter;

/**
 * AI 角色服务: character 模块对外唯一入口
 *
 * <p>M1-3 只读切片（聊天链路取 greeting/system_prompt/采样参数）,
 * 角色 CRUD、广场查询、收藏、状态机流转在 M2 补充。
 */
public interface CharacterService extends IService<AiCharacter> {
}
