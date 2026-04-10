package com.booking.service;

import com.booking.domain.SearchTerm;
import com.booking.mapper.SearchTermMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchTermService {

    private final SearchTermMapper searchTermMapper;

    public SearchTermService(SearchTermMapper searchTermMapper) {
        this.searchTermMapper = searchTermMapper;
    }

    public List<SearchTerm> getPopular(int limit) {
        return searchTermMapper.findPopular(Math.min(Math.max(limit, 1), 50));
    }

    @Transactional
    public void recordTerm(String term) {
        if (term == null || term.isBlank() || term.length() > 255) return;
        String normalized = term.trim().toLowerCase();
        SearchTerm existing = searchTermMapper.findByTerm(normalized);
        if (existing != null) {
            searchTermMapper.incrementFrequency(normalized);
        } else {
            SearchTerm st = new SearchTerm();
            st.setTerm(normalized);
            searchTermMapper.insert(st);
        }
    }
}
