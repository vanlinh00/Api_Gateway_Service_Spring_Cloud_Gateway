package com.example.resource.security;

import java.util.List;

public record UserContext(
        String userId,
        String username,
        String email,
        List<String> roles
) {}
