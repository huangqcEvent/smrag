package com.hqc.smrag.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MyBatis 类型处理器配置类
 * <p>
 * 用于注册自定义的 MyBatis 类型处理器，处理 Java 类型与 JDBC 类型之间的转换。
 * 主要解决 PostgreSQL 数据库中 UUID 类型的读写问题。
 *
 * @see PostgresUuidTypeHandler UUID 类型处理器实现
 */
@Configuration
public class MybatisTypeHandlerConfig {

    /**
     * 注册 UUID 类型处理器
     * <p>
     * 将 {@link PostgresUuidTypeHandler} 注册到 MyBatis 的类型处理器注册表中，
     * 使 MyBatis 能够正确处理 PostgreSQL 的 UUID 类型字段。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration
            .getTypeHandlerRegistry()
            .register(UUID.class, new PostgresUuidTypeHandler());
    }
}
