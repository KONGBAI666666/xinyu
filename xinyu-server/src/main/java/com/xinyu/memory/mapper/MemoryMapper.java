package com.xinyu.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemoryMapper extends BaseMapper<Memory> {
}
