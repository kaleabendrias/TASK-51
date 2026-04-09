package com.booking.mapper;

import com.booking.domain.ChatAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatAttachmentMapper {
    ChatAttachment findById(@Param("id") Long id);
    List<ChatAttachment> findByMessageId(@Param("messageId") Long messageId);
    void insert(ChatAttachment attachment);
}
