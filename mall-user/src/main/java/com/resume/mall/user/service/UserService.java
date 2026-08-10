package com.resume.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.JwtClaims;
import com.resume.mall.common.JwtUtil;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.user.dto.LoginRequest;
import com.resume.mall.user.dto.LoginResponse;
import com.resume.mall.user.dto.RegisterRequest;
import com.resume.mall.user.dto.UpdateUserRequest;
import com.resume.mall.user.dto.UserCache;
import com.resume.mall.user.dto.UserResponse;
import com.resume.mall.user.entity.MallUser;
import com.resume.mall.user.mapper.MallUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class UserService {
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String LEVEL_NONE = "NONE";
    private static final String LEVEL_NORMAL = "NORMAL";
    private static final String LEVEL_VIP = "VIP";
    private static final String LEVEL_SVIP = "SVIP";
    private static final int STATUS_ENABLED = 1;

    private final MallUserMapper userMapper;
    private final JdbcClient jdbcClient;
    private final RedisTemplate<String, UserCache> userCacheRedisTemplate;
    private final String jwtSecret;
    private final long jwtTtlSeconds;
    private final Duration userCacheTtl;

    public UserService(
            MallUserMapper userMapper,
            JdbcClient jdbcClient,
            RedisTemplate<String, UserCache> userCacheRedisTemplate,
            @Value("${mall.jwt.secret}") String jwtSecret,
            @Value("${mall.jwt.ttl-seconds}") long jwtTtlSeconds,
            @Value("${mall.user-cache.ttl-seconds}") long userCacheTtlSeconds) {
        this.userMapper = userMapper;
        this.jdbcClient = jdbcClient;
        this.userCacheRedisTemplate = userCacheRedisTemplate;
        this.jwtSecret = jwtSecret;
        this.jwtTtlSeconds = jwtTtlSeconds;
        this.userCacheTtl = Duration.ofSeconds(userCacheTtlSeconds);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        MallUser user = new MallUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(hashPassword(request.password()));
        user.setRole(ROLE_USER);
        user.setLevel(LEVEL_NORMAL);
        user.setStatus(STATUS_ENABLED);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("username already exists");
        }
        MallUser saved = userMapper.selectById(user.getId());
        cacheUser(saved);
        return UserResponse.from(saved);
    }

    public LoginResponse login(LoginRequest request) {
        MallUser user = findByUsernameWithCache(request.username().trim());
        if (user == null || !user.getPasswordHash().equals(hashPassword(request.password()))) {
            throw new IllegalArgumentException("invalid username or password");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new IllegalStateException("user disabled");
        }

        long exp = Instant.now().plusSeconds(jwtTtlSeconds).getEpochSecond();
        String token = JwtUtil.createToken(new JwtClaims(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLevel(),
                exp), jwtSecret);
        return new LoginResponse("Bearer", token, jwtTtlSeconds, UserResponse.from(user));
    }

    public UserResponse getById(long userId) {
        MallUser user = findByIdWithCache(userId);
        if (user == null) {
            throw new NoSuchElementException("user not found");
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(long userId, UpdateUserRequest request) {
        MallUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("user not found");
        }
        String targetRole = request.role() != null && !request.role().isBlank() ? request.role() : user.getRole();
        String targetLevel = request.level() != null && !request.level().isBlank() ? request.level() : user.getLevel();
        validateRoleAndLevel(targetRole, targetLevel);
        user.setRole(targetRole);
        user.setLevel(targetLevel);
        if (request.status() != null) {
            if (request.status() != 0 && request.status() != 1) {
                throw new IllegalArgumentException("status must be 0 or 1");
            }
            user.setStatus(request.status());
        }
        userMapper.updateById(user);
        evictUserCache(user);
        MallUser updated = userMapper.selectById(userId);
        cacheUser(updated);
        return UserResponse.from(updated);
    }

    public PageResult<Map<String, Object>> pageUsers(int pageNum, int pageSize, String username, String role, String level, Integer status) {
        int normalizedPageNum = Math.max(pageNum, 1);
        int normalizedPageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            where.append(" and username like ?");
            params.add("%" + username.trim() + "%");
        }
        if (role != null && !role.isBlank()) {
            where.append(" and role = ?");
            params.add(role.trim());
        }
        if (level != null && !level.isBlank()) {
            where.append(" and level = ?");
            params.add(level.trim());
        }
        if (status != null) {
            where.append(" and status = ?");
            params.add(status);
        }

        long total = queryCount("select count(*) from mall_user" + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select id, username, role, level, status, created_at, updated_at
                        from mall_user
                        """ + where + " order by created_at desc, id desc limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    private void validateRoleAndLevel(String role, String level) {
        if (ROLE_ADMIN.equals(role)) {
            if (!LEVEL_NONE.equals(level)) {
                throw new IllegalArgumentException("admin level must be NONE");
            }
            return;
        }
        if (ROLE_USER.equals(role)) {
            if (!LEVEL_NORMAL.equals(level) && !LEVEL_VIP.equals(level) && !LEVEL_SVIP.equals(level)) {
                throw new IllegalArgumentException("user level must be NORMAL, VIP or SVIP");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported role: " + role);
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(("mall:" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash password", ex);
        }
    }

    private MallUser findByUsernameWithCache(String username) {
        UserCache cached = userCacheRedisTemplate.opsForValue().get(RedisKeys.userAuth(username));
        if (cached != null) {
            return cached.toEntity();
        }

        MallUser user = userMapper.selectOne(new LambdaQueryWrapper<MallUser>()
                .eq(MallUser::getUsername, username));
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    private MallUser findByIdWithCache(long userId) {
        UserCache cached = userCacheRedisTemplate.opsForValue().get(RedisKeys.userById(userId));
        if (cached != null) {
            return cached.toEntity();
        }

        MallUser user = userMapper.selectById(userId);
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    private void cacheUser(MallUser user) {
        if (user == null) {
            return;
        }
        UserCache value = UserCache.from(user);
        userCacheRedisTemplate.opsForValue().set(RedisKeys.userAuth(user.getUsername()), value, userCacheTtl);
        userCacheRedisTemplate.opsForValue().set(RedisKeys.userById(user.getId()), value, userCacheTtl);
    }

    private void evictUserCache(MallUser user) {
        if (user == null) {
            return;
        }
        userCacheRedisTemplate.delete(RedisKeys.userAuth(user.getUsername()));
        userCacheRedisTemplate.delete(RedisKeys.userById(user.getId()));
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
