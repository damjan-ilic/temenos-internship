package com.temenos.coreservice.scheduler;

import com.temenos.coreservice.config.RedisConfig;
import com.temenos.coreservice.domain.TimerEntity;
import com.temenos.coreservice.domain.TimerStatus;
import com.temenos.coreservice.redis.stream.IntakeStreamProducer;
import com.temenos.coreservice.repository.TimerRepository;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryJob {

    private static final Logger logger = LoggerFactory.getLogger(RecoveryJob.class);

    private final TimerRepository timerRepository;
    private final IntakeStreamProducer intakeStreamProducer;

    @Value("${scheduler.recovery.max-attempts}")
    private int maxAttempts;

    @Value("${scheduler.recovery.stuck-processing-millis}")
    private long stuckProcessingMillis;

    public RecoveryJob(TimerRepository timerRepository, IntakeStreamProducer intakeStreamProducer) {
        this.timerRepository = timerRepository;
        this.intakeStreamProducer = intakeStreamProducer;
    }

    @Scheduled(fixedDelayString = "${scheduler.recovery.fixed-delay}")
    public void recover() {
        logger.debug("RecoveryJob started");
        long now = System.currentTimeMillis();

        recoverFailedTimers();
        recoverStuckProcessingTimers(now);
        recoverOverdueScheduledTimers(now);

        logger.debug("RecoveryJob completed");
    }

    // ==================== FAILED ====================
    private void recoverFailedTimers() {
        timerRepository.findFailedTimers(maxAttempts)
                .flatMap(entity -> resetAndReInject(entity, now()))
                .doOnError(e -> logger.error("Error recovering failed timers: {}", e.getMessage()))
                .subscribe();
    }

    // ==================== STUCK PROCESSING ====================
    private void recoverStuckProcessingTimers(long now) {
        long stuckBefore = now - stuckProcessingMillis;

        timerRepository.findStuckProcessingTimers(stuckBefore)
                .flatMap(entity -> resetAndReInject(entity, now))
                .doOnError(e -> logger.error("Error recovering stuck processing timers: {}", e.getMessage()))
                .subscribe();
    }

    // ==================== OVERDUE SCHEDULED ====================
    private void recoverOverdueScheduledTimers(long now) {
        timerRepository.findOverdueScheduledTimers(now)
                .flatMap(entity -> resetAndReInject(entity, now))
                .doOnError(e -> logger.error("Error recovering overdue scheduled timers: {}", e.getMessage()))
                .subscribe();
    }

    // ==================== HELPER ====================
    private reactor.core.publisher.Mono<TimerEntity> resetAndReInject(TimerEntity entity, long now) {
        entity.setStatus(TimerStatus.PENDING);
        entity.setUpdatedAt(now);

        return timerRepository.save(entity)
                .doOnSuccess(saved -> {
                    long fireAt = saved.getCreatedAt() + (saved.getDelay() * 1000L);
                    intakeStreamProducer.publish(saved.getTimerId(), fireAt);
                    logger.debug("Re-injected timer {} into intake stream", saved.getTimerId());
                });
    }

    private long now() {
        return System.currentTimeMillis();
    }
}