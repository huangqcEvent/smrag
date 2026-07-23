package com.hqc.smrag.service.chat;

/**
 * Builds the internal key used by Spring AI chat memory.
 *
 * <p>The client-facing session id is only unique within an account. Prefixing
 * it with the authenticated user id prevents one account from reading or
 * contaminating another account's in-memory conversation.</p>
 */
public final class ConversationKeys {

    private ConversationKeys() {
    }

    public static String scoped(Long userId, String sessionId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return userId + ":" + sessionId.trim();
    }
}
