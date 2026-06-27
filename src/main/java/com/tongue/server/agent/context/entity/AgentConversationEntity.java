package com.tongue.server.agent.context.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "agent_conversation")
public class AgentConversationEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(length = 128)
    public String threadId;

    public Integer threadEpoch = 1;

    @Column(length = 128)
    public String title;

    public Long activeReportId;

    public Long lastImageFileId;

    public Long summaryId;

    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";
}
