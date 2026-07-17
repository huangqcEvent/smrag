package com.hqc.smrag.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MybatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration
            .getTypeHandlerRegistry()
            .register(UUID.class, new PostgresUuidTypeHandler());
    }
}
