package com.booking.service;

import com.booking.controller.ChatSseController;
import com.booking.domain.Conversation;
import com.booking.domain.Message;
import com.booking.domain.Order;
import com.booking.domain.User;
import com.booking.mapper.ConversationMapper;
import com.booking.mapper.MessageMapper;
import com.booking.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final OrderMapper orderMapper;

    @Autowired(required = false)
    private ChatSseController chatSseController;

    public MessageService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                          OrderMapper orderMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.orderMapper = orderMapper;
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

        boolean isAdmin = "ADMINISTRATOR".equals(sender.getRoleName());

        // Both CUSTOMER and PHOTOGRAPHER require a valid orderId — no ad-hoc messaging
        if (!isAdmin && orderId == null) {
            throw new IllegalArgumentException("An order ID is required to start a conversation");
        }

        // Validate order-scoped authorization when orderId is present
        if (orderId != null) {
            Order order = orderMapper.findById(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Order not found");
            }
            boolean isBuyer = order.getCustomerId().equals(sender.getId());
            boolean isSeller = order.getPhotographerId().equals(sender.getId());
            if (!isBuyer && !isSeller && !isAdmin) {
                throw new SecurityException("Only the buyer or seller of this order can start a conversation");
            }
            boolean recipientIsBuyer = order.getCustomerId().equals(recipientId);
            boolean recipientIsSeller = order.getPhotographerId().equals(recipientId);
            if (!recipientIsBuyer && !recipientIsSeller) {
                throw new SecurityException("Recipient is not a participant of this order");
            }
        }

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
        pushToRecipient(conv, sender.getId(), msg);
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
        pushToRecipient(conv, sender.getId(), msg);
        return msg;
    }

    private void enforceParticipant(Conversation conv, User user) {
        if (!conv.getParticipantOne().equals(user.getId()) &&
            !conv.getParticipantTwo().equals(user.getId()) &&
            !"ADMINISTRATOR".equals(user.getRoleName())) {
            throw new SecurityException("Not a participant in this conversation");
        }
    }

    private void pushToRecipient(Conversation conv, Long senderId, Message msg) {
        if (chatSseController == null) return;
        Long recipientId = conv.getParticipantOne().equals(senderId)
                ? conv.getParticipantTwo() : conv.getParticipantOne();
        chatSseController.pushMessageEvent(recipientId,
                Map.of("conversationId", conv.getId(),
                       "messageId", msg.getId(),
                       "senderId", senderId,
                       "content", msg.getContent()));
    }
}
