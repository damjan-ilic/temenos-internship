package com.temenos.coreservice.worker;

import com.temenos.coreservice.config.RedisConfig;
import com.temenos.coreservice.domain.TimerStatus;
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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExecutionWorker {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionWorker.class);

    private final RStream<String, String> executionStream;
    private final TimerRepository timerRepository;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ExecutionWorker(RedissonClient redissonClient, TimerRepository timerRepository) {
        this.executionStream = redissonClient.getStream(RedisConfig.EXECUTION_STREAM);
        this.timerRepository = timerRepository;
    }

    @PostConstruct
    public void start() {
        try {
            executionStream.createGroup(StreamCreateGroupArgs
                    .name(RedisConfig.CONSUMER_GROUP)
                    .id(StreamMessageId.NEWEST)
                    .makeStream());
        } catch (Exception e) {
            logger.debug("Consumer group already exists: {}", e.getMessage());
        }

        executorService.submit(this::consume);
        logger.debug("ExecutionWorker started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executorService.shutdown();
        logger.debug("ExecutionWorker stopped");
    }

    private void consume() {
        while (running.get()) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = executionStream.readGroup(
                        RedisConfig.CONSUMER_GROUP,
                        "worker-1",
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
                logger.error("Error in execution worker: {}", e.getMessage());
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

        timerRepository.findById(timerId)
                .flatMap(entity -> {
                    // optimistic lock — only proceed if still SCHEDULED
                    if (entity.getStatus() != TimerStatus.SCHEDULED) {
                        logger.debug("Timer {} already claimed by another worker, skipping", timerId);
                        return timerRepository.save(entity);
                    }

                    entity.setStatus(TimerStatus.PROCESSING);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    return timerRepository.save(entity);
                })
                .flatMap(entity -> {
                    if (entity.getStatus() != TimerStatus.PROCESSING) {
                        return reactor.core.publisher.Mono.just(entity);
                    }

                    // execute work here
                    logger.debug("Executing timer {}", timerId);

                    entity.setStatus(TimerStatus.COMPLETED);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    return timerRepository.save(entity);
                })
                .doOnSuccess(__ -> {
                    executionStream.ack(RedisConfig.CONSUMER_GROUP, messageId);
                    logger.debug("Timer {} completed and acked", timerId);
                })
                .doOnError(e -> logger.error("Failed to execute timer {}: {}", timerId, e.getMessage()))
                .subscribe();
    }
}