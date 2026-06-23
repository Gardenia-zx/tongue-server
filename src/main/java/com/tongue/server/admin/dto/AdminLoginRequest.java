package com.tongue.server.admin.dto;

import javax.validation.constraints.NotBlank;

public class AdminLoginRequest {

    @NotBlank
    public String username;

    @NotBlank
    public String password;
}
