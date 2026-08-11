package com.resume.mall.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.Servlet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@ConditionalOnClass(Tracer.class)
@EnableConfigurationProperties(HttpAccessLogProperties.class)
public class HttpAccessLogAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(UnexpectedExceptionHandler.class)
    UnexpectedExceptionHandler mallUnexpectedExceptionHandler() {
        return new UnexpectedExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Servlet.class)
    @ConditionalOnProperty(
            prefix = "mall.observability.access-log",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class ServletConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "mallServletHttpAccessLogObservationHandler")
        ServletHttpAccessLogObservationHandler mallServletHttpAccessLogObservationHandler(
                Tracer tracer,
                HttpAccessLogProperties properties) {
            return new ServletHttpAccessLogObservationHandler(tracer, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    @ConditionalOnProperty(
            prefix = "mall.observability.access-log",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class ReactiveConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "mallReactiveHttpAccessLogWebFilter")
        WebFilter mallReactiveHttpAccessLogWebFilter(Tracer tracer, HttpAccessLogProperties properties) {
            return new ReactiveHttpAccessLogWebFilter(tracer, properties);
        }
    }
}
