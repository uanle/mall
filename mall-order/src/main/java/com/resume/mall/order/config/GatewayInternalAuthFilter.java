package com.resume.mall.order.config;

import com.resume.mall.common.UserHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewayInternalAuthFilter extends OncePerRequestFilter {
    private final String internalSecret;

    public GatewayInternalAuthFilter(@Value("${mall.gateway.internal-secret}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (hasGatewayUserHeaders(request)
                && !internalSecret.equals(request.getHeader(UserHeaders.GATEWAY_INTERNAL_SECRET))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":403,\"message\":\"invalid gateway internal secret\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasGatewayUserHeaders(HttpServletRequest request) {
        return request.getHeader(UserHeaders.USER_ID) != null
                || request.getHeader(UserHeaders.USERNAME) != null
                || request.getHeader(UserHeaders.USER_ROLE) != null
                || request.getHeader(UserHeaders.USER_LEVEL) != null
                || request.getHeader(UserHeaders.TOKEN_ID) != null;
    }
}
