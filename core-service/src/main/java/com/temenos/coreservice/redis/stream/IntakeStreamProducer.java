package com.temenos.coreservice.redis.stream;

import com.temenos.coreservice.config.RedisConfig;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class IntakeStreamProducer {

    private static final Logger logger = LoggerFactory.getLogger(IntakeStreamProducer.class);

    private final RStream<String, String> intakeStream;

    public IntakeStreamProducer(RedissonClient redissonClient) {
        this.intakeStream = redissonClient.getStream(RedisConfig.INTAKE_STREAM);
    }

    public void publish(UUID timerId, long fireAt) {
        Map<String, String> entry = new java.util.HashMap<>();
        entry.put("timer_id", timerId.toString());
        entry.put("fire_at", String.valueOf(fireAt));

        intakeStream.add(StreamAddArgs.entries(entry));

        logger.debug("Published timer {} to intake stream with fireAt {}", timerId, fireAt);
    }
}