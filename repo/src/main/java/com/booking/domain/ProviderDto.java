package com.booking.domain;

/**
 * Read-only DTO for the provider discovery path (/api/users/providers).
 * Covers both PHOTOGRAPHER and SERVICE_PROVIDER roles.
 * Shields sensitive fields like email, phone, enabled status, and account metadata.
 */
public class ProviderDto {
    private final Long id;
    private final String username;
    private final String fullName;
    private final String role;

    public ProviderDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.role = user.getRoleName();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
}
