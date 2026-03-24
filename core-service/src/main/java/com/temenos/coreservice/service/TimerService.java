package com.temenos.coreservice.service;

import com.temenos.coreservice.domain.TimerEntity;
import com.temenos.coreservice.domain.TimerStatus;
import com.temenos.coreservice.exception.TimerNotFoundException;
import com.temenos.coreservice.model.Timer;
import com.temenos.coreservice.model.TimerRequest;
import com.temenos.coreservice.redis.stream.IntakeStreamProducer;
import com.temenos.coreservice.repository.TimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class TimerService {

    private static final Logger logger = LoggerFactory.getLogger(TimerService.class);

    private final TimerRepository timerRepository;
    private final IntakeStreamProducer intakeStreamProducer;

    public TimerService(TimerRepository timerRepository, IntakeStreamProducer intakeStreamProducer) {
        this.timerRepository = timerRepository;
        this.intakeStreamProducer = intakeStreamProducer;
    }

    public Mono<Timer> createTimer(TimerRequest request) {
        TimerEntity entity = TimerEntity.builder()
                .createdAt(request.getCreatedAt())
                .delay(request.getDelay())
                .status(TimerStatus.PENDING)
                .attempts(0)
                .updatedAt(System.currentTimeMillis())
                .build();

        logger.debug("Creating timer with id: {}", entity.getTimerId());

        return timerRepository.save(entity)
                .doOnSuccess(saved -> {
                    long fireAt = saved.getCreatedAt() + (saved.getDelay() * 1000L);
                    try {
                        intakeStreamProducer.publish(saved.getTimerId(), fireAt);
                    } catch (Exception e) {
                        logger.error("Failed to publish timer {} to intake stream, promotion job will recover", saved.getTimerId(), e);
                    }
                })
                .doOnError(error -> logger.error("Failed to save timer: {}", error.getMessage()))
                .map(this::toApiModel);
    }

    public Mono<Timer> getTimerById(String timerId) {
        logger.debug("Fetching timer with id: {}", timerId);
        return timerRepository.findById(UUID.fromString(timerId))
                .switchIfEmpty(Mono.error(new TimerNotFoundException(timerId)))
                .map(this::toApiModel);
    }

    public Flux<Timer> getAllTimers() {
        logger.debug("Fetching all timers");

        return timerRepository.findAll()
                .map(this::toApiModel);
    }

    public Mono<Void> deleteTimer(String timerId) {
        logger.debug("Deleting timer with id: {}", timerId);
        return timerRepository.findById(UUID.fromString(timerId))
                .switchIfEmpty(Mono.error(new TimerNotFoundException(timerId)))
                .flatMap(entity -> timerRepository.deleteById(UUID.fromString(timerId)));
    }


    // ==================== MAPPER ====================
    private Timer toApiModel(TimerEntity entity) {
        Timer timer = new Timer();
        timer.setTimerId(entity.getTimerId().toString());
        timer.setCreatedAt(entity.getCreatedAt());
        timer.setDelay(entity.getDelay());
        timer.setStatus(com.temenos.coreservice.model.TimerStatus.valueOf(entity.getStatus().name()));
        timer.setUpdatedAt(entity.getUpdatedAt());
        timer.setAttempts(entity.getAttempts());
        return timer;
    }
}