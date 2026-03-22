package com.temenos.coreservice.redis.queue;

import com.temenos.coreservice.config.RedisConfig;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DelayedQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(DelayedQueueManager.class);

    private final RDelayedQueue<String> delayedQueue;

    public DelayedQueueManager(RedissonClient redissonClient) {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(RedisConfig.DELAYED_QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    public void offer(UUID timerId, long delayMillis) {
        delayedQueue.offer(timerId.toString(), delayMillis, TimeUnit.MILLISECONDS);
        logger.debug("Offered timer {} to delayed queue with delay {}ms", timerId, delayMillis);
    }
}