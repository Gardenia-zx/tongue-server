package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.context.entity.AgentChatConversationEntity;
import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import com.tongue.server.agent.context.entity.AgentChatTurnEntity;
import com.tongue.server.agent.context.repository.AgentChatConversationRepository;
import com.tongue.server.agent.context.repository.AgentChatMessageRepository;
import com.tongue.server.agent.context.repository.AgentChatTurnRepository;
import com.tongue.server.agent.dto.AgentChatV2Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatTurnStoreTest {

    @Test
    void beginReusesThreadConversationWhenClientConversationIdChanged() {
        AgentChatConversationRepository conversations = mock(AgentChatConversationRepository.class);
        AgentChatTurnRepository turns = mock(AgentChatTurnRepository.class);
        AgentChatMessageRepository messages = mock(AgentChatMessageRepository.class);
        AgentChatConversationEntity existingConversation = conversation("conversation-old", "thread-1");

        when(turns.findByUserIdAndRequestId(7L, "req-1")).thenReturn(Optional.empty());
        when(conversations.findByUserIdAndConversationId(7L, "conversation-new")).thenReturn(Optional.empty());
        when(conversations.findByUserIdAndThreadIdAndThreadEpoch(7L, "thread-1", 1))
                .thenReturn(Optional.of(existingConversation));
        when(turns.saveAndFlush(any(AgentChatTurnEntity.class))).thenAnswer(invocation -> {
            AgentChatTurnEntity turn = invocation.getArgument(0);
            turn.setId(100L);
            return turn;
        });
        when(messages.existsByMessageId("msg-user-1")).thenReturn(false);
        when(messages.countByUserIdAndConversationId(7L, "conversation-old")).thenReturn(2L);

        AgentChatTurnStore store = new AgentChatTurnStore(
                conversations,
                turns,
                messages,
                mock(JdbcTemplate.class),
                new ObjectMapper()
        );

        AgentChatTurnStore.BeginTurnResult result = store.begin(
                7L,
                request(),
                "conversation-new",
                "turn-1",
                "msg-assistant-1",
                "trace-1",
                "hash-1",
                "NONE",
                null
        );

        assertEquals("conversation-old", result.getConversation().getConversationId());
        assertEquals("conversation-old", result.getTurn().getConversationId());

        ArgumentCaptor<AgentChatMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(AgentChatMessageEntity.class);
        verify(messages).save(messageCaptor.capture());
        assertEquals("conversation-old", messageCaptor.getValue().getConversationId());
        assertEquals(3L, messageCaptor.getValue().getSequenceNo());
    }

    private AgentChatV2Request request() {
        AgentChatV2Request request = new AgentChatV2Request();
        request.setRequestId("req-1");
        request.setClientMessageId("msg-user-1");
        request.setThreadId("thread-1");
        AgentChatV2Request.Message message = new AgentChatV2Request.Message();
        message.setContent("给我详细解释一下什么是脾虚");
        request.setMessage(message);
        return request;
    }

    private AgentChatConversationEntity conversation(String conversationId, String threadId) {
        LocalDateTime now = LocalDateTime.now();
        AgentChatConversationEntity conversation = new AgentChatConversationEntity();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setConversationId(conversationId);
        conversation.setThreadId(threadId);
        conversation.setThreadEpoch(1);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversation;
    }
}
