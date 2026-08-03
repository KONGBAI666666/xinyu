package com.xinyu.character.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色收藏实体（对应 character_favorite 表）
 *
 * <p>uk(user_id, character_id) 防重复收藏; 收藏/取消收藏走幂等 upsert / delete。
 */
@Data
@TableName("character_favorite")
public class CharacterFavorite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long characterId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
