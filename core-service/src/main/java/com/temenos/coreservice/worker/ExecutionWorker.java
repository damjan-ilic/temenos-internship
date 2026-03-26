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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    private final HeartbeatManager heartbeatManager;
    private final WebClient webClient;

    public ExecutionWorker(RedissonClient redissonClient,
                           TimerRepository timerRepository,
                           HeartbeatManager heartbeatManager,
                           WebClient webClient) {
        this.executionStream = redissonClient.getStream(RedisConfig.EXECUTION_STREAM);
        this.timerRepository = timerRepository;
        this.heartbeatManager = heartbeatManager;
        this.webClient = webClient;
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
                // Use Object to safely handle Redisson returning EmptyList vs EmptyMap
                Object result = executionStream.readGroup(
                        RedisConfig.CONSUMER_GROUP,
                        "worker-1",
                        StreamReadGroupArgs.neverDelivered()
                                .count(10)
                                .timeout(Duration.ofSeconds(2))
                );

                if (!(result instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<StreamMessageId, Map<String, String>> messages = (Map<StreamMessageId, Map<String, String>>) result;

                if (messages.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    // CRITICAL: We block here so the worker thread waits for the
                    // reactive processing to finish before picking up the next message.
                    processMessage(entry.getKey(), entry.getValue()).block();
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

    private Mono<Void> processMessage(StreamMessageId messageId, Map<String, String> message) {
        UUID timerId = UUID.fromString(message.get("timer_id"));

        return timerRepository.findById(timerId)
                .flatMap(entity -> {
                    // Optimistic lock check
                    if (entity.getStatus() != TimerStatus.SCHEDULED) {
                        logger.debug("Timer {} not in SCHEDULED status, skipping", timerId);
                        return Mono.just(entity);
                    }

                    entity.setStatus(TimerStatus.PROCESSING);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    return timerRepository.save(entity)
                            .doOnSuccess(saved -> heartbeatManager.register(timerId));
                })
                .flatMap(entity -> {
                    if (entity.getStatus() != TimerStatus.PROCESSING) {
                        return Mono.just(entity);
                    }

                    logger.debug("Executing timer {}", timerId);

                    return sendCallback(entity)
                            .then(Mono.defer(() -> {
                                entity.setStatus(TimerStatus.COMPLETED);
                                entity.setUpdatedAt(System.currentTimeMillis());
                                return timerRepository.save(entity);
                            }));
                })
                .onErrorResume(e -> {
                    logger.error("Execution failed for timer {}: {}", timerId, e.getMessage());
                    return timerRepository.findById(timerId)
                            .flatMap(entity -> {
                                entity.setStatus(TimerStatus.FAILED);
                                entity.setAttempts(entity.getAttempts() + 1);
                                entity.setUpdatedAt(System.currentTimeMillis());
                                return timerRepository.save(entity);
                            });
                })
                .doOnSuccess(__ -> {
                    heartbeatManager.unregister(timerId);
                    executionStream.ack(RedisConfig.CONSUMER_GROUP, messageId);
                    logger.debug("Timer {} processed and acked", timerId);
                })
                .doOnError(e -> {
                    heartbeatManager.unregister(timerId);
                    logger.error("Critial error processing timer {}: {}", timerId, e.getMessage());
                })
                .then(); // Return Mono<Void>
    }

    private Mono<Void> sendCallback(com.temenos.coreservice.domain.TimerEntity entity) {
        if (entity.getCallbackUrl() == null || entity.getCallbackUrl().isBlank()) {
            logger.debug("No callback URL for timer {}, skipping", entity.getTimerId());
            return Mono.empty();
        }

        return webClient.post()
                .uri(entity.getCallbackUrl())
                .bodyValue(entity)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(__ -> logger.debug("Callback sent for timer {}", entity.getTimerId()))
                .onErrorResume(e -> {
                    logger.error("Callback failed for timer {}, continuing: {}", entity.getTimerId(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}