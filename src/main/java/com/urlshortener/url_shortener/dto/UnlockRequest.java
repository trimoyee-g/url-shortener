package com.urlshortener.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnlockRequest {

    @NotBlank(message = "Password is required")
    private String password;
}
