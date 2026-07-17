package com.hqc.smrag.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresUuidTypeHandlerTest {

    @Test
    void registersUuidHandlerForMapperParametersWithoutJdbcType() {
        MybatisConfiguration configuration = new MybatisConfiguration();

        new MybatisTypeHandlerConfig()
            .uuidTypeHandlerCustomizer()
            .customize(configuration);

        TypeHandler<UUID> handler = configuration
            .getTypeHandlerRegistry()
            .getTypeHandler(UUID.class);
        assertThat(handler).isInstanceOf(PostgresUuidTypeHandler.class);
    }

    @Test
    void bindsUuidAsJdbcObject() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        UUID value = UUID.randomUUID();

        new PostgresUuidTypeHandler()
            .setNonNullParameter(statement, 1, value, null);

        verify(statement).setObject(1, value);
    }

    @Test
    void readsUuidFromPostgresResult() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        UUID value = UUID.randomUUID();
        when(resultSet.getObject("processing_token")).thenReturn(value);

        UUID result = new PostgresUuidTypeHandler()
            .getNullableResult(resultSet, "processing_token");

        assertThat(result).isEqualTo(value);
    }
}
