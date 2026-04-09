package com.booking.mapper;

import com.booking.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    List<Message> findByConversationId(@Param("conversationId") Long conversationId);
    void insert(Message message);
    void markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
    int countUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
