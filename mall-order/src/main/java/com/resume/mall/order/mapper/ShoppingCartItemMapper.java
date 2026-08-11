package com.resume.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resume.mall.order.entity.ShoppingCartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShoppingCartItemMapper extends BaseMapper<ShoppingCartItem> {
}
