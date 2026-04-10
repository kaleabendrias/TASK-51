package com.booking.unit;

import com.booking.util.FieldEncryptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldEncryptorTest {

    private static final String TEST_KEY = "TestKeyForUnitTests32CharsPBKDF2!";

    @BeforeAll static void setup() {
        // Key is derived via PBKDF2 to a 32-byte AES-256 key
        FieldEncryptor.configure(TEST_KEY);
    }

    @Test void encryptDecryptRoundTrip() {
        String original = "alice@example.com";
        String encrypted = FieldEncryptor.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        assertEquals(original, FieldEncryptor.decrypt(encrypted));
    }

    @Test void encryptProducesDifferentCiphertextsEachTime() {
        String a = FieldEncryptor.encrypt("test");
        String b = FieldEncryptor.encrypt("test");
        assertNotEquals(a, b, "Different IVs should produce different ciphertexts");
    }

    @Test void encryptNullReturnsNull() {
        assertNull(FieldEncryptor.encrypt(null));
        assertNull(FieldEncryptor.decrypt(null));
    }

    @Test void decryptGarbageThrows() {
        assertThrows(RuntimeException.class, () -> FieldEncryptor.decrypt("not-valid-base64!!!"));
    }

    @Test void encryptEmptyString() {
        String enc = FieldEncryptor.encrypt("");
        assertEquals("", FieldEncryptor.decrypt(enc));
    }

    @Test void encryptSpecialChars() {
        String input = "héllo+wörld@特殊.com";
        assertEquals(input, FieldEncryptor.decrypt(FieldEncryptor.encrypt(input)));
    }

    @Test void unconfiguredThrows() {
        assertTrue(FieldEncryptor.isConfigured());
    }

    @Test void configureWithShortKeyThrows() {
        // Keys under 32 chars must be rejected for AES-256
        assertThrows(IllegalArgumentException.class, () -> FieldEncryptor.configure("short"));
        assertThrows(IllegalArgumentException.class, () -> FieldEncryptor.configure("only16characters"));
        assertThrows(IllegalArgumentException.class, () -> FieldEncryptor.configure("exactly31chars_for_this_test!!!"));
    }

    @Test void configureWith32CharKeySucceeds() {
        assertDoesNotThrow(() -> FieldEncryptor.configure("exactly32characters_for_testing!"));
        // Restore original key
        FieldEncryptor.configure(TEST_KEY);
    }

    @Test void pbkdf2DerivesConsistentKey() {
        String consistentKey = "ConsistentKeyTestWithAtLeast32Ch";
        FieldEncryptor.configure(consistentKey);
        String enc = FieldEncryptor.encrypt("round-trip");
        FieldEncryptor.configure(consistentKey);
        assertEquals("round-trip", FieldEncryptor.decrypt(enc),
                "PBKDF2 derivation should be deterministic for the same input");
        // Restore original key
        FieldEncryptor.configure(TEST_KEY);
    }
}
