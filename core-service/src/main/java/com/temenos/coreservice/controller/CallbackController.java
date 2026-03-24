package com.temenos.coreservice.controller;

import com.temenos.coreservice.model.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class CallbackController {

    private static final Logger logger = LoggerFactory.getLogger(CallbackController.class);

    @PostMapping("/callback")
    public Mono<ResponseEntity<Void>> callback(@RequestBody Mono<Timer> timer) {
        return timer
                .doOnNext(t -> logger.info("Callback received for timer: {}", t.toJson()))
                .then(Mono.just(ResponseEntity.<Void>ok().build()));
    }
}