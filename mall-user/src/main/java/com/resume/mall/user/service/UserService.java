package com.resume.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.JwtClaims;
import com.resume.mall.common.JwtUtil;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.user.dto.ChangePasswordRequest;
import com.resume.mall.user.dto.ChangeUsernameRequest;
import com.resume.mall.user.dto.LoginRequest;
import com.resume.mall.user.dto.LoginResponse;
import com.resume.mall.user.dto.RegisterRequest;
import com.resume.mall.user.dto.AuthUserCache;
import com.resume.mall.user.dto.TokenSessionCache;
import com.resume.mall.user.dto.UpdateUserRequest;
import com.resume.mall.user.dto.UserProfileCache;
import com.resume.mall.user.dto.UserResponse;
import com.resume.mall.user.entity.MallUser;
import com.resume.mall.user.exception.UserUnauthorizedException;
import com.resume.mall.user.mapper.MallUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String LEVEL_NONE = "NONE";
    private static final String LEVEL_NORMAL = "NORMAL";
    private static final String LEVEL_VIP = "VIP";
    private static final String LEVEL_SVIP = "SVIP";
    private static final int STATUS_ENABLED = 1;

    private final MallUserMapper userMapper;
    private final JdbcClient jdbcClient;
    private final RedisTemplate<String, AuthUserCache> authUserCacheRedisTemplate;
    private final RedisTemplate<String, UserProfileCache> userProfileCacheRedisTemplate;
    private final RedisTemplate<String, TokenSessionCache> tokenSessionRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final String jwtSecret;
    private final long jwtTtlSeconds;
    private final Duration userCacheTtl;
    private final Duration tokenTtl;

    public UserService(
            MallUserMapper userMapper,
            JdbcClient jdbcClient,
            @Qualifier("authUserCacheRedisTemplate") RedisTemplate<String, AuthUserCache> authUserCacheRedisTemplate,
            @Qualifier("userProfileCacheRedisTemplate") RedisTemplate<String, UserProfileCache> userProfileCacheRedisTemplate,
            @Qualifier("tokenSessionRedisTemplate") RedisTemplate<String, TokenSessionCache> tokenSessionRedisTemplate,
            StringRedisTemplate stringRedisTemplate,
            @Value("${mall.jwt.secret}") String jwtSecret,
            @Value("${mall.jwt.ttl-seconds}") long jwtTtlSeconds,
            @Value("${mall.user-cache.ttl-seconds}") long userCacheTtlSeconds) {
        this.userMapper = userMapper;
        this.jdbcClient = jdbcClient;
        this.authUserCacheRedisTemplate = authUserCacheRedisTemplate;
        this.userProfileCacheRedisTemplate = userProfileCacheRedisTemplate;
        this.tokenSessionRedisTemplate = tokenSessionRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtSecret = jwtSecret;
        this.jwtTtlSeconds = jwtTtlSeconds;
        this.userCacheTtl = Duration.ofSeconds(userCacheTtlSeconds);
        this.tokenTtl = Duration.ofSeconds(jwtTtlSeconds);
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
        LOGGER.atInfo()
                .addKeyValue("event", "user_registered")
                .addKeyValue("userId", saved.getId())
                .addKeyValue("role", saved.getRole())
                .addKeyValue("level", saved.getLevel())
                .log("User registered");
        return UserResponse.from(saved);
    }

    public LoginResponse login(LoginRequest request) {
        MallUser user = findByUsernameWithCache(request.username().trim());
        if (user == null || !user.getPasswordHash().equals(hashPassword(request.password()))) {
            LOGGER.atInfo()
                    .addKeyValue("event", "user_login_rejected")
                    .addKeyValue("reason", "invalid_credentials")
                    .log("User login rejected");
            throw new IllegalArgumentException("invalid username or password");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            LOGGER.atInfo()
                    .addKeyValue("event", "user_login_rejected")
                    .addKeyValue("userId", user.getId())
                    .addKeyValue("reason", "user_disabled")
                    .log("User login rejected");
            throw new IllegalStateException("user disabled");
        }

        long issuedAt = Instant.now().getEpochSecond();
        long exp = issuedAt + jwtTtlSeconds;
        String tokenId = UUID.randomUUID().toString();
        String token = JwtUtil.createToken(new JwtClaims(
                tokenId,
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLevel(),
                exp), jwtSecret);
        cacheTokenSession(new TokenSessionCache(
                tokenId,
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLevel(),
                issuedAt,
                exp));
        LOGGER.atInfo()
                .addKeyValue("event", "user_login_succeeded")
                .addKeyValue("userId", user.getId())
                .addKeyValue("role", user.getRole())
                .addKeyValue("level", user.getLevel())
                .log("User login succeeded");
        return new LoginResponse("Bearer", token, jwtTtlSeconds, UserResponse.from(user));
    }

    public void logout(String tokenId, long userId) {
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("missing token id");
        }
        tokenSessionRedisTemplate.delete(RedisKeys.tokenSession(tokenId));
        stringRedisTemplate.opsForSet().remove(RedisKeys.userTokens(userId), tokenId);
        LOGGER.atInfo()
                .addKeyValue("event", "user_logged_out")
                .addKeyValue("userId", userId)
                .log("User logged out");
    }

    public UserResponse getByAuthorizationToken(String authorization) {
        JwtClaims claims = parseBearerToken(authorization);
        TokenSessionCache session = tokenSessionRedisTemplate.opsForValue().get(RedisKeys.tokenSession(claims.jti()));
        if (session == null) {
            throw new UserUnauthorizedException("token session expired or logged out");
        }
        if (session.userId() == null
                || claims.userId() != session.userId()
                || !claims.username().equals(session.username())
                || !claims.role().equals(session.role())
                || !claims.level().equals(session.level())) {
            throw new UserUnauthorizedException("token session mismatch");
        }
        UserResponse user = getById(claims.userId());
        if (user.status() == null || user.status() != STATUS_ENABLED) {
            throw new UserUnauthorizedException("user disabled");
        }
        return user;
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
        evictUserTokens(userId);
        MallUser updated = userMapper.selectById(userId);
        cacheUser(updated);
        LOGGER.atInfo()
                .addKeyValue("event", "user_access_updated")
                .addKeyValue("userId", userId)
                .addKeyValue("role", updated.getRole())
                .addKeyValue("level", updated.getLevel())
                .addKeyValue("status", updated.getStatus())
                .log("User access attributes updated and sessions revoked");
        return UserResponse.from(updated);
    }

    @Transactional
    public UserResponse changeUsername(long userId, ChangeUsernameRequest request) {
        MallUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("user not found");
        }
        String oldUsername = user.getUsername();
        String newUsername = request.username().trim();
        if (oldUsername.equals(newUsername)) {
            return UserResponse.from(user);
        }
        user.setUsername(newUsername);
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("username already exists");
        }
        authUserCacheRedisTemplate.delete(RedisKeys.userAuth(oldUsername));
        evictUserCache(user);
        evictUserTokens(userId);
        MallUser updated = userMapper.selectById(userId);
        cacheUser(updated);
        LOGGER.atInfo()
                .addKeyValue("event", "username_changed")
                .addKeyValue("userId", userId)
                .log("Username changed and sessions revoked");
        return UserResponse.from(updated);
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        MallUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("user not found");
        }
        if (!user.getPasswordHash().equals(hashPassword(request.oldPassword()))) {
            throw new IllegalArgumentException("old password is incorrect");
        }
        user.setPasswordHash(hashPassword(request.newPassword()));
        userMapper.updateById(user);
        evictUserCache(user);
        evictUserTokens(userId);
        LOGGER.atInfo()
                .addKeyValue("event", "user_password_changed")
                .addKeyValue("userId", userId)
                .log("User password changed and sessions revoked");
    }

    @Transactional
    public void deleteUser(long userId) {
        MallUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("user not found");
        }
        if (user.getStatus() == null || user.getStatus() != 0) {
            user.setStatus(0);
            userMapper.updateById(user);
        }
        evictUserCache(user);
        evictUserTokens(userId);
        LOGGER.atInfo()
                .addKeyValue("event", "user_disabled")
                .addKeyValue("userId", userId)
                .log("User disabled and sessions revoked");
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

    private JwtClaims parseBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UserUnauthorizedException("missing Authorization or accessToken header");
        }
        String value = authorization.trim();
        if (!value.startsWith("Bearer ")) {
            throw new UserUnauthorizedException("Authorization header must start with Bearer");
        }
        String token = value.substring("Bearer ".length()).trim();
        if (token.startsWith("{") && token.endsWith("}") && token.length() > 2) {
            token = token.substring(1, token.length() - 1).trim();
        }
        try {
            return JwtUtil.parseAndValidate(token, jwtSecret);
        } catch (RuntimeException ex) {
            throw new UserUnauthorizedException(ex.getMessage());
        }
    }

    private MallUser findByUsernameWithCache(String username) {
        AuthUserCache cached = authUserCacheRedisTemplate.opsForValue().get(RedisKeys.userAuth(username));
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
        UserProfileCache cached = userProfileCacheRedisTemplate.opsForValue().get(RedisKeys.userById(userId));
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
        authUserCacheRedisTemplate.opsForValue().set(RedisKeys.userAuth(user.getUsername()), AuthUserCache.from(user), userCacheTtl);
        userProfileCacheRedisTemplate.opsForValue().set(RedisKeys.userById(user.getId()), UserProfileCache.from(user), userCacheTtl);
    }

    private void evictUserCache(MallUser user) {
        if (user == null) {
            return;
        }
        authUserCacheRedisTemplate.delete(RedisKeys.userAuth(user.getUsername()));
        userProfileCacheRedisTemplate.delete(RedisKeys.userById(user.getId()));
    }

    private void cacheTokenSession(TokenSessionCache session) {
        tokenSessionRedisTemplate.opsForValue().set(RedisKeys.tokenSession(session.tokenId()), session, tokenTtl);
        stringRedisTemplate.opsForSet().add(RedisKeys.userTokens(session.userId()), session.tokenId());
        stringRedisTemplate.expire(RedisKeys.userTokens(session.userId()), tokenTtl);
    }

    private void evictUserTokens(long userId) {
        String userTokenKey = RedisKeys.userTokens(userId);
        Set<String> tokenIds = stringRedisTemplate.opsForSet().members(userTokenKey);
        if (tokenIds != null && !tokenIds.isEmpty()) {
            tokenSessionRedisTemplate.delete(tokenIds.stream()
                    .map(RedisKeys::tokenSession)
                    .toList());
        }
        stringRedisTemplate.delete(userTokenKey);
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
