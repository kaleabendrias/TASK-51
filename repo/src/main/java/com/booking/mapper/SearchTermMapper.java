package com.booking.mapper;

import com.booking.domain.SearchTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchTermMapper {
    List<SearchTerm> findPopular(@Param("limit") int limit);
    SearchTerm findByTerm(@Param("term") String term);
    void insert(SearchTerm searchTerm);
    void incrementFrequency(@Param("term") String term);
}
