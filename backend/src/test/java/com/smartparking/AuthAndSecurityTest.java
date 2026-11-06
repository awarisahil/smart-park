package com.smartparking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.dto.AuthResponse;
import com.smartparking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
class AuthAndSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtService jwtService;

    private AuthResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    @Test
    void registerThenLoginReturnsTokenPair() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"itreg@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));

        AuthResponse auth = login("itreg@test.com", "secret123");
        assertNotNull(auth.token());
        assertNotNull(auth.refreshToken());
        assertTrue(jwtService.isTokenValid(auth.token()));
    }

    @Test
    void duplicateEmailRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dup\",\"email\":\"user@parking.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refreshRotatesAndReuseRevokesFamily() throws Exception {
        AuthResponse first = login("user@parking.com", "user123");

        // Normal rotation: old refresh token dies, new pair issued.
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse second = objectMapper.readValue(rotated.getResponse().getContentAsString(), AuthResponse.class);
        assertNotEquals(first.refreshToken(), second.refreshToken(), "refresh token must rotate");

        // Replaying the revoked token = theft signal: the whole family is revoked.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());

        // Even the rotated token is now dead.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());

        // The user can simply sign in again.
        assertTrue(jwtService.isTokenValid(login("user@parking.com", "user123").token()));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        AuthResponse auth = login("user@parking.com", "user123");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auth.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auth.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rbacMatrixEnforced() throws Exception {
        String userToken = "Bearer " + jwtService.generateToken("user@parking.com", "USER");
        String attendantToken = "Bearer " + jwtService.generateToken("attendant@parking.com", "ATTENDANT");
        String adminToken = "Bearer " + jwtService.generateToken("admin@parking.com", "ADMIN");

        // USER must not reach admin or attendant endpoints.
        mockMvc.perform(get("/api/reports/usage").header("Authorization", userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reservations").header("Authorization", userToken))
                .andExpect(status().isForbidden());

        // ATTENDANT must not manage slots or users.
        mockMvc.perform(post("/api/slots")
                        .header("Authorization", attendantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotNumber\":\"X-1\",\"zone\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", attendantToken))
                .andExpect(status().isForbidden());

        // ADMIN can.
        mockMvc.perform(get("/api/admin/users").header("Authorization", adminToken))
                .andExpect(status().isOk());

        // Actuator: health public, metrics admin-only.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/slots"))
                .andExpect(status().isUnauthorized());
    }
}
