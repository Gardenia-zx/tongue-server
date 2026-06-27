package com.tongue.server.agent.context.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "agent_message")
public class AgentMessageEntity extends BaseEntity {

    @Column(nullable = false)
    public Long conversationId;

    @Column(nullable = false)
    public Long userId;

    @Column(length = 128)
    public String externalMessageId;

    @Column(nullable = false, length = 32)
    public String role;

    @Column(columnDefinition = "text")
    public String content;

    @Column(nullable = false, length = 32)
    public String contentType = "text";

    public Long imageFileId;

    public Long reportId;

    @Column(columnDefinition = "json")
    public String metadataJson;
}
