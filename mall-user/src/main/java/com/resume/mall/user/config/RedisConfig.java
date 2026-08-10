package com.resume.mall.user.config;

import com.resume.mall.user.dto.AuthUserCache;
import com.resume.mall.user.dto.TokenSessionCache;
import com.resume.mall.user.dto.UserProfileCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, AuthUserCache> authUserCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, AuthUserCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(AuthUserCache.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(AuthUserCache.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, UserProfileCache> userProfileCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, UserProfileCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(UserProfileCache.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(UserProfileCache.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, TokenSessionCache> tokenSessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TokenSessionCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(TokenSessionCache.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(TokenSessionCache.class));
        template.afterPropertiesSet();
        return template;
    }
}
