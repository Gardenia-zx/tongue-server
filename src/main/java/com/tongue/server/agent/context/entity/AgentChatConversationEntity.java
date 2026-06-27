package com.tongue.server.agent.context.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_chat_conversation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_agent_chat_conversation_user_conversation", columnNames = {"user_id", "conversation_id"}),
                @UniqueConstraint(name = "uk_agent_chat_conversation_user_thread_epoch", columnNames = {"user_id", "thread_id", "thread_epoch"})
        },
        indexes = {
                @Index(name = "idx_agent_chat_conversation_user_updated", columnList = "user_id,updated_at"),
                @Index(name = "idx_agent_chat_conversation_active_report", columnList = "active_report_id")
        }
)
public class AgentChatConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "thread_id", nullable = false, length = 128)
    private String threadId;

    @Column(name = "thread_epoch", nullable = false)
    private Integer threadEpoch = 1;

    @Column(name = "active_report_id")
    private Long activeReportId;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public Integer getThreadEpoch() { return threadEpoch; }
    public void setThreadEpoch(Integer threadEpoch) { this.threadEpoch = threadEpoch; }
    public Long getActiveReportId() { return activeReportId; }
    public void setActiveReportId(Long activeReportId) { this.activeReportId = activeReportId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
