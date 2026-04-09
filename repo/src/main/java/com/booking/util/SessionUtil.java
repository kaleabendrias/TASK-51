package com.booking.util;

import com.booking.domain.User;
import jakarta.servlet.http.HttpSession;

public final class SessionUtil {

    private static final String USER_KEY = "currentUser";

    private SessionUtil() {}

    public static void setCurrentUser(HttpSession session, User user) {
        session.setAttribute(USER_KEY, user);
    }

    public static User getCurrentUser(HttpSession session) {
        return (User) session.getAttribute(USER_KEY);
    }

    public static boolean isAdmin(HttpSession session) {
        User user = getCurrentUser(session);
        return user != null && "ADMINISTRATOR".equals(user.getRoleName());
    }

    public static boolean hasRole(HttpSession session, String roleName) {
        User user = getCurrentUser(session);
        return user != null && roleName.equals(user.getRoleName());
    }
}
