package com.resume.mall.common;

public final class RedisKeys {
    private RedisKeys() {
    }

    public static String seckillStock(long activityId) {
        return "seckill:stock:" + activityId;
    }

    public static String seckillUser(long activityId, long userId) {
        return "seckill:user:" + activityId + ":" + userId;
    }

    public static String seckillRequest(String requestId) {
        return "seckill:request:" + requestId;
    }

    public static String productCache(long productId) {
        return "cache:product:" + productId;
    }

    public static String productMutex(long productId) {
        return "lock:product:" + productId;
    }

    public static String userAuth(String username) {
        return "cache:user:auth:" + username;
    }

    public static String userById(long userId) {
        return "cache:user:id:" + userId;
    }
}
