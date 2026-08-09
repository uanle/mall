package com.resume.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("product_inventory")
public class ProductInventory {
    @TableId(type = IdType.INPUT)
    private Long productId;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public Integer getLockedStock() {
        return lockedStock;
    }

    public void setLockedStock(Integer lockedStock) {
        this.lockedStock = lockedStock;
    }

    public Integer getSoldStock() {
        return soldStock;
    }

    public void setSoldStock(Integer soldStock) {
        this.soldStock = soldStock;
    }
}
