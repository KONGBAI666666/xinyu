package com.xinyu.character.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.mapper.AiCharacterMapper;
import com.xinyu.character.service.CharacterService;
import org.springframework.stereotype.Service;

/**
 * AI 角色服务实现（M1-3 只读切片, 业务方法 M2 补充）
 */
@Service
public class CharacterServiceImpl extends ServiceImpl<AiCharacterMapper, AiCharacter>
        implements CharacterService {
}
