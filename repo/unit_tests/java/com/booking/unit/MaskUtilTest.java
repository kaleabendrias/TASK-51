package com.booking.unit;

import com.booking.util.MaskUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaskUtilTest {

    @Test void maskEmailNormal() { assertEquals("al***@example.com", MaskUtil.maskEmail("alice@example.com")); }
    @Test void maskEmailShort() { assertEquals("***@x.com", MaskUtil.maskEmail("ab@x.com")); }
    @Test void maskEmailNull() { assertEquals("***", MaskUtil.maskEmail(null)); }
    @Test void maskEmailNoAt() { assertEquals("***", MaskUtil.maskEmail("nope")); }

    @Test void maskPhoneNormal() { assertEquals("***0004", MaskUtil.maskPhone("+1-555-0004")); }
    @Test void maskPhoneShort() { assertEquals("***", MaskUtil.maskPhone("12")); }
    @Test void maskPhoneNull() { assertEquals("***", MaskUtil.maskPhone(null)); }

    @Test void maskCardNormal() { assertEquals("****-****-****-5678", MaskUtil.maskCardNumber("1234567890125678")); }
    @Test void maskCardShort() { assertEquals("***", MaskUtil.maskCardNumber("12")); }
    @Test void maskCardNull() { assertEquals("***", MaskUtil.maskCardNumber(null)); }

    @Test void maskGenericNormal() { assertEquals("he***ld", MaskUtil.maskGeneric("helloworld")); }
    @Test void maskGenericShort() { assertEquals("***", MaskUtil.maskGeneric("abc")); }
    @Test void maskGenericNull() { assertEquals("***", MaskUtil.maskGeneric(null)); }
}
