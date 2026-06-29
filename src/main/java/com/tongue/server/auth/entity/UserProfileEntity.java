package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    public Long userId;

    @Column(length = 64)
    public String nickname;

    @Column(length = 16)
    public String gender;

    /**
     * 兼容旧客户端保留年龄字段。新客户端优先提交 birthDate，服务层会自动计算并同步年龄。
     */
    public Integer age;

    public LocalDate birthDate;

    @Column(length = 128)
    public String email;

    public Long avatarFileId;

    /**
     * 本地头像文件名，只保存随机文件名，不保存客户端原始路径。
     */
    @Column(length = 160)
    public String avatarFileName;

    @Column(columnDefinition = "text")
    public String healthFocus;

    @Column(length = 500)
    public String profileNote;

    public Double heightCm;
    public Double weightKg;
    public Double sleepHours;

    @Column(length = 32)
    public String exerciseFrequency;

    @Column(columnDefinition = "text")
    public String dietaryPreference;

    @Column(length = 16)
    public String answerDetailLevel;

    public Boolean useHealthProfile;
    public Boolean useHistoryReports;
    public Boolean useLongTermMemory;

    public Boolean tongueReminderEnabled;

    @Column(length = 8)
    public String tongueReminderTime;

    public Boolean sleepReminderEnabled;

    @Column(length = 8)
    public String sleepReminderTime;
}
