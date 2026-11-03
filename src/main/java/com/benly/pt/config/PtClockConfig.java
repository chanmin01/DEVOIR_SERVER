package com.benly.pt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PtClockConfig {

    @Bean("ptClock")
    public Clock ptClock() {
        return Clock.systemUTC();
    }
}