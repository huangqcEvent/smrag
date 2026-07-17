package com.hqc.smrag.kafka;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.service.knowledge.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 文档解析 Kafka 消费者
 * <p>
 * 监听 Kafka topic "document-parse"，消费文档解析任务消息。
 * <p>
 * 消费流程：
 * <ol>
 *     <li>接收 JSON 格式的消息</li>
 *     <li>解析为 {@link DocumentParseTaskDTO} 对象</li>
 *     <li>调用 {@link DocumentIngestionService} 处理文档解析</li>
 * </ol>
 * <p>
 * 错误处理：
 * <ul>
 *     <li>处理失败时，由 {@link DocumentParseRecoverer} 进行恢复</li>
 *     <li>采用指数退避重试策略，最多重试 2 次</li>
 *     <li>重试失败后，发送到死信队列（DLT）</li>
 * </ul>
 *
 * @see DocumentIngestionService 文档解析服务
 * @see DocumentParseRecoverer 文档解析恢复器
 * @see DocumentParseTaskDTO 文档解析任务 DTO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseConsumer {

    private final DocumentIngestionService ingestionService;

    /**
     * 消费文档解析任务消息
     * <p>
     * 监听 topic: document-parse
     * <br>
     * 消费者组: smrag-parse-group
     *
     * @param message JSON 格式的消息内容
     */
    @KafkaListener(topics = "document-parse", groupId = "smrag-parse-group")
    public void consume(String message) {
        log.info("Received document ingestion task");
        DocumentParseTaskDTO task = JSONUtil.toBean(message, DocumentParseTaskDTO.class);
        ingestionService.process(task);
    }
}
