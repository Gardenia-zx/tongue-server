package com.tongue.server.auth.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySmsCodeStore implements SmsCodeStore {

    private final ConcurrentHashMap<String, SmsCodeValue> codes =
            new ConcurrentHashMap<String, SmsCodeValue>();

    @Override
    public void put(String phone, String code, long ttlSeconds) {
        SmsCodeValue value = new SmsCodeValue();
        value.code = code;
        value.expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        codes.put(phone, value);
    }

    @Override
    public String get(String phone) {
        SmsCodeValue value = codes.get(phone);
        if (value == null) {
            return null;
        }
        if (value.expiresAt < Instant.now().getEpochSecond()) {
            codes.remove(phone);
            return null;
        }
        return value.code;
    }

    @Override
    public void remove(String phone) {
        codes.remove(phone);
    }

    private static class SmsCodeValue {
        private String code;
        private long expiresAt;
    }
}
