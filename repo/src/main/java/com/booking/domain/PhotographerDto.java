package com.booking.domain;

/**
 * Read-only DTO for the photographer discovery path (/api/users/photographers).
 * Shields sensitive fields like email, phone, enabled status, and account metadata
 * from unauthorized authenticated roles.
 */
public class PhotographerDto {
    private final Long id;
    private final String username;
    private final String fullName;

    public PhotographerDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
}
