package com.meridian.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meridian.admin.config.JwtProperties;
import com.meridian.admin.config.JwtService;
import com.meridian.admin.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthApiTest extends ApiTestBase {

    @Autowired JwtProperties jwtProperties;

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        var user = newUser(Role.REVIEWER);
        postAs(null, "/api/auth/login", Map.of("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.role").value("REVIEWER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        var user = newUser(Role.REVIEWER);
        var wrongPassword = postAs(null, "/api/auth/login", Map.of("email", user.getEmail(), "password", "nope"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        var unknownEmail = postAs(null, "/api/auth/login", Map.of("email", "nobody@test.example", "password", "nope"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPassword).get("message")).isEqualTo(json.readTree(unknownEmail).get("message"));
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        var user = newUser(Role.REVIEWER);
        user.deactivate();
        em.flush();
        postAs(null, "/api/auth/login", Map.of("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingTokenStopsWorkingWhenUserIsDeactivated() throws Exception {
        var user = newUser(Role.REVIEWER);
        String token = login(user);
        getAs(token, "/api/auth/me").andExpect(status().isOk());

        user.deactivate();
        em.flush();
        getAs(token, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        getAs(null, "/api/applications").andExpect(status().isUnauthorized());
        getAs(null, "/api/dashboard/overview").andExpect(status().isUnauthorized());
        postAs(null, "/api/applications", request(Map.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageAndTamperedTokensAreRejected() throws Exception {
        getAs("not-a-jwt", "/api/auth/me").andExpect(status().isUnauthorized());

        String valid = tokenFor(Role.REVIEWER);
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA";
        getAs(tampered, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        var user = newUser(Role.REVIEWER);
        // Issue a token two days in the past with the real secret, so only the expiry is wrong.
        var pastClock = Clock.offset(Clock.systemUTC(), Duration.ofDays(-2));
        var expired = new JwtService(jwtProperties, pastClock).issue(user.getId()).token();
        getAs(expired, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() throws Exception {
        var user = newUser(Role.REVIEWER);
        var other = new JwtService(new JwtProperties("a-completely-different-secret-key-0123456789", 60), Clock.systemUTC());
        getAs(other.issue(user.getId()).token(), "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void loginValidatesItsInput() throws Exception {
        postAs(null, "/api/auth/login", Map.of("email", "not-an-email", "password", "x"))
                .andExpect(status().isBadRequest());
    }
}
