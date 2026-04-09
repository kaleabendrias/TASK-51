package com.booking.service;

import com.booking.domain.Conversation;
import com.booking.domain.Message;
import com.booking.domain.User;
import com.booking.mapper.ConversationMapper;
import com.booking.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public MessageService(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public List<Conversation> getConversations(User user) {
        List<Conversation> convos = conversationMapper.findByUserId(user.getId());
        for (Conversation c : convos) {
            c.setUnreadCount(messageMapper.countUnread(c.getId(), user.getId()));
        }
        return convos;
    }

    public List<Message> getMessages(Long conversationId, User user) {
        Conversation conv = conversationMapper.findById(conversationId);
        if (conv == null) throw new IllegalArgumentException("Conversation not found");
        enforceParticipant(conv, user);
        // Mark as read
        messageMapper.markRead(conversationId, user.getId());
        return messageMapper.findByConversationId(conversationId);
    }

    @Transactional
    public Message sendMessage(Long recipientId, String content, Long orderId, User sender) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        // Photographers can only message their own customers/order participants
        // This is enforced by conversation lookup

        // Find or create conversation
        Conversation conv = conversationMapper.findByParticipants(
                sender.getId(), recipientId, orderId);

        if (conv == null) {
            conv = new Conversation();
            conv.setParticipantOne(sender.getId());
            conv.setParticipantTwo(recipientId);
            conv.setOrderId(orderId);
            conversationMapper.insert(conv);
            conv = conversationMapper.findById(conv.getId());
        }

        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setSenderId(sender.getId());
        msg.setContent(content);
        messageMapper.insert(msg);

        conversationMapper.updateLastMessageAt(conv.getId());
        return msg;
    }

    @Transactional
    public Message sendToConversation(Long conversationId, String content, User sender) {
        Conversation conv = conversationMapper.findById(conversationId);
        if (conv == null) throw new IllegalArgumentException("Conversation not found");
        enforceParticipant(conv, sender);

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(sender.getId());
        msg.setContent(content);
        messageMapper.insert(msg);

        conversationMapper.updateLastMessageAt(conversationId);
        return msg;
    }

    private void enforceParticipant(Conversation conv, User user) {
        if (!conv.getParticipantOne().equals(user.getId()) &&
            !conv.getParticipantTwo().equals(user.getId()) &&
            !"ADMINISTRATOR".equals(user.getRoleName())) {
            throw new SecurityException("Not a participant in this conversation");
        }
    }
}
