package com.booking.util;

public final class MaskUtil {

    private MaskUtil() {}

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return "***" + email.substring(atIdx);
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    public static String maskCardNumber(String card) {
        if (card == null || card.length() < 4) return "***";
        return "****-****-****-" + card.substring(card.length() - 4);
    }

    public static String maskGeneric(String value) {
        if (value == null) return "***";
        if (value.length() <= 4) return "***";
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
