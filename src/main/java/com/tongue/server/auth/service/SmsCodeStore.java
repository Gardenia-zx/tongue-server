package com.tongue.server.auth.service;

public interface SmsCodeStore {
    void put(String phone, String code, long ttlSeconds);

    String get(String phone);

    void remove(String phone);
}
