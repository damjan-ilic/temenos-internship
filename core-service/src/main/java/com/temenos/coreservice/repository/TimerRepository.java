package com.temenos.coreservice.repository;

import com.temenos.coreservice.domain.TimerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface TimerRepository extends ReactiveCrudRepository<TimerEntity, UUID> {

    //promotion job
    @Query("SELECT * FROM timer WHERE status = 'PENDING' AND (created_at + delay * 1000) <= :fireAtBefore")
    Flux<TimerEntity> findPendingTimersFireBefore(long fireAtBefore);

    //recovery job
    @Query("SELECT * FROM timer WHERE status = 'FAILED' AND attempts < :maxAttempts")
    Flux<TimerEntity> findFailedTimers(int maxAttempts);

    @Query("SELECT * FROM timer WHERE status = 'PROCESSING' AND updated_at < :stuckBefore")
    Flux<TimerEntity> findStuckProcessingTimers(long stuckBefore);

    @Query("SELECT * FROM timer WHERE status = 'SCHEDULED' AND (created_at + delay * 1000) < :now")
    Flux<TimerEntity> findOverdueScheduledTimers(long now);
}