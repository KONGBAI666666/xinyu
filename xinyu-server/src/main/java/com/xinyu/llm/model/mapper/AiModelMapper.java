package com.xinyu.llm.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.llm.model.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {
}
