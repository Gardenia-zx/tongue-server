package com.tongue.server.auth.dto;

import javax.validation.constraints.NotBlank;

public class SmsLoginRequest {
    @NotBlank
    public String phone;
    @NotBlank
    public String code;
}
