package com.temenos.coreservice.repository;

import com.temenos.coreservice.domain.TimerEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TimerRepository extends ReactiveCrudRepository<TimerEntity, UUID> {
}