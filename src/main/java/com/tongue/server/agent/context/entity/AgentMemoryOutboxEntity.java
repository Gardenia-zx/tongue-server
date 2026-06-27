package com.tongue.server.agent.context.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "agent_memory_outbox")
public class AgentMemoryOutboxEntity extends BaseEntity {

    @Column(nullable = false, length = 128, unique = true)
    public String eventId;

    @Column(nullable = false, length = 128)
    public String tenantId;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long conversationId;

    @Column(nullable = false, length = 128)
    public String threadId;

    @Column(nullable = false)
    public Integer threadEpoch = 1;

    @Column(nullable = false, length = 256)
    public String turnId;

    @Column(length = 128)
    public String userMessageId;

    @Column(length = 128)
    public String assistantMessageId;

    public Long activeReportId;

    @Column(nullable = false, length = 64)
    public String eventType = "AGENT_TURN_COMPLETED";

    @Column(nullable = false, length = 32)
    public String status = "NEW";

    public Integer retryCount = 0;

    @Column(columnDefinition = "json")
    public String payloadRefJson;
}
