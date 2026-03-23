package com.domain.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL must not be blank")
    @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://")
    @Size(max = 2048, message = "URL too long")
    private String url;

    @Size(min = 3, max = 10, message = "Custom alias must be 3–10 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Alias can only contain letters, numbers, _ or -")
    private String customAlias;

    private Integer ttlDays;
}
