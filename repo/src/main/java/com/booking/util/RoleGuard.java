package com.booking.util;

import com.booking.domain.User;
import jakarta.servlet.http.HttpSession;

public final class RoleGuard {

    private RoleGuard() {}

    public static User requireLogin(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        if (user == null) throw new SecurityException("Authentication required");
        return user;
    }

    public static User requireAdmin(HttpSession session) {
        User user = requireLogin(session);
        if (!"ADMINISTRATOR".equals(user.getRoleName())) {
            throw new SecurityException("Administrator access required");
        }
        return user;
    }

    public static User requireRole(HttpSession session, String... roles) {
        User user = requireLogin(session);
        for (String role : roles) {
            if (role.equals(user.getRoleName())) return user;
        }
        throw new SecurityException("Insufficient role: required one of " + String.join(", ", roles));
    }

    public static void requireOwnerOrAdmin(HttpSession session, Long ownerId) {
        User user = requireLogin(session);
        if (!"ADMINISTRATOR".equals(user.getRoleName()) && !user.getId().equals(ownerId)) {
            throw new SecurityException("Access denied");
        }
    }
}
