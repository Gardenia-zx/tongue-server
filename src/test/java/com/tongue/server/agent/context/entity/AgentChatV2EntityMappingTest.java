package com.tongue.server.agent.context.entity;

import org.junit.jupiter.api.Test;

import javax.persistence.Table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentChatV2EntityMappingTest {

    @Test
    void conversationUsesDedicatedChatTable() {
        assertTableName(AgentChatConversationEntity.class, "agent_chat_conversation");
    }

    @Test
    void turnUsesDedicatedChatTable() {
        assertTableName(AgentChatTurnEntity.class, "agent_chat_turn");
    }

    @Test
    void messageUsesDedicatedChatTable() {
        assertTableName(AgentChatMessageEntity.class, "agent_chat_message");
    }

    private void assertTableName(Class<?> entityType, String expectedTableName) {
        Table table = entityType.getAnnotation(Table.class);
        assertNotNull(table, "Entity must declare an explicit @Table mapping");
        assertEquals(expectedTableName, table.name());
    }
}
