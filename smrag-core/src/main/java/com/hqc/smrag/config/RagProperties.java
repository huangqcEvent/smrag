package com.hqc.smrag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG（检索增强生成）配置属性
 * <p>
 * 配置前缀：smrag.ai.rag
 * <p>
 * 控制知识库检索、文档分块、对话记忆等核心参数。
 *
 * @see SpringAIConfig 使用这些配置的 Spring AI 配置类
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "smrag.ai.rag")
public class RagProperties {

    /**
     * 向量检索返回的最相似文档数量
     * <p>
     * 默认值：8。取值范围：[1, 100]。
     */
    @Min(1)
    @Max(100)
    private int topK = 8;

    /**
     * 相似度阈值
     * <p>
     * 只有相似度分数超过此阈值的文档才会被返回。
     * 默认值：0.40。取值范围：[0.0, 1.0]。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double similarityThreshold = 0.40;

    /**
     * 最大上下文文档数量
     * <p>
     * 在构建提示词时，最多引用的文档数量，避免上下文过长。
     * 默认值：5。
     */
    @Min(1)
    private int maxContextDocuments = 5;

    /**
     * 对话记忆窗口大小
     * <p>
     * 保留最近 N 条对话消息，用于多轮对话上下文。
     * 默认值：12。
     */
    @Min(1)
    private int memoryWindow = 12;

    /**
     * 文档分块大小（字符数）
     * <p>
     * 将长文档切分为不超过此大小的块，便于向量化处理。
     * 默认值：800。
     */
    @Min(1)
    private int chunkSize = 800;

    /**
     * 最小分块大小（字符数）
     * <p>
     * 分块时，如果剩余内容少于这个值，则合并到前一个块。
     * 默认值：350。
     */
    @Min(0)
    private int minChunkSizeChars = 350;

    /**
     * 最小嵌入长度
     * <p>
     * 少于此长度的分块将不会被嵌入到向量数据库。
     * 默认值：20。
     */
    @Min(0)
    private int minChunkLengthToEmbed = 20;

    /**
     * 最大分块数量
     * <p>
     * 单个文档最多切分的块数，防止超大文档占用过多资源。
     * 默认值：5000。
     */
    @Min(1)
    private int maxNumChunks = 5000;

    /**
     * 是否保留分隔符
     * <p>
     * 分块时是否保留段落、章节等分隔符。
     * 默认值：true。
     */
    private boolean keepSeparator = true;

    @Min(1)
    private int recursiveChunkSize = 800;

    @Min(0)
    private int recursiveChunkOverlap = 120;

    private List<String> recursiveSeparators =
        List.of("\n\n", "\n", "。", "！", "？", "；", "，", " ");

    @AssertTrue(message = "递归切块配置不合法")
    public boolean isRecursiveChunkingValid() {
        if (recursiveChunkOverlap >= recursiveChunkSize
            || recursiveSeparators == null
            || recursiveSeparators.isEmpty()) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        return recursiveSeparators.stream()
            .allMatch(separator ->
                separator != null
                    && !separator.isEmpty()
                    && unique.add(separator));
    }

    /**
     * 最大上传文件大小（字节）
     * <p>
     * 默认值：20MB (20 * 1024 * 1024)。
     */
    @Min(1)
    private long maxUploadSizeBytes = 20L * 1024 * 1024;

    /**
     * 是否在启动时重建索引
     * <p>
     * 开启后，应用启动时会重新处理所有待处理的文档。
     * 默认值：false。
     */
    private boolean reindexOnStartup;
}
