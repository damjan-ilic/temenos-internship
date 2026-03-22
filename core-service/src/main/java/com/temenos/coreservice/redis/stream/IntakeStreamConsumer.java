package com.temenos.coreservice.redis.stream;

import com.temenos.coreservice.config.RedisConfig;
import com.temenos.coreservice.domain.TimerStatus;
import com.temenos.coreservice.redis.queue.DelayedQueueManager;
import com.temenos.coreservice.repository.TimerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class IntakeStreamConsumer {

    private static final Logger logger = LoggerFactory.getLogger(IntakeStreamConsumer.class);
    @Value("${scheduler.promotion.window-millis}")
    private long promotionWindowMillis;
    private final RStream<String, String> intakeStream;
    private final TimerRepository timerRepository;
    private final DelayedQueueManager delayedQueueManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public IntakeStreamConsumer(RedissonClient redissonClient,
                                TimerRepository timerRepository,
                                DelayedQueueManager delayedQueueManager) {
        this.intakeStream = redissonClient.getStream(RedisConfig.INTAKE_STREAM);
        this.timerRepository = timerRepository;
        this.delayedQueueManager = delayedQueueManager;
    }

    @PostConstruct
    public void start() {
        try {
            intakeStream.createGroup(StreamCreateGroupArgs
                    .name(RedisConfig.CONSUMER_GROUP)
                    .id(StreamMessageId.NEWEST)
                    .makeStream());
        } catch (Exception e) {
            logger.debug("Consumer group already exists: {}", e.getMessage());
        }

        executorService.submit(this::consume);
        logger.debug("IntakeStreamConsumer started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executorService.shutdown();
        logger.debug("IntakeStreamConsumer stopped");
    }

    private void consume() {
        while (running.get()) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = intakeStream.readGroup(
                        RedisConfig.CONSUMER_GROUP,
                        "consumer-1",
                        StreamReadGroupArgs.neverDelivered()
                                .count(10)
                                .timeout(Duration.ofSeconds(2))
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    processMessage(entry.getKey(), entry.getValue());
                }

            } catch (Exception e) {
                logger.error("Error consuming from intake stream: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processMessage(StreamMessageId messageId, Map<String, String> message) {
        UUID timerId = UUID.fromString(message.get("timer_id"));
        long fireAt = Long.parseLong(message.get("fire_at"));
        long now = System.currentTimeMillis();
        long delayMillis = fireAt - now;

        if (delayMillis <= promotionWindowMillis) {
            timerRepository.findById(timerId)
                    .flatMap(entity -> {
                        if (entity.getStatus() == TimerStatus.PENDING) {
                            entity.setStatus(TimerStatus.SCHEDULED);
                            entity.setUpdatedAt(System.currentTimeMillis());
                            return timerRepository.save(entity)
                                    .doOnSuccess(saved -> {
                                        delayedQueueManager.offer(timerId, Math.max(delayMillis, 0));
                                        logger.debug("Scheduled timer {} with delay {}ms", timerId, delayMillis);
                                    });
                        }
                        return Mono.just(entity);
                    })
                    .doOnSuccess(__ -> intakeStream.ack(RedisConfig.CONSUMER_GROUP, messageId))
                    .doOnError(e -> logger.error("Failed to process timer {}: {}", timerId, e.getMessage()))
                    .subscribe();
        } else {
            logger.debug("Timer {} fireAt > 1 hour, leaving as PENDING", timerId);
            intakeStream.ack(RedisConfig.CONSUMER_GROUP, messageId);
        }
    }
}