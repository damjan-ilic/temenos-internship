package com.temenos.coreservice.exception;

public class TimerNotFoundException extends RuntimeException {
    public TimerNotFoundException(String timerId) {
        super("Timer not found: " + timerId);
    }
}