package com.resume.mall.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JwtUtil {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private JwtUtil() {
    }

    public static String createToken(JwtClaims claims, String secret) {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{"
                + "\"jti\":\"" + escape(claims.jti()) + "\","
                + "\"userId\":" + claims.userId() + ","
                + "\"username\":\"" + escape(claims.username()) + "\","
                + "\"role\":\"" + escape(claims.role()) + "\","
                + "\"level\":\"" + escape(claims.level()) + "\","
                + "\"exp\":" + claims.exp()
                + "}";
        String unsigned = encode(header) + "." + encode(payload);
        return unsigned + "." + sign(unsigned, secret);
    }

    public static JwtClaims parseAndValidate(String token, String secret) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token");
        }
        String unsigned = parts[0] + "." + parts[1];
        String expected = sign(unsigned, secret);
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }

        String payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> values = parseFlatJson(payload);
        long exp = Long.parseLong(required(values, "exp"));
        if (exp < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("token expired");
        }
        return new JwtClaims(
                required(values, "jti"),
                Long.parseLong(required(values, "userId")),
                required(values, "username"),
                required(values, "role"),
                required(values, "level"),
                exp);
    }

    private static String encode(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String unsigned, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign token", ex);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String item : body.split(",")) {
            String[] pair = item.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = unquote(pair[0].trim());
            String value = unquote(pair[1].trim());
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing token claim: " + key);
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return value;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        int result = a.length() ^ b.length();
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
