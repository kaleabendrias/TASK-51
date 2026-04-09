package com.booking.mapper;

import com.booking.domain.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationMapper {
    Conversation findById(@Param("id") Long id);
    List<Conversation> findByUserId(@Param("userId") Long userId);
    Conversation findByParticipants(@Param("p1") Long p1, @Param("p2") Long p2, @Param("orderId") Long orderId);
    void insert(Conversation conversation);
    void updateLastMessageAt(@Param("id") Long id);
}
