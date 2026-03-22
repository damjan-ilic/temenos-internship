package com.temenos.coreservice.config;

import com.temenos.coreservice.domain.TimerStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.relational.core.mapping.NamingStrategy;

import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE,
                List.of(
                        new TimerStatusWritingConverter(),
                        new TimerStatusReadingConverter()
                )
        );
    }

    @WritingConverter
    public static class TimerStatusWritingConverter implements Converter<TimerStatus, String> {
        @Override
        public String convert(TimerStatus status) {
            return status.name();
        }
    }

    @ReadingConverter
    public static class TimerStatusReadingConverter implements Converter<String, TimerStatus> {
        @Override
        public TimerStatus convert(String status) {
            return TimerStatus.valueOf(status);
        }
    }
}