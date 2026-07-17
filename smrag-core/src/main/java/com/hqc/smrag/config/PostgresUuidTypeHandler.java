package com.hqc.smrag.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PostgreSQL UUID 类型处理器
 * <p>
 * 用于处理 Java {@link UUID} 类型与 PostgreSQL UUID 类型之间的转换。
 * 解决 JDBC 驱动在某些情况下无法正确识别 UUID 类型的问题。
 * <p>
 * 支持的操作：
 * <ul>
 *     <li>将 UUID 参数设置到 PreparedStatement 中</li>
 *     <li>从 ResultSet 中读取 UUID 值</li>
 *     <li>从 CallableStatement 中读取 UUID 值</li>
 * </ul>
 *
 * @see MybatisTypeHandlerConfig 配置类中注册此处理器
 */
@MappedTypes(UUID.class)
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 设置非空参数
     * <p>
     * 将 UUID 对象直接设置到 PreparedStatement 中，
     * 依赖 JDBC 驱动进行类型转换。
     *
     * @param statement 预编译语句
     * @param index     参数索引位置
     * @param parameter UUID 参数值
     * @param jdbcType  JDBC 类型
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement statement,
        int index,
        UUID parameter,
        JdbcType jdbcType
    ) throws SQLException {
        statement.setObject(index, parameter);
    }

    /**
     * 根据列名获取可为空的结果
     *
     * @param resultSet  结果集
     * @param columnName 列名
     * @return UUID 值，如果为 null 则返回 null
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return uuid(resultSet.getObject(columnName));
    }

    /**
     * 根据列索引获取可为空的结果
     *
     * @param resultSet  结果集
     * @param columnIndex 列索引
     * @return UUID 值，如果为 null 则返回 null
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return uuid(resultSet.getObject(columnIndex));
    }

    /**
     * 从存储过程调用中获取可为空的结果
     *
     * @param statement  存储过程调用语句
     * @param columnIndex 列索引
     * @return UUID 值，如果为 null 则返回 null
     */
    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return uuid(statement.getObject(columnIndex));
    }

    /**
     * 将数据库对象转换为 UUID
     * <p>
     * 处理数据库返回的不同类型：
     * <ul>
     *     <li>如果是 UUID 类型，直接返回</li>
     *     <li>如果是字符串类型，解析为 UUID</li>
     *     <li>如果为 null，返回 null</li>
     * </ul>
     *
     * @param value 数据库返回的值
     * @return 转换后的 UUID，或 null
     */
    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
