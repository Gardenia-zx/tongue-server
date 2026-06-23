package com.tongue.server.auth;

public class CurrentUser {

    public Long userId;
    public String phone;
    public String role;

    public CurrentUser() {
    }

    public CurrentUser(Long userId, String phone, String role) {
        this.userId = userId;
        this.phone = phone;
        this.role = role;
    }
}
