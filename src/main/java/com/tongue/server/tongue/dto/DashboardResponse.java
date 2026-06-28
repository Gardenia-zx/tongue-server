package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tongue.server.auth.dto.UserMeResponse;
import com.tongue.server.notification.entity.UserNotificationEntity;

import java.util.List;
import java.util.Map;

public class DashboardResponse {
    public UserMeResponse user;
    @JsonProperty("report_count")
    public int reportCount;
    @JsonProperty("latest_report")
    public ReportListItemResponse latestReport;
    @JsonProperty("trend_status")
    public Map<String, Object> trendStatus;
    public List<DashboardTodoResponse> todos;
    @JsonProperty("unread_notification_count")
    public long unreadNotificationCount;
    @JsonProperty("recent_notifications")
    public List<UserNotificationEntity> recentNotifications;

    public static class DashboardTodoResponse {
        public String type;
        public String title;
        public String content;
        public String action;
        @JsonProperty("report_id")
        public Long reportId;
        @JsonProperty("task_id")
        public Long taskId;
        @JsonProperty("review_id")
        public Long reviewId;
    }
}
