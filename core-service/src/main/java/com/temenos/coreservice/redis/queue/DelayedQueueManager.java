package com.temenos.coreservice.redis.queue;

import com.temenos.coreservice.config.RedisConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DelayedQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(DelayedQueueManager.class);

    private final RBlockingQueue<String> blockingQueue;
    private final RDelayedQueue<String> delayedQueue;
    private final RStream<String, String> executionStream;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DelayedQueueManager(RedissonClient redissonClient) {
        this.blockingQueue = redissonClient.getBlockingQueue(RedisConfig.DELAYED_QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.executionStream = redissonClient.getStream(RedisConfig.EXECUTION_STREAM);
    }

    @PostConstruct
    public void startListener() {
        executorService.submit(this::listen);
        logger.debug("DelayedQueueManager listener started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executorService.shutdown();
    }

    public void offer(UUID timerId, long delayMillis) {
        delayedQueue.offer(timerId.toString(), delayMillis, TimeUnit.MILLISECONDS);
        logger.debug("Offered timer {} to delayed queue with delay {}ms", timerId, delayMillis);
    }

    private void listen() {
        while (running.get()) {
            try {
                String timerId = blockingQueue.poll(2, TimeUnit.SECONDS);
                if (timerId != null) {
                    executionStream.add(StreamAddArgs.entries(
                            Map.of("timer_id", timerId)
                    ));
                    logger.debug("Moved timer {} from delayed queue to execution stream", timerId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in delayed queue listener: {}", e.getMessage());
            }
        }
    }
}