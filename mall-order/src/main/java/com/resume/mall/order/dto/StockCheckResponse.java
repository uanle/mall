package com.resume.mall.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "下单库存检查结果")
public record StockCheckResponse(
        @Schema(description = "商品 ID", example = "2001")
        Long productId,
        @Schema(description = "请求购买数量", example = "1")
        Integer requestedQuantity,
        @Schema(description = "商品是否存在且上架", example = "true")
        boolean productAvailable,
        @Schema(description = "库存记录是否存在", example = "true")
        boolean inventoryExists,
        @Schema(description = "当前可售库存", example = "100")
        Integer availableStock,
        @Schema(description = "当前锁定库存", example = "2")
        Integer lockedStock,
        @Schema(description = "当前已售库存", example = "30")
        Integer soldStock,
        @Schema(description = "是否允许继续下单", example = "true")
        boolean passed,
        @Schema(description = "失败原因或通过说明", example = "stock available")
        String reason
) {
}
