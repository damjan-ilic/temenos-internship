package com.temenos.coreservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    public static final String INTAKE_STREAM = "timers:intake";
    public static final String DELAYED_QUEUE = "timers:delayed";
    public static final String EXECUTION_STREAM = "timers:execution";
    public static final String CONSUMER_GROUP = "timers:consumers";

    @Value("${spring.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(redisUrl);
        return Redisson.create(config);
    }
}