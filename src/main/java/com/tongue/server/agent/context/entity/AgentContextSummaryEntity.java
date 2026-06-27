package com.tongue.server.agent.context.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "agent_context_summary")
public class AgentContextSummaryEntity extends BaseEntity {

    @Column(nullable = false)
    public Long conversationId;

    @Column(nullable = false)
    public Long userId;

    @Column(columnDefinition = "text")
    public String summaryText;

    @Column(columnDefinition = "json")
    public String structuredSummaryJson;

    public Long sourceStartMessageId;

    public Long sourceEndMessageId;

    @Column(columnDefinition = "json")
    public String sourceMessageIdsJson;

    @Column(columnDefinition = "json")
    public String sourceReportIdsJson;

    @Column(columnDefinition = "json")
    public String sourceFileIdsJson;

    @Column(columnDefinition = "json")
    public String sourceEvidenceIdsJson;

    @Column(length = 64)
    public String compressionVersion = "context-summary-v1";

    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";
}
