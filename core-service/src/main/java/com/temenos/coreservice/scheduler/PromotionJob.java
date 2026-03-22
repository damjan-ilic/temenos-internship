package com.temenos.coreservice.scheduler;

import com.temenos.coreservice.domain.TimerStatus;
import com.temenos.coreservice.redis.queue.DelayedQueueManager;
import com.temenos.coreservice.repository.TimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PromotionJob {

    private static final Logger logger = LoggerFactory.getLogger(PromotionJob.class);

    private final TimerRepository timerRepository;
    private final DelayedQueueManager delayedQueueManager;

    @Value("${scheduler.promotion.window-millis}")
    private long promotionWindowMillis;

    public PromotionJob(TimerRepository timerRepository, DelayedQueueManager delayedQueueManager) {
        this.timerRepository = timerRepository;
        this.delayedQueueManager = delayedQueueManager;
    }

    @Scheduled(fixedDelayString = "${scheduler.promotion.fixed-delay}")
    public void promote() {
        logger.debug("PromotionJob started");
        long now = System.currentTimeMillis();
        long windowEnd = now + promotionWindowMillis;

        timerRepository.findPendingTimersFireBefore(windowEnd)
                .flatMap(entity -> {
                    entity.setStatus(TimerStatus.SCHEDULED);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    return timerRepository.save(entity)
                            .doOnSuccess(saved -> {
                                long fireAt = saved.getCreatedAt() + (saved.getDelay() * 1000L);
                                long delayMillis = Math.max(fireAt - now, 0);
                                delayedQueueManager.offer(saved.getTimerId(), delayMillis);
                                logger.debug("Promoted timer {} with delay {}ms", saved.getTimerId(), delayMillis);
                            });
                })
                .doOnComplete(() -> logger.debug("PromotionJob completed"))
                .doOnError(e -> logger.error("PromotionJob failed: {}", e.getMessage()))
                .subscribe();
    }
}
