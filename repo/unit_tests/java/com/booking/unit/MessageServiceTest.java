package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.ConversationMapper;
import com.booking.mapper.MessageMapper;
import com.booking.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock com.booking.mapper.OrderMapper orderMapper;
    @Mock ConversationMapper conversationMapper;
    @Mock MessageMapper messageMapper;
    @InjectMocks MessageService messageService;

    private Conversation conv(Long p1, Long p2) {
        Conversation c = new Conversation(); c.setId(1L);
        c.setParticipantOne(p1); c.setParticipantTwo(p2); return c;
    }
    private User user(Long id, String role) { User u = new User(); u.setId(id); u.setRoleName(role); return u; }

    @Test void sendMessageCreatesConversationIfNeeded() {
        when(conversationMapper.findByParticipants(4L, 2L, null)).thenReturn(null);
        Conversation newConv = conv(4L, 2L);
        when(conversationMapper.findById(any())).thenReturn(newConv);
        messageService.sendMessage(2L, "Hello", null, user(4L, "CUSTOMER"));
        verify(conversationMapper).insert(any());
        verify(messageMapper).insert(any());
        verify(conversationMapper).updateLastMessageAt(any());
    }

    @Test void sendMessageUsesExistingConversation() {
        when(conversationMapper.findByParticipants(4L, 2L, 1L)).thenReturn(conv(4L, 2L));
        messageService.sendMessage(2L, "Hi", 1L, user(4L, "CUSTOMER"));
        verify(conversationMapper, never()).insert(any());
        verify(messageMapper).insert(any());
    }

    @Test void sendEmptyContentFails() {
        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage(2L, "", null, user(4L, "CUSTOMER")));
    }

    @Test void sendNullContentFails() {
        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage(2L, null, null, user(4L, "CUSTOMER")));
    }

    @Test void getMessagesMarksRead() {
        when(conversationMapper.findById(1L)).thenReturn(conv(4L, 2L));
        when(messageMapper.findByConversationId(1L)).thenReturn(List.of());
        messageService.getMessages(1L, user(4L, "CUSTOMER"));
        verify(messageMapper).markRead(1L, 4L);
    }

    @Test void getMessagesNonParticipantDenied() {
        when(conversationMapper.findById(1L)).thenReturn(conv(4L, 2L));
        assertThrows(SecurityException.class, () -> messageService.getMessages(1L, user(99L, "CUSTOMER")));
    }

    @Test void getMessagesAdminAllowed() {
        when(conversationMapper.findById(1L)).thenReturn(conv(4L, 2L));
        when(messageMapper.findByConversationId(1L)).thenReturn(List.of());
        assertDoesNotThrow(() -> messageService.getMessages(1L, user(1L, "ADMINISTRATOR")));
    }

    @Test void sendToConversationNonParticipantDenied() {
        when(conversationMapper.findById(1L)).thenReturn(conv(4L, 2L));
        assertThrows(SecurityException.class, () -> messageService.sendToConversation(1L, "hi", user(99L, "CUSTOMER")));
    }

    @Test void getConversationsIncludesUnreadCount() {
        Conversation c = conv(4L, 2L);
        when(conversationMapper.findByUserId(4L)).thenReturn(List.of(c));
        when(messageMapper.countUnread(1L, 4L)).thenReturn(3);
        List<Conversation> result = messageService.getConversations(user(4L, "CUSTOMER"));
        assertEquals(3, result.get(0).getUnreadCount());
    }
}
