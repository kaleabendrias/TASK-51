package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiIT extends BaseApiIT {

    @Test void loginSuccess() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMINISTRATOR"));
    }

    @Test void loginBadPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "wrong"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test void loginMissingFields() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin"))))
            .andExpect(status().isBadRequest());
    }

    @Test void meWithoutSession() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test void meWithSession() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/auth/me").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMINISTRATOR"));
    }

    @Test void logout() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/auth/logout").session(s))
            .andExpect(status().isOk());
    }

    @Test void registerSuccess() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "newreg", "email", "newreg@t.com",
                        "password", "pass123", "fullName", "New Reg", "phone", "+1-555-9999"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test void registerDuplicateUsername() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "email", "dup@t.com",
                        "password", "p", "fullName", "D"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("Username already exists")));
    }

    @Test void unauthenticatedApiBlocked() throws Exception {
        mvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized());
    }
}
