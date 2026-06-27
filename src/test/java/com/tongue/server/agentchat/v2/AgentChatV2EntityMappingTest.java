package com.tongue.server.agentchat.v2;

import org.junit.jupiter.api.Test;

import javax.persistence.Entity;
import javax.persistence.Table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentChatV2EntityMappingTest {

    @Test
    void conversationUsesDedicatedEntityAndTableNames() {
        assertMapping(
                AgentConversationEntity.class,
                "AgentChatV2ConversationEntity",
                "agent_chat_conversation"
        );
    }

    @Test
    void turnUsesDedicatedEntityAndTableNames() {
        assertMapping(
                AgentTurnEntity.class,
                "AgentChatV2TurnEntity",
                "agent_chat_turn"
        );
    }

    @Test
    void messageUsesDedicatedEntityAndTableNames() {
        assertMapping(
                AgentMessageEntity.class,
                "AgentChatV2MessageEntity",
                "agent_chat_message"
        );
    }

    private void assertMapping(
            Class<?> entityType,
            String expectedEntityName,
            String expectedTableName
    ) {
        Entity entity = entityType.getAnnotation(Entity.class);
        assertNotNull(entity, "Entity must declare @Entity");
        assertEquals(expectedEntityName, entity.name());

        Table table = entityType.getAnnotation(Table.class);
        assertNotNull(table, "Entity must declare an explicit @Table mapping");
        assertEquals(expectedTableName, table.name());
    }
}
