package com.temenos.coreservice.repository;

import com.temenos.coreservice.domain.TimerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface TimerRepository extends ReactiveCrudRepository<TimerEntity, UUID> {

    @Query("SELECT * FROM timer WHERE status = 'PENDING' AND (created_at + delay * 1000) <= :fireAtBefore")
    Flux<TimerEntity> findPendingTimersFireBefore(long fireAtBefore);

}