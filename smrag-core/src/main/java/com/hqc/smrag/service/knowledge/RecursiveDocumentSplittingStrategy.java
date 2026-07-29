package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.enums.ChunkingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Recursively splits text by semantic separators, then greedily merges the
 * fragments and adds a token-based overlap.
 */
@Component
public class RecursiveDocumentSplittingStrategy
    implements DocumentSplittingStrategy {

    private final Cl100kTokenCodec tokenCodec;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int minChunkLengthToEmbed;
    private final int maxNumChunks;
    private final List<String> separators;

    public RecursiveDocumentSplittingStrategy(
        RagProperties properties,
        Cl100kTokenCodec tokenCodec
    ) {
        this.tokenCodec = tokenCodec;
        this.chunkSize = properties.getRecursiveChunkSize();
        this.chunkOverlap = properties.getRecursiveChunkOverlap();
        this.minChunkLengthToEmbed = properties.getMinChunkLengthToEmbed();
        this.maxNumChunks = properties.getMaxNumChunks();
        this.separators = List.copyOf(properties.getRecursiveSeparators());
        if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("递归切块大小和重叠配置不合法");
        }
        if (separators.isEmpty() || separators.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("递归切块分隔符不能为空");
        }
    }

    @Override
    public ChunkingStrategy type() {
        return ChunkingStrategy.RECURSIVE;
    }

    @Override
    public List<Document> split(List<Document> documents) {
        Objects.requireNonNull(documents, "documents must not be null");
        List<Document> output = new ArrayList<>();
        for (Document source : documents) {
            Objects.requireNonNull(source, "source document must not be null");
            String text = source.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            int outputSizeBeforeSource = output.size();
            List<String> atomicFragments = splitRecursively(text, 0);
            for (String chunk : mergeFragments(atomicFragments)) {
                addChunk(output, chunk, source);
            }
            if (output.size() == outputSizeBeforeSource) {
                throw new IllegalStateException(
                    "非空源文档无法生成有效的递归切块"
                );
            }
        }
        return output;
    }

    private List<String> splitRecursively(String text, int separatorIndex) {
        if (text.isEmpty()) {
            return List.of();
        }
        if (tokenCodec.count(text) <= chunkSize) {
            return List.of(text);
        }
        if (separatorIndex >= separators.size()) {
            return tokenCodec.splitByTokens(text, chunkSize);
        }

        List<String> result = new ArrayList<>();
        for (String piece : splitKeepingSeparator(
            text,
            separators.get(separatorIndex)
        )) {
            if (tokenCodec.count(piece) > chunkSize) {
                result.addAll(splitRecursively(piece, separatorIndex + 1));
            }
            else if (!piece.isEmpty()) {
                result.add(piece);
            }
        }
        return result;
    }

    private List<String> splitKeepingSeparator(
        String text,
        String separator
    ) {
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        int match;
        while ((match = text.indexOf(separator, cursor)) >= 0) {
            int end = match + separator.length();
            pieces.add(text.substring(cursor, end));
            cursor = end;
        }
        if (cursor < text.length()) {
            pieces.add(text.substring(cursor));
        }
        return pieces.isEmpty() ? List.of(text) : pieces;
    }

    private List<String> mergeFragments(List<String> fragments) {
        List<String> chunks = new ArrayList<>();
        List<String> currentFragments = new ArrayList<>();
        String current = "";

        for (String fragment : fragments) {
            if (fragment.isEmpty()) {
                continue;
            }
            if (current.isEmpty()) {
                current = fragment;
                currentFragments.add(fragment);
                continue;
            }

            String combined = current + fragment;
            if (tokenCodec.count(combined) <= chunkSize) {
                current = combined;
                currentFragments.add(fragment);
                continue;
            }

            chunks.add(current);
            String overlap = selectOverlap(current, currentFragments);
            overlap = fitOverlap(overlap, current, fragment);
            currentFragments = new ArrayList<>();
            if (!overlap.isEmpty()) {
                currentFragments.add(overlap);
            }
            currentFragments.add(fragment);
            current = overlap + fragment;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private String selectOverlap(
        String emittedChunk,
        List<String> emittedFragments
    ) {
        if (chunkOverlap == 0) {
            return "";
        }

        String selected = "";
        for (int index = emittedFragments.size() - 1; index >= 0; index--) {
            String candidate = emittedFragments.get(index) + selected;
            if (tokenCodec.count(candidate) > chunkOverlap) {
                break;
            }
            selected = candidate;
        }
        return selected.isEmpty()
            ? tokenCodec.lastTokens(emittedChunk, chunkOverlap)
            : selected;
    }

    private String fitOverlap(
        String selectedOverlap,
        String emittedChunk,
        String nextFragment
    ) {
        if (selectedOverlap.isEmpty()
            || tokenCodec.count(selectedOverlap + nextFragment) <= chunkSize) {
            return selectedOverlap;
        }

        int budget = Math.min(
            chunkOverlap,
            tokenCodec.count(selectedOverlap) - 1
        );
        while (budget > 0) {
            String candidate = tokenCodec.lastTokens(emittedChunk, budget);
            if (tokenCodec.count(candidate + nextFragment) <= chunkSize) {
                return candidate;
            }
            budget--;
        }
        if (tokenCodec.count(nextFragment) > chunkSize) {
            throw new IllegalStateException("递归原子片段超过 token 上限");
        }
        return "";
    }

    private void addChunk(
        List<Document> output,
        String text,
        Document source
    ) {
        if (text.isBlank() || text.length() < minChunkLengthToEmbed) {
            return;
        }
        if (tokenCodec.count(text) > chunkSize) {
            throw new IllegalStateException("递归切块超过 token 上限");
        }
        if (output.size() >= maxNumChunks) {
            throw new IllegalStateException(
                "文档切块数量超过上限: " + maxNumChunks
            );
        }
        output.add(new Document(text, new HashMap<>(source.getMetadata())));
    }
}
