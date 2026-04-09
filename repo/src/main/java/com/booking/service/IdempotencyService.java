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

    public IdempotencyResult checkToken(String token, String action) {
        if (token == null || token.isBlank()) return new IdempotencyResult(false, null, null);

        IdempotencyToken existing = tokenMapper.findByToken(token);
        if (existing != null) {
            if (existing.isExpired()) {
                // Expired token — treat as new
                return new IdempotencyResult(false, null, null);
            }
            if (existing.getResponseStatus() != null) {
                // Already processed — return cached response
                return new IdempotencyResult(true, existing.getResponseStatus(), existing.getResponseBody());
            }
            // In-flight (token claimed but no response yet) — reject as duplicate
            return new IdempotencyResult(true, 409, "{\"error\":\"Request is already being processed\"}");
        }

        // Claim the token
        IdempotencyToken newToken = new IdempotencyToken();
        newToken.setToken(token);
        newToken.setAction(action);
        newToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
        try {
            tokenMapper.insert(newToken);
        } catch (Exception e) {
            // Duplicate insert race — treat as duplicate
            return new IdempotencyResult(true, 409, "{\"error\":\"Duplicate request\"}");
        }

        return new IdempotencyResult(false, null, null);
    }

    public void recordResponse(String token, Long orderId, int status, String body) {
        if (token == null || token.isBlank()) return;
        try {
            tokenMapper.updateResponse(token, status, body);
            if (orderId != null) {
                // update order_id on the token for reference
                IdempotencyToken t = tokenMapper.findByToken(token);
                if (t != null) {
                    t.setOrderId(orderId);
                }
            }
        } catch (Exception e) {
            // Non-critical — log and continue
        }
    }

    public void cleanupExpired() {
        tokenMapper.deleteExpired(LocalDateTime.now());
    }
}
