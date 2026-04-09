package com.booking.domain;

import com.booking.util.FieldEncryptor;
import com.booking.util.MaskUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class User {
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    private Long roleId;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer pointsBalance;
    private String department;
    private String team;

    // Joined field
    private String roleName;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    @JsonIgnore
    public String getPhone() { return phone; }
    @JsonProperty("phone")
    public String getPhoneMasked() {
        if (phone == null) return null;
        try {
            // Phone may be encrypted at rest — try to decrypt then mask
            String decrypted = FieldEncryptor.decrypt(phone);
            return MaskUtil.maskPhone(decrypted);
        } catch (Exception e) {
            // Plaintext fallback for legacy unencrypted data
            return MaskUtil.maskPhone(phone);
        }
    }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getPointsBalance() { return pointsBalance; }
    public void setPointsBalance(Integer pointsBalance) { this.pointsBalance = pointsBalance; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
}
