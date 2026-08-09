package com.resume.mall.order;

import com.resume.mall.common.ApiResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final JdbcClient jdbcClient;

    public OrderController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @GetMapping("/{requestId}")
    public ApiResponse<Map<String, Object>> byRequestId(@PathVariable("requestId") String requestId) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select order_no, user_id, activity_id, product_id, amount_cent, status, created_at
                        from trade_order
                        where request_id = ?
                        """)
                .param(requestId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            return ApiResponse.fail(404, "order not found");
        }
        return ApiResponse.ok(rows.get(0));
    }
}
