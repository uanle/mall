package com.resume.mall.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component("rateLimitRules")
public class RateLimitRulesHealthIndicator implements HealthIndicator {
    private final GatewayRateLimitProperties properties;

    public RateLimitRulesHealthIndicator(GatewayRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }

        Set<GatewayFlowRule> gatewayRules = GatewayRuleManager.getRules();
        Set<String> loadedGatewayResources = gatewayRules.stream()
                .map(GatewayFlowRule::getResource)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missingGatewayResources = new LinkedHashSet<>(properties.getRequiredGatewayResources());
        missingGatewayResources.removeAll(loadedGatewayResources);

        Set<String> missingUserResources = properties.getUserLimitedRoutes().stream()
                .map(GatewayRateLimitConstants::userResource)
                .filter(resource -> !ParamFlowRuleManager.hasRules(resource))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean apiGroupLoaded = GatewayApiDefinitionManager.getApiDefinition("api-all") != null;
        Health.Builder builder = Health.up()
                .withDetail("enabled", true)
                .withDetail("gatewayRuleCount", gatewayRules.size())
                .withDetail("apiDefinitionCount", GatewayApiDefinitionManager.getApiDefinitions().size())
                .withDetail("userRuleCount", ParamFlowRuleManager.getRules().size());

        if (properties.isRequireRules()
                && (!missingGatewayResources.isEmpty() || !missingUserResources.isEmpty() || !apiGroupLoaded)) {
            builder = Health.down()
                    .withDetail("missingGatewayResources", missingGatewayResources)
                    .withDetail("missingUserResources", missingUserResources)
                    .withDetail("apiAllDefinitionLoaded", apiGroupLoaded);
        }
        return builder.build();
    }
}
