package com.tongue.server.auth.dto;

import javax.validation.constraints.NotBlank;

public class SmsSendRequest {
    @NotBlank
    public String phone;
}
