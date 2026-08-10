package com.resume.mall.user.config;

import com.resume.mall.user.dto.UserCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, UserCache> userCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, UserCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(UserCache.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(UserCache.class));
        template.afterPropertiesSet();
        return template;
    }
}
