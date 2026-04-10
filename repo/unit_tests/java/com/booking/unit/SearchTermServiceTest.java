package com.booking.unit;

import com.booking.domain.SearchTerm;
import com.booking.mapper.SearchTermMapper;
import com.booking.service.SearchTermService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchTermServiceTest {

    @Mock SearchTermMapper searchTermMapper;
    @InjectMocks SearchTermService searchTermService;

    @Test void recordNewTerm() {
        when(searchTermMapper.findByTerm("portrait")).thenReturn(null);
        searchTermService.recordTerm("Portrait");
        verify(searchTermMapper).insert(argThat(st -> "portrait".equals(st.getTerm())));
    }

    @Test void recordExistingTermIncrementsFrequency() {
        SearchTerm existing = new SearchTerm();
        existing.setTerm("portrait");
        existing.setFrequency(3);
        when(searchTermMapper.findByTerm("portrait")).thenReturn(existing);
        searchTermService.recordTerm("Portrait");
        verify(searchTermMapper).incrementFrequency("portrait");
        verify(searchTermMapper, never()).insert(any());
    }

    @Test void recordNullTermIgnored() {
        searchTermService.recordTerm(null);
        verify(searchTermMapper, never()).findByTerm(any());
    }

    @Test void recordBlankTermIgnored() {
        searchTermService.recordTerm("   ");
        verify(searchTermMapper, never()).findByTerm(any());
    }

    @Test void recordTooLongTermIgnored() {
        String longTerm = "x".repeat(256);
        searchTermService.recordTerm(longTerm);
        verify(searchTermMapper, never()).findByTerm(any());
    }

    @Test void getPopularLimitsRange() {
        when(searchTermMapper.findPopular(15)).thenReturn(List.of());
        searchTermService.getPopular(15);
        verify(searchTermMapper).findPopular(15);
    }

    @Test void getPopularClampsMinimum() {
        when(searchTermMapper.findPopular(1)).thenReturn(List.of());
        searchTermService.getPopular(0);
        verify(searchTermMapper).findPopular(1);
    }

    @Test void getPopularClampsMaximum() {
        when(searchTermMapper.findPopular(50)).thenReturn(List.of());
        searchTermService.getPopular(100);
        verify(searchTermMapper).findPopular(50);
    }
}
