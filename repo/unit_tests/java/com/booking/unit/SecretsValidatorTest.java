package com.booking.unit;

import com.booking.config.SecretsValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SecretsValidatorTest {

    private SecretsValidator validator(String username, String password, String encKey) throws Exception {
        SecretsValidator v = new SecretsValidator();
        setField(v, "datasourceUsername", username);
        setField(v, "datasourcePassword", password);
        setField(v, "encryptionKey", encKey);
        return v;
    }

    private void setField(Object obj, String name, String value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test void validSecretsPass() throws Exception {
        assertDoesNotThrow(() ->
            validator("booking_app", "strong_pw", "abcdefghijklmnopqrstuvwxyz123456").validate());
    }

    @Test void emptyUsernameRejected() throws Exception {
        SecretsValidator v = validator("", "pw", "abcdefghijklmnopqrstuvwxyz123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("SPRING_DATASOURCE_USERNAME"));
    }

    @Test void nullUsernameRejected() throws Exception {
        SecretsValidator v = validator(null, "pw", "abcdefghijklmnopqrstuvwxyz123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("SPRING_DATASOURCE_USERNAME"));
    }

    @Test void emptyPasswordRejected() throws Exception {
        SecretsValidator v = validator("user", "", "abcdefghijklmnopqrstuvwxyz123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("SPRING_DATASOURCE_PASSWORD"));
    }

    @Test void emptyEncryptionKeyRejected() throws Exception {
        SecretsValidator v = validator("user", "pw", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("ENCRYPTION_KEY"));
    }

    @Test void shortEncryptionKeyRejected() throws Exception {
        SecretsValidator v = validator("user", "pw", "only31chars_not_enough_for_aes!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("at least 32"));
    }

    @Test void exactly32CharKeyAccepted() throws Exception {
        assertDoesNotThrow(() ->
            validator("user", "pw", "exactly32characters_for_testing!").validate());
    }
}
