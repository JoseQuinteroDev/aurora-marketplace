package com.aurora.backend.dev;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class DevTestController {

    @GetMapping("/success")
    public ApiResponse<Void> success() {
        return ApiResponse.success("Development success endpoint is working.");
    }

    @GetMapping("/business-error")
    public ApiResponse<Void> businessError() {
        throw new BusinessException("DEV_BUSINESS_ERROR", "This is a development business error.");
    }

    @GetMapping("/not-found")
    public ApiResponse<Void> notFound() {
        throw new NotFoundException("Development resource", "demo-id");
    }

    @PostMapping("/validation-error")
    public ApiResponse<ValidationTestRequest> validationError(@Valid @RequestBody ValidationTestRequest request) {
        return ApiResponse.success("Validation passed.", request);
    }

    public record ValidationTestRequest(
            @NotBlank(message = "Name is required.")
            String name,

            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            String email,

            @NotNull(message = "Quantity is required.")
            @Min(value = 1, message = "Quantity must be at least 1.")
            Integer quantity
    ) {
    }
}
