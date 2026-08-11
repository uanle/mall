package com.resume.mall.gateway.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcUserApiAccessLogRepository implements UserApiAccessLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserApiAccessLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UserApiAccessLog log) {
        jdbcTemplate.update("""
                        INSERT INTO user_api_access_log (
                            trace_id, request_id, user_id, user_role, route_id, http_method, path,
                            status, success, duration_ms, client_ip, error_type, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                log.traceId(),
                log.requestId(),
                log.userId(),
                log.userRole(),
                log.routeId(),
                log.httpMethod(),
                log.path(),
                log.status(),
                log.success() ? 1 : 0,
                log.durationMs(),
                log.clientIp(),
                log.errorType(),
                Timestamp.valueOf(log.createdAt()));
    }

    @Override
    public long count(
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        QueryParts query = buildWhere(userId, httpMethod, pathLike, status, success, traceId, requestId, createdFrom, createdTo);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_api_access_log" + query.where(),
                Long.class, query.params().toArray());
        return total == null ? 0 : total;
    }

    @Override
    public List<UserApiAccessLog> page(
            long offset,
            long pageSize,
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        QueryParts query = buildWhere(userId, httpMethod, pathLike, status, success, traceId, requestId, createdFrom, createdTo);
        List<Object> params = new ArrayList<>(query.params());
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.query("""
                        SELECT id, trace_id, request_id, user_id, user_role, route_id, http_method, path,
                               status, success, duration_ms, client_ip, error_type, created_at
                        FROM user_api_access_log
                        """
                        + query.where()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> map(rs),
                params.toArray());
    }

    private QueryParts buildWhere(
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            conditions.add("user_id = ?");
            params.add(userId);
        }
        if (httpMethod != null && !httpMethod.isBlank()) {
            conditions.add("http_method = ?");
            params.add(httpMethod.trim().toUpperCase());
        }
        if (pathLike != null && !pathLike.isBlank()) {
            conditions.add("path LIKE ?");
            params.add("%" + escapeLike(pathLike.trim()) + "%");
        }
        if (status != null) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (success != null) {
            conditions.add("success = ?");
            params.add(success ? 1 : 0);
        }
        if (traceId != null && !traceId.isBlank()) {
            conditions.add("trace_id = ?");
            params.add(traceId.trim());
        }
        if (requestId != null && !requestId.isBlank()) {
            conditions.add("request_id = ?");
            params.add(requestId.trim());
        }
        if (createdFrom != null) {
            conditions.add("created_at >= ?");
            params.add(Timestamp.valueOf(createdFrom));
        }
        if (createdTo != null) {
            conditions.add("created_at <= ?");
            params.add(Timestamp.valueOf(createdTo));
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new QueryParts(where, params);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private UserApiAccessLog map(ResultSet rs) throws SQLException {
        long userId = rs.getLong("user_id");
        boolean userIdWasNull = rs.wasNull();
        return new UserApiAccessLog(
                rs.getLong("id"),
                rs.getString("trace_id"),
                rs.getString("request_id"),
                userIdWasNull ? null : userId,
                rs.getString("user_role"),
                rs.getString("route_id"),
                rs.getString("http_method"),
                rs.getString("path"),
                rs.getInt("status"),
                rs.getInt("success") == 1,
                rs.getLong("duration_ms"),
                rs.getString("client_ip"),
                rs.getString("error_type"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private record QueryParts(String where, List<Object> params) {
    }
}
