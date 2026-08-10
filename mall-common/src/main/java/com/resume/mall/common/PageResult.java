package com.resume.mall.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "分页响应")
public record PageResult<T>(
        @Schema(description = "当前页码，从 1 开始", example = "1")
        long pageNum,
        @Schema(description = "每页数量", example = "10")
        long pageSize,
        @Schema(description = "总记录数", example = "25")
        long total,
        @Schema(description = "总页数", example = "3")
        long pages,
        @Schema(description = "当前页数据")
        List<T> records
) {
    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> records) {
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(pageNum, pageSize, total, pages, records);
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}
