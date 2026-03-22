package com.temenos.coreservice.controller;

import com.temenos.coreservice.api.TimersApi;
import com.temenos.coreservice.model.Timer;
import com.temenos.coreservice.model.TimerRequest;
import com.temenos.coreservice.service.TimerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class TimerController implements TimersApi {

    private static final Logger logger = LoggerFactory.getLogger(TimerController.class);

    private final TimerService timerService;

    public TimerController(TimerService timerService) {
        this.timerService = timerService;
    }

    @Override
    public Mono<ResponseEntity<Timer>> createTimer(Mono<TimerRequest> timerRequest, ServerWebExchange exchange) {
        return timerRequest
                .flatMap(timerService::createTimer)
                .map(timer -> ResponseEntity.status(HttpStatus.CREATED).body(timer));
    }

    @Override
    public Mono<ResponseEntity<Timer>> getTimerById(String timerId, ServerWebExchange exchange) {
        return timerService.getTimerById(timerId)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Flux<Timer>>> getAllTimers(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(timerService.getAllTimers()));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteTimer(String timerId, ServerWebExchange exchange) {
        return timerService.deleteTimer(timerId)
                .then(Mono.just(ResponseEntity.<Void>ok().build()));
    }
}