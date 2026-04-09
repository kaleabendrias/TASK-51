package com.booking.unit;

import com.booking.util.FieldEncryptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldEncryptorTest {

    @Test
    void encryptDecryptRoundTrip() {
        String original = "alice@example.com";
        String encrypted = FieldEncryptor.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        assertEquals(original, FieldEncryptor.decrypt(encrypted));
    }

    @Test
    void encryptProducesDifferentCiphertextsEachTime() {
        String a = FieldEncryptor.encrypt("test");
        String b = FieldEncryptor.encrypt("test");
        assertNotEquals(a, b, "Different IVs should produce different ciphertexts");
    }

    @Test
    void encryptNullReturnsNull() {
        assertNull(FieldEncryptor.encrypt(null));
        assertNull(FieldEncryptor.decrypt(null));
    }

    @Test
    void decryptGarbageThrows() {
        assertThrows(RuntimeException.class, () -> FieldEncryptor.decrypt("not-valid-base64!!!"));
    }

    @Test
    void encryptEmptyString() {
        String enc = FieldEncryptor.encrypt("");
        assertEquals("", FieldEncryptor.decrypt(enc));
    }

    @Test
    void encryptSpecialChars() {
        String input = "héllo+wörld@特殊.com";
        assertEquals(input, FieldEncryptor.decrypt(FieldEncryptor.encrypt(input)));
    }
}
