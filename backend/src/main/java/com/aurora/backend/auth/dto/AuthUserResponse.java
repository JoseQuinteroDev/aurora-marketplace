package com.aurora.backend.auth.dto;

import java.util.UUID;

import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;

public record AuthUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role
) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );
    }
}
