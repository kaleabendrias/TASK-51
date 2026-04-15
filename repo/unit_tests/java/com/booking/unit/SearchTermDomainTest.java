package com.booking.unit;

import com.booking.domain.SearchTerm;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for the SearchTerm domain object.
 *
 * Rather than testing getters/setters in isolation, these tests verify
 * the meaningful invariants of the domain object (freshly constructed state,
 * timestamp semantics, and frequency update behaviour).
 */
class SearchTermDomainTest {

    @Test
    void newSearchTermHasNullFrequencyByDefault() {
        SearchTerm st = new SearchTerm();
        assertNull(st.getFrequency(),
                "Freshly constructed SearchTerm must have null frequency (not yet recorded)");
    }

    @Test
    void newSearchTermHasNullTermByDefault() {
        SearchTerm st = new SearchTerm();
        assertNull(st.getTerm(),
                "Freshly constructed SearchTerm must have null term");
    }

    @Test
    void termCanBeUpdatedAfterConstruction() {
        SearchTerm st = new SearchTerm();
        st.setTerm("portrait");
        assertEquals("portrait", st.getTerm(),
                "setTerm must persist the value for subsequent retrieval");
    }

    @Test
    void frequencyCanBeIncrementedManually() {
        SearchTerm st = new SearchTerm();
        st.setFrequency(3);
        st.setFrequency(st.getFrequency() + 1);
        assertEquals(4, st.getFrequency(),
                "Frequency must reflect manual increment (simulating what the service does)");
    }

    @Test
    void lastUsedAtCanBeSetToArbitraryTimestamp() {
        SearchTerm st = new SearchTerm();
        LocalDateTime ts = LocalDateTime.of(2025, 6, 15, 12, 0);
        st.setLastUsedAt(ts);
        assertEquals(ts, st.getLastUsedAt(),
                "lastUsedAt must store the exact timestamp set on it");
    }

    @Test
    void createdAtCanPrecedeLastUsedAt() {
        SearchTerm st = new SearchTerm();
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime lastUsed = LocalDateTime.of(2025, 6, 1, 9, 0);
        st.setCreatedAt(created);
        st.setLastUsedAt(lastUsed);
        assertTrue(st.getCreatedAt().isBefore(st.getLastUsedAt()),
                "createdAt should be earlier than lastUsedAt for a term used after creation");
    }

    @Test
    void idIsIndependentOfOtherFields() {
        SearchTerm st = new SearchTerm();
        st.setId(42L);
        st.setTerm("wedding");
        st.setFrequency(7);
        assertEquals(42L, st.getId(),
                "id must be preserved independently of other field changes");
        assertEquals("wedding", st.getTerm());
        assertEquals(7, st.getFrequency());
    }
}
