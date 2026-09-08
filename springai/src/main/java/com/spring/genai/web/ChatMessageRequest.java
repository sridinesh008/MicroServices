package com.spring.genai.web;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(@NotBlank String message) {
}
