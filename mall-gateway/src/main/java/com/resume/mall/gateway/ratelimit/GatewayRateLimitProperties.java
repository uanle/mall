package com.resume.mall.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "mall.gateway.rate-limit")
public class GatewayRateLimitProperties {
    private boolean enabled = true;
    private boolean requireRules = true;
    private Set<String> trustedProxies = new LinkedHashSet<>();
    private Set<String> requiredGatewayResources = new LinkedHashSet<>();
    private Set<String> userLimitedRoutes = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireRules() {
        return requireRules;
    }

    public void setRequireRules(boolean requireRules) {
        this.requireRules = requireRules;
    }

    public Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(Set<String> trustedProxies) {
        this.trustedProxies = copyOf(trustedProxies);
    }

    public Set<String> getRequiredGatewayResources() {
        return requiredGatewayResources;
    }

    public void setRequiredGatewayResources(Set<String> requiredGatewayResources) {
        this.requiredGatewayResources = copyOf(requiredGatewayResources);
    }

    public Set<String> getUserLimitedRoutes() {
        return userLimitedRoutes;
    }

    public void setUserLimitedRoutes(Set<String> userLimitedRoutes) {
        this.userLimitedRoutes = copyOf(userLimitedRoutes);
    }

    private Set<String> copyOf(Set<String> source) {
        return source == null ? new LinkedHashSet<>() : new LinkedHashSet<>(source);
    }
}
