package com.booking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class BaseApiIT {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    protected MockHttpSession loginAs(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", username, "password", "password123"))))
            .andExpect(status().isOk());
        return session;
    }

    protected String json(Object obj) throws Exception {
        return om.writeValueAsString(obj);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseMap(MvcResult r) throws Exception {
        return om.readValue(r.getResponse().getContentAsString(), Map.class);
    }
}
