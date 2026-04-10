package com.booking.unit;

import com.booking.domain.SearchTerm;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SearchTermDomainTest {

    @Test void gettersAndSetters() {
        SearchTerm st = new SearchTerm();
        st.setId(1L);
        st.setTerm("portrait");
        st.setFrequency(5);
        LocalDateTime now = LocalDateTime.now();
        st.setLastUsedAt(now);
        st.setCreatedAt(now);

        assertEquals(1L, st.getId());
        assertEquals("portrait", st.getTerm());
        assertEquals(5, st.getFrequency());
        assertEquals(now, st.getLastUsedAt());
        assertEquals(now, st.getCreatedAt());
    }
}
