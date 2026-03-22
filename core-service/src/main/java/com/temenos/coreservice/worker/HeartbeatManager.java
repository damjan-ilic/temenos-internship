package com.temenos.coreservice.worker;

import com.temenos.coreservice.repository.TimerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class HeartbeatManager {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatManager.class);

    private final TimerRepository timerRepository;
    private final ConcurrentHashMap<UUID, Long> inFlight = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Value("${scheduler.heartbeat.interval-millis:30000}")
    private long heartbeatIntervalMillis;

    public HeartbeatManager(TimerRepository timerRepository) {
        this.timerRepository = timerRepository;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::sendHeartbeat,
                heartbeatIntervalMillis,
                heartbeatIntervalMillis,
                TimeUnit.MILLISECONDS
        );
        logger.debug("HeartbeatManager started with interval {}ms", heartbeatIntervalMillis);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        logger.debug("HeartbeatManager stopped");
    }

    public void register(UUID timerId) {
        inFlight.put(timerId, System.currentTimeMillis());
        logger.debug("Registered timer {} for heartbeat", timerId);
    }

    public void unregister(UUID timerId) {
        inFlight.remove(timerId);
        logger.debug("Unregistered timer {} from heartbeat", timerId);
    }

    private void sendHeartbeat() {
        if (inFlight.isEmpty()) {
            return;
        }

        UUID[] timerIds = inFlight.keySet().toArray(new UUID[0]);
        long now = System.currentTimeMillis();

        timerRepository.updateHeartbeat(now, timerIds)
                .doOnSuccess(__ -> logger.debug("Heartbeat sent for {} timers", timerIds.length))
                .doOnError(e -> logger.error("Heartbeat failed: {}", e.getMessage()))
                .subscribe();
    }
}