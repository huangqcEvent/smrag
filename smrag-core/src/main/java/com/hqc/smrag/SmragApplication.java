package com.hqc.smrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 私募AI专家应用程序入口
 * <p>
 * 基于 Spring Boot 3.5 和 Spring AI 构建的私募基金智能问答平台。
 * <p>
 * 核心功能：
 * <ul>
 *     <li>知识库管理：上传、解析、向量化文档</li>
 *     <li>智能问答：基于 RAG 架构的多轮对话</li>
 *     <li>产品查询：基金产品和基金经理信息查询</li>
 *     <li>销售话术：合规的基金销售话术生成</li>
 * </ul>
 * <p>
 * 技术栈：
 * <ul>
 *     <li>Spring Boot 3.5：基础框架</li>
 *     <li>Spring AI 1.1：AI 能力集成</li>
 *     <li>Alibaba DashScope：大语言模型服务</li>
 *     <li>PostgreSQL + PGVector：向量数据库</li>
 *     <li>Kafka：异步消息处理</li>
 *     <li>MyBatis Plus：数据访问层</li>
 * </ul>
 * <p>
 * 启动方式：
 * <pre>{@code
 * java -jar smrag-core.jar
 * }</pre>
 *
 * @see com.hqc.smrag.config.SpringAIConfig Spring AI 配置
 * @see com.hqc.smrag.config.RagProperties RAG 配置属性
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmragApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SmragApplication.class, args);
    }
}
