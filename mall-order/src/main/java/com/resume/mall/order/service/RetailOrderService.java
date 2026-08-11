package com.resume.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.resume.mall.common.PageResult;
import com.resume.mall.order.dto.AddCartItemRequest;
import com.resume.mall.order.dto.CheckoutCartRequest;
import com.resume.mall.order.dto.CreateOrderRequest;
import com.resume.mall.order.dto.RetailOrderResponse;
import com.resume.mall.order.dto.StockCheckResponse;
import com.resume.mall.order.dto.UpdateCartItemRequest;
import com.resume.mall.order.entity.ProductInventory;
import com.resume.mall.order.entity.ProductSnapshot;
import com.resume.mall.order.entity.RetailOrder;
import com.resume.mall.order.entity.ShoppingCartItem;
import com.resume.mall.order.exception.OrderBizException;
import com.resume.mall.order.mapper.ProductInventoryMapper;
import com.resume.mall.order.mapper.ProductSnapshotMapper;
import com.resume.mall.order.mapper.RetailOrderMapper;
import com.resume.mall.order.mapper.ShoppingCartItemMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RetailOrderService {
    private static final String CREATED = "CREATED";
    private static final String PAID = "PAID";
    private static final String COMPLETED = "COMPLETED";

    private final ProductSnapshotMapper productMapper;
    private final ProductInventoryMapper inventoryMapper;
    private final RetailOrderMapper orderMapper;
    private final ShoppingCartItemMapper cartItemMapper;
    private final JdbcClient jdbcClient;

    public RetailOrderService(
            ProductSnapshotMapper productMapper,
            ProductInventoryMapper inventoryMapper,
            RetailOrderMapper orderMapper,
            ShoppingCartItemMapper cartItemMapper,
            JdbcClient jdbcClient) {
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.orderMapper = orderMapper;
        this.cartItemMapper = cartItemMapper;
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public RetailOrderResponse create(long userId, CreateOrderRequest request, String idempotencyKey) {
        RetailOrder existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return RetailOrderResponse.from(existing);
        }

        ProductSnapshot product = productMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getId, request.productId())
                .eq(ProductSnapshot::getStatus, 1));
        StockCheckResponse stockCheck = checkStock(product, request.productId(), request.quantity());
        if (!stockCheck.passed()) {
            int code = stockCheck.productAvailable() && stockCheck.inventoryExists() ? 409 : 400;
            throw new OrderBizException(code, stockCheck.reason(), stockCheck);
        }

        int reserved = inventoryMapper.reserveStock(request.productId(), request.quantity());
        if (reserved != 1) {
            StockCheckResponse latestStockCheck = checkStock(product, request.productId(), request.quantity());
            throw new OrderBizException(409, "stock changed, please retry", latestStockCheck);
        }

        RetailOrder order = new RetailOrder();
        order.setOrderNo("RO" + System.currentTimeMillis() + Math.abs(UUID.randomUUID().hashCode()));
        order.setUserId(userId);
        order.setProductId(request.productId());
        order.setQuantity(request.quantity());
        order.setAmountCent(product.getPriceCent() * request.quantity());
        order.setStatus(CREATED);
        order.setIdempotencyKey(idempotencyKey);

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ignored) {
            return RetailOrderResponse.from(findByIdempotencyKey(idempotencyKey));
        }
        return RetailOrderResponse.from(orderMapper.selectById(order.getId()));
    }

    @Transactional
    public RetailOrderResponse pay(String orderNo, long payerUserId, boolean allowHelpPay) {
        RetailOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (PAID.equals(order.getStatus()) || COMPLETED.equals(order.getStatus())) {
            return RetailOrderResponse.from(order);
        }
        if (!CREATED.equals(order.getStatus())) {
            throw new IllegalStateException("order cannot be paid in status " + order.getStatus());
        }
        if (!allowHelpPay && !order.getUserId().equals(payerUserId)) {
            throw new SecurityException("only the order owner can pay this order");
        }

        orderMapper.update(null, new LambdaUpdateWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo)
                .eq(RetailOrder::getStatus, CREATED)
                .set(RetailOrder::getStatus, PAID)
                .set(RetailOrder::getPayerUserId, payerUserId)
                .set(RetailOrder::getPaidAt, LocalDateTime.now()));
        return RetailOrderResponse.from(findByOrderNo(orderNo));
    }

    @Transactional
    public RetailOrderResponse complete(String orderNo) {
        RetailOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (COMPLETED.equals(order.getStatus())) {
            return RetailOrderResponse.from(order);
        }
        if (!PAID.equals(order.getStatus())) {
            throw new IllegalStateException("order cannot be completed in status " + order.getStatus());
        }

        int confirmed = inventoryMapper.confirmSale(order.getProductId(), order.getQuantity());
        if (confirmed != 1) {
            throw new IllegalStateException("locked stock is inconsistent");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo)
                .eq(RetailOrder::getStatus, PAID)
                .set(RetailOrder::getStatus, COMPLETED)
                .set(RetailOrder::getCompletedAt, LocalDateTime.now()));
        return RetailOrderResponse.from(findByOrderNo(orderNo));
    }

    @Transactional
    public void deleteOrder(String orderNo, long userId) {
        RetailOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new NoSuchElementException("order not found");
        }
        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("only the order owner can delete this order");
        }
        if (!CREATED.equals(order.getStatus())) {
            throw new IllegalStateException("only CREATED orders can be deleted");
        }
        int released = inventoryMapper.releaseStock(order.getProductId(), order.getQuantity());
        if (released != 1) {
            throw new IllegalStateException("locked stock is inconsistent");
        }
        orderMapper.deleteById(order.getId());
    }

    public RetailOrder findByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo));
    }

    private RetailOrder findByIdempotencyKey(String idempotencyKey) {
        return orderMapper.selectOne(new LambdaQueryWrapper<RetailOrder>()
                .eq(RetailOrder::getIdempotencyKey, idempotencyKey));
    }

    public StockCheckResponse checkStock(long productId, int quantity) {
        ProductSnapshot product = productMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getId, productId)
                .eq(ProductSnapshot::getStatus, 1));
        return checkStock(product, productId, quantity);
    }

    @Transactional
    public Map<String, Object> addCartItem(long userId, AddCartItemRequest request) {
        checkCartProduct(request.productId(), request.quantity());
        ShoppingCartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId)
                .eq(ShoppingCartItem::getProductId, request.productId()));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.quantity());
            if (request.selected() != null) {
                existing.setSelected(toSelected(request.selected()));
            }
            cartItemMapper.updateById(existing);
            return getCartItem(userId, existing.getId());
        }

        ShoppingCartItem item = new ShoppingCartItem();
        item.setUserId(userId);
        item.setProductId(request.productId());
        item.setQuantity(request.quantity());
        item.setSelected(toSelected(request.selected() == null || request.selected()));
        cartItemMapper.insert(item);
        return getCartItem(userId, item.getId());
    }

    public List<Map<String, Object>> listCartItems(long userId, Boolean selected) {
        StringBuilder where = new StringBuilder(" where c.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (selected != null) {
            where.append(" and c.selected = ?");
            params.add(toSelected(selected));
        }
        return queryRows("""
                        select c.id, c.user_id, c.product_id, p.name as product_name, p.price_cent,
                               c.quantity, c.selected, c.created_at, c.updated_at
                        from shopping_cart_item c
                        join product p on p.id = c.product_id
                        """ + where + " order by c.updated_at desc, c.id desc",
                params);
    }

    @Transactional
    public Map<String, Object> updateCartItem(long userId, long itemId, UpdateCartItemRequest request) {
        ShoppingCartItem item = requireCartItem(userId, itemId);
        if (request.quantity() != null) {
            checkCartProduct(item.getProductId(), request.quantity());
            item.setQuantity(request.quantity());
        }
        if (request.selected() != null) {
            item.setSelected(toSelected(request.selected()));
        }
        cartItemMapper.updateById(item);
        return getCartItem(userId, itemId);
    }

    @Transactional
    public void deleteCartItem(long userId, long itemId) {
        ShoppingCartItem item = requireCartItem(userId, itemId);
        cartItemMapper.deleteById(item.getId());
    }

    @Transactional
    public void clearCart(long userId, Boolean selected) {
        LambdaQueryWrapper<ShoppingCartItem> wrapper = new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId);
        if (selected != null) {
            wrapper.eq(ShoppingCartItem::getSelected, toSelected(selected));
        }
        cartItemMapper.delete(wrapper);
    }

    @Transactional
    public List<RetailOrderResponse> checkoutCart(long userId, CheckoutCartRequest request) {
        List<ShoppingCartItem> items = findCheckoutItems(userId, request == null ? null : request.itemIds());
        if (items.isEmpty()) {
            throw new NoSuchElementException("cart item not found");
        }
        List<RetailOrderResponse> orders = new ArrayList<>();
        for (ShoppingCartItem item : items) {
            RetailOrderResponse order = create(
                    userId,
                    new CreateOrderRequest(item.getProductId(), item.getQuantity()),
                    "CART-" + item.getId() + "-" + UUID.randomUUID());
            orders.add(order);
        }
        for (ShoppingCartItem item : items) {
            cartItemMapper.deleteById(item.getId());
        }
        return orders;
    }

    private StockCheckResponse checkStock(ProductSnapshot product, long productId, int quantity) {
        if (quantity < 1) {
            return new StockCheckResponse(
                    productId, quantity, product != null, false,
                    null, null, null, false, "quantity must be greater than 0");
        }
        if (product == null) {
            return new StockCheckResponse(
                    productId, quantity, false, false,
                    null, null, null, false, "product not found or inactive");
        }

        ProductInventory inventory = inventoryMapper.selectById(productId);
        if (inventory == null) {
            return new StockCheckResponse(
                    productId, quantity, true, false,
                    null, null, null, false, "product inventory not found");
        }
        if (inventory.getAvailableStock() < quantity) {
            return new StockCheckResponse(
                    productId, quantity, true, true,
                    inventory.getAvailableStock(),
                    inventory.getLockedStock(),
                    inventory.getSoldStock(),
                    false,
                    "insufficient stock");
        }
        return new StockCheckResponse(
                productId, quantity, true, true,
                inventory.getAvailableStock(),
                inventory.getLockedStock(),
                inventory.getSoldStock(),
                true,
                "stock available");
    }

    private void checkCartProduct(long productId, int quantity) {
        StockCheckResponse stockCheck = checkStock(productId, quantity);
        if (!stockCheck.productAvailable()) {
            throw new IllegalArgumentException(stockCheck.reason());
        }
    }

    private Map<String, Object> getCartItem(long userId, long itemId) {
        List<Map<String, Object>> rows = queryRows("""
                        select c.id, c.user_id, c.product_id, p.name as product_name, p.price_cent,
                               c.quantity, c.selected, c.created_at, c.updated_at
                        from shopping_cart_item c
                        join product p on p.id = c.product_id
                        where c.user_id = ? and c.id = ?
                        """,
                List.of(userId, itemId));
        if (rows.isEmpty()) {
            throw new NoSuchElementException("cart item not found");
        }
        return rows.get(0);
    }

    private ShoppingCartItem requireCartItem(long userId, long itemId) {
        ShoppingCartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId)
                .eq(ShoppingCartItem::getId, itemId));
        if (item == null) {
            throw new NoSuchElementException("cart item not found");
        }
        return item;
    }

    private List<ShoppingCartItem> findCheckoutItems(long userId, List<Long> itemIds) {
        LambdaQueryWrapper<ShoppingCartItem> wrapper = new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId);
        if (itemIds == null || itemIds.isEmpty()) {
            wrapper.eq(ShoppingCartItem::getSelected, 1);
        } else {
            wrapper.in(ShoppingCartItem::getId, itemIds);
        }
        return cartItemMapper.selectList(wrapper.orderByAsc(ShoppingCartItem::getId));
    }

    private int toSelected(boolean selected) {
        return selected ? 1 : 0;
    }

    public PageResult<Map<String, Object>> pageRetailOrders(
            int pageNum,
            int pageSize,
            Long userId,
            Long productId,
            String status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" and user_id = ?");
            params.add(userId);
        }
        if (productId != null) {
            where.append(" and product_id = ?");
            params.add(productId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            params.add(status.trim());
        }
        if (createdFrom != null) {
            where.append(" and created_at >= ?");
            params.add(createdFrom);
        }
        if (createdTo != null) {
            where.append(" and created_at <= ?");
            params.add(createdTo);
        }

        long total = queryCount("select count(*) from retail_order" + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select order_no, user_id, product_id, quantity, amount_cent, status,
                               idempotency_key, payer_user_id, paid_at, completed_at, created_at, updated_at
                        from retail_order
                        """ + where + " order by created_at desc, id desc limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    private int normalizePageNum(int pageNum) {
        return Math.max(pageNum, 1);
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private long queryCount(String sql, List<Object> params) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        return spec.query(Long.class).single();
    }

    private List<Map<String, Object>> queryRows(String sql, List<Object> params) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        return spec.query().listOfRows();
    }
}
