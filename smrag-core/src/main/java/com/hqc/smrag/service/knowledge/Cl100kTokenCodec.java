package com.hqc.smrag.service.knowledge;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CL100K_BASE token operations shared by document splitting strategies.
 */
@Component
public class Cl100kTokenCodec {

    private final Encoding encoding = Encodings.newLazyEncodingRegistry()
        .getEncoding(EncodingType.CL100K_BASE);

    public int count(String text) {
        return encoding.countTokens(text);
    }

    public List<String> splitByTokens(String text, int maxTokens) {
        requirePositive(maxTokens);
        IntArrayList tokens = encoding.encode(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < tokens.size()) {
            int end = Math.min(start + maxTokens, tokens.size());
            String piece = null;
            while (end > start && piece == null) {
                piece = decodeValidUtf8(slice(tokens, start, end));
                if (piece == null) {
                    end--;
                }
            }
            if (piece == null) {
                throw new IllegalArgumentException(
                    "maxTokens is too small to preserve a Unicode code point"
                );
            }
            pieces.add(piece);
            start = end;
        }
        return pieces;
    }

    public String lastTokens(String text, int maxTokens) {
        if (maxTokens < 0) {
            throw new IllegalArgumentException("maxTokens must not be negative");
        }
        if (maxTokens == 0 || text.isEmpty()) {
            return "";
        }

        IntArrayList tokens = encoding.encode(text);
        int start = Math.max(0, tokens.size() - maxTokens);
        while (start < tokens.size()) {
            String candidate = decodeValidUtf8(slice(tokens, start, tokens.size()));
            if (candidate != null && text.endsWith(candidate)) {
                return candidate;
            }
            start++;
        }
        return "";
    }

    private IntArrayList slice(IntArrayList tokens, int start, int end) {
        IntArrayList slice = new IntArrayList(end - start);
        for (int index = start; index < end; index++) {
            slice.add(tokens.get(index));
        }
        return slice;
    }

    private String decodeValidUtf8(IntArrayList tokens) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(encoding.decodeBytes(tokens)))
                .toString();
        }
        catch (CharacterCodingException exception) {
            return null;
        }
    }

    private void requirePositive(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }
}
