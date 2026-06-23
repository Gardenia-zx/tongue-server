package com.tongue.server.service;

import org.springframework.web.multipart.MultipartFile;

public class TongueAnalyzeCommand {

    private Long userId;
    private String conversationId;
    private String threadId;
    private String clientTraceId;
    private MultipartFile image;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getClientTraceId() {
        return clientTraceId;
    }

    public void setClientTraceId(String clientTraceId) {
        this.clientTraceId = clientTraceId;
    }

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
}
