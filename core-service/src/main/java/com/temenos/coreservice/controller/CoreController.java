package com.temenos.coreservice.controller;

import com.temenos.coreservice.service.CoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class CoreController {

    private final CoreService coreService;

    public CoreController(CoreService coreService) {
        this.coreService = coreService;
    }

    @GetMapping("/test")
    public Mono<ResponseEntity<String>> test() {
        return coreService.getTestMessage()
                .map(ResponseEntity::ok);
    }
}