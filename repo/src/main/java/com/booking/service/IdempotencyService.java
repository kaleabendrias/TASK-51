package com.booking.service;

import com.booking.domain.IdempotencyToken;
import com.booking.mapper.IdempotencyTokenMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class IdempotencyService {

    private static final int TOKEN_VALIDITY_MINUTES = 10;

    private final IdempotencyTokenMapper tokenMapper;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyTokenMapper tokenMapper, ObjectMapper objectMapper) {
        this.tokenMapper = tokenMapper;
        this.objectMapper = objectMapper;
    }

    public static class IdempotencyResult {
        public final boolean isDuplicate;
        public final Integer cachedStatus;
        public final String cachedBody;

        public IdempotencyResult(boolean isDuplicate, Integer cachedStatus, String cachedBody) {
            this.isDuplicate = isDuplicate;
            this.cachedStatus = cachedStatus;
            this.cachedBody = cachedBody;
        }
    }

    /**
     * Checks an idempotency token scoped to a specific order+action combination.
     * The compound key prevents caching collisions across different actions on the same order.
     */
    public IdempotencyResult checkToken(String token, String action, Long orderId) {
        if (token == null || token.isBlank()) return new IdempotencyResult(false, null, null);

        // Build a compound key: token + action + orderId to prevent cross-action collisions
        String scopedKey = token + "::" + action + "::" + (orderId != null ? orderId : "new");

        IdempotencyToken existing = tokenMapper.findByToken(scopedKey);
        if (existing != null) {
            if (existing.isExpired()) {
                return new IdempotencyResult(false, null, null);
            }
            if (existing.getResponseStatus() != null) {
                return new IdempotencyResult(true, existing.getResponseStatus(), existing.getResponseBody());
            }
            return new IdempotencyResult(true, 409, "{\"error\":\"Request is already being processed\"}");
        }

        IdempotencyToken newToken = new IdempotencyToken();
        newToken.setToken(scopedKey);
        newToken.setAction(action);
        newToken.setOrderId(orderId);
        newToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
        try {
            tokenMapper.insert(newToken);
        } catch (Exception e) {
            return new IdempotencyResult(true, 409, "{\"error\":\"Duplicate request\"}");
        }

        return new IdempotencyResult(false, null, null);
    }

    /** @deprecated Use checkToken(token, action, orderId) for proper scoping */
    @Deprecated
    public IdempotencyResult checkToken(String token, String action) {
        return checkToken(token, action, null);
    }

    public void recordResponse(String token, String action, Long orderId, int status, String body) {
        if (token == null || token.isBlank()) return;
        String scopedKey = token + "::" + action + "::" + (orderId != null ? orderId : "new");
        try {
            tokenMapper.updateResponse(scopedKey, status, body);
        } catch (Exception e) {
            // Non-critical
        }
    }

    /** @deprecated Use recordResponse(token, action, orderId, status, body) for proper scoping */
    @Deprecated
    public void recordResponse(String token, Long orderId, int status, String body) {
        // Fallback — cannot reconstruct the scoped key without action
        if (token == null || token.isBlank()) return;
        try {
            tokenMapper.updateResponse(token, status, body);
        } catch (Exception e) {
            // Non-critical
        }
    }

    public void cleanupExpired() {
        tokenMapper.deleteExpired(LocalDateTime.now());
    }
}
