package com.resume.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resume.mall.order.entity.ProductInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductInventoryMapper extends BaseMapper<ProductInventory> {
    @Update("""
            update product_inventory
            set available_stock = available_stock - #{quantity},
                locked_stock = locked_stock + #{quantity}
            where product_id = #{productId}
              and available_stock >= #{quantity}
            """)
    int reserveStock(@Param("productId") long productId, @Param("quantity") int quantity);

    @Update("""
            update product_inventory
            set locked_stock = locked_stock - #{quantity},
                sold_stock = sold_stock + #{quantity}
            where product_id = #{productId}
              and locked_stock >= #{quantity}
            """)
    int confirmSale(@Param("productId") long productId, @Param("quantity") int quantity);

    @Update("""
            update product_inventory
            set available_stock = available_stock + #{quantity},
                locked_stock = locked_stock - #{quantity}
            where product_id = #{productId}
              and locked_stock >= #{quantity}
            """)
    int releaseStock(@Param("productId") long productId, @Param("quantity") int quantity);
}
