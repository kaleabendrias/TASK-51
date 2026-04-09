package com.booking.unit;

import com.booking.domain.IdempotencyToken;
import com.booking.mapper.IdempotencyTokenMapper;
import com.booking.service.IdempotencyService;
import com.booking.service.IdempotencyService.IdempotencyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyTokenMapper tokenMapper;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks IdempotencyService idempotencyService;

    @Test void nullTokenPassesThrough() {
        IdempotencyResult r = idempotencyService.checkToken(null, "CREATE", 1L);
        assertFalse(r.isDuplicate);
    }

    @Test void blankTokenPassesThrough() {
        IdempotencyResult r = idempotencyService.checkToken("", "CREATE", 1L);
        assertFalse(r.isDuplicate);
    }

    @Test void newTokenClaimedWithScopedKey() {
        String scopedKey = "tok1::CREATE::1";
        when(tokenMapper.findByToken(scopedKey)).thenReturn(null);
        IdempotencyResult r = idempotencyService.checkToken("tok1", "CREATE", 1L);
        assertFalse(r.isDuplicate);
        verify(tokenMapper).insert(argThat(t -> t.getToken().equals(scopedKey)));
    }

    @Test void duplicateWithCachedResponse() {
        String scopedKey = "tok2::CONFIRM::5";
        IdempotencyToken existing = new IdempotencyToken();
        existing.setToken(scopedKey); existing.setResponseStatus(200); existing.setResponseBody("{\"ok\":true}");
        existing.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenMapper.findByToken(scopedKey)).thenReturn(existing);
        IdempotencyResult r = idempotencyService.checkToken("tok2", "CONFIRM", 5L);
        assertTrue(r.isDuplicate);
        assertEquals(200, r.cachedStatus);
    }

    @Test void sameTokenDifferentActionNoCacheCollision() {
        // CONFIRM on order 1 is cached
        String confirmKey = "tok3::CONFIRM::1";
        IdempotencyToken cached = new IdempotencyToken();
        cached.setToken(confirmKey); cached.setResponseStatus(200);
        cached.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenMapper.findByToken(confirmKey)).thenReturn(cached);

        // PAY on order 1 with same raw token is a NEW key
        String payKey = "tok3::PAY::1";
        when(tokenMapper.findByToken(payKey)).thenReturn(null);

        IdempotencyResult r1 = idempotencyService.checkToken("tok3", "CONFIRM", 1L);
        assertTrue(r1.isDuplicate);

        IdempotencyResult r2 = idempotencyService.checkToken("tok3", "PAY", 1L);
        assertFalse(r2.isDuplicate);
    }

    @Test void inFlightReturns409() {
        String scopedKey = "tok4::CREATE::new";
        IdempotencyToken inflight = new IdempotencyToken();
        inflight.setToken(scopedKey); inflight.setResponseStatus(null);
        inflight.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenMapper.findByToken(scopedKey)).thenReturn(inflight);
        IdempotencyResult r = idempotencyService.checkToken("tok4", "CREATE", null);
        assertTrue(r.isDuplicate);
        assertEquals(409, r.cachedStatus);
    }

    @Test void expiredTokenTreatedAsNew() {
        String scopedKey = "tok5::CREATE::new";
        IdempotencyToken expired = new IdempotencyToken();
        expired.setToken(scopedKey); expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenMapper.findByToken(scopedKey)).thenReturn(expired);
        IdempotencyResult r = idempotencyService.checkToken("tok5", "CREATE", null);
        assertFalse(r.isDuplicate);
    }

    @Test void duplicateInsertRace() {
        String scopedKey = "tok6::CREATE::new";
        when(tokenMapper.findByToken(scopedKey)).thenReturn(null);
        doThrow(new RuntimeException("Dup")).when(tokenMapper).insert(any());
        IdempotencyResult r = idempotencyService.checkToken("tok6", "CREATE", null);
        assertTrue(r.isDuplicate);
        assertEquals(409, r.cachedStatus);
    }

    @Test void cleanupCallsMapper() {
        idempotencyService.cleanupExpired();
        verify(tokenMapper).deleteExpired(any());
    }

    @Test void recordResponseScopedKey() {
        assertDoesNotThrow(() -> idempotencyService.recordResponse("tok", "PAY", 1L, 200, "body"));
        verify(tokenMapper).updateResponse("tok::PAY::1", 200, "body");
    }

    @Test void recordResponseNullTokenNoOp() {
        assertDoesNotThrow(() -> idempotencyService.recordResponse(null, "PAY", 1L, 200, "body"));
        verify(tokenMapper, never()).updateResponse(any(), anyInt(), any());
    }
}
