package com.temenos.coreservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CoreService {

    private static final Logger logger = LoggerFactory.getLogger(CoreService.class);

    public Mono<String> getTestMessage() {
        logger.debug("Test endpoint was called");
        return Mono.just("test ok");
    }
}