package com.aurora.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull(message = "Rating is required.")
        @Min(value = 1, message = "Rating must be at least 1.")
        @Max(value = 5, message = "Rating must be at most 5.")
        Integer rating,

        @Size(max = 160, message = "Review title must be at most 160 characters.")
        String title,

        String comment
) {
}
