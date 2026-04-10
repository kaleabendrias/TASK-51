package com.booking.unit;

import com.booking.domain.PhotographerDto;
import com.booking.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PhotographerDtoTest {

    @Test void dtoExposesOnlyPublicFields() {
        User user = new User();
        user.setId(2L);
        user.setUsername("photo1");
        user.setFullName("Photographer One");
        user.setEmail("photo1@test.com");
        user.setEnabled(true);
        user.setRoleId(2L);

        PhotographerDto dto = new PhotographerDto(user);

        assertEquals(2L, dto.getId());
        assertEquals("photo1", dto.getUsername());
        assertEquals("Photographer One", dto.getFullName());
    }

    @Test void dtoSerializationExcludesSensitiveFields() throws Exception {
        User user = new User();
        user.setId(3L);
        user.setUsername("photo2");
        user.setFullName("Photographer Two");
        user.setEmail("sensitive@test.com");
        user.setEnabled(false);
        user.setRoleId(2L);
        user.setPhone("555-0003");

        PhotographerDto dto = new PhotographerDto(user);
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(dto);
        Map<?, ?> map = om.readValue(json, Map.class);

        // Only id, username, fullName should be present
        assertTrue(map.containsKey("id"));
        assertTrue(map.containsKey("username"));
        assertTrue(map.containsKey("fullName"));

        // Sensitive fields must not be present
        assertFalse(map.containsKey("email"));
        assertFalse(map.containsKey("phone"));
        assertFalse(map.containsKey("enabled"));
        assertFalse(map.containsKey("roleId"));
        assertFalse(map.containsKey("passwordHash"));
        assertFalse(map.containsKey("department"));
        assertFalse(map.containsKey("team"));
    }
}
