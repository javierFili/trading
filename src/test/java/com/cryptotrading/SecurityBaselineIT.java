package com.cryptotrading;

import com.cryptotrading.security.application.UserAccountService;
import com.cryptotrading.security.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityBaselineIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserAccountService users;
    @Autowired
    JsonMapper json;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void openApiDocumentationIsGenerated() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{id}/approve']").exists());
    }

    @Test
    void staticUiIsServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void apiAndSensitiveActuatorEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/proposals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlRSQURFUiJdfQ.c2lnbmF0dXJl";
        mvc.perform(get("/api/v1/proposals").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesTokenAndWrongPasswordLocksAfterFiveAttempts() throws Exception {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        users.createUser(username, "correct-horse-battery", UserRole.VIEWER, "test");

        String token = login(username, "correct-horse-battery");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER"));

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "password", "wrong-password!"))))
                    .andExpect(status().isUnauthorized());
        }
        // Locked: even the correct password is refused now.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "correct-horse-battery"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotApproveOrRunBacktests() throws Exception {
        String username = "viewer" + UUID.randomUUID().toString().substring(0, 8);
        users.createUser(username, "viewer-password-123", UserRole.VIEWER, "test");
        String token = login(username, "viewer-password-123");
        mvc.perform(post("/api/v1/proposals/" + UUID.randomUUID() + "/approve").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"contentHash\":\"" + "a".repeat(64) + "\",\"totpCode\":\"123456\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/backtests").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BTC/USDT\",\"timeframe\":\"H1\",\"from\":\"2025-01-01T00:00:00Z\",\"to\":\"2025-02-01T00:00:00Z\",\"strategyCode\":\"EMA_RSI_MACD\"}"))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }
}
