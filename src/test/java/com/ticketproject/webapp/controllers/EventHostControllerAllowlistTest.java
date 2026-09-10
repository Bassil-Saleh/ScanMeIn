package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.repositories.EventHostRepository;
import com.ticketproject.webapp.services.email.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that the config-driven email allowlist gates
 * account registration, login, and email changes at the HTTP layer. The
 * allowlist is enabled for this whole class via {@link TestPropertySource},
 * so only addresses at {@code allowed.com} may use the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.access.allowed-email-domains=allowed.com")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventHostControllerAllowlistTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventHostRepository eventHostRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.EventHosts.ROOT;
    private static final String LOGIN_PATH = ApiPaths.BASE + ApiPaths.Sessions.ROOT + ApiPaths.Sessions.LOGIN;
    private static final String ALLOWED_EMAIL = "me@allowed.com";
    private static final String DISALLOWED_EMAIL = "eve@other.com";
    private static final String PASSWORD = "securePassword123";

    @BeforeEach
    void setUp()
    {
        eventHostRepository.deleteAll();
    }

    /**
     * Persists a verified EventHost directly via the repository, bypassing the
     * service-layer allowlist so tests can set up accounts at disallowed domains.
     */
    private EventHost createVerifiedEventHost(String email, String password)
    {
        EventHost host = new EventHost.Builder()
            .firstName("Test")
            .lastName("Host")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .email(email)
            .plaintextPassword(password)
            .build();
        host.generateVerificationToken();
        host.setVerified(true);
        return eventHostRepository.save(host);
    }

    private String buildCreateBody(String email)
    {
        return """
            {
                "firstName": "Test",
                "lastName": "Host",
                "email": "%s",
                "password": "%s",
                "dateOfBirth": "1990-01-01"
            }
            """.formatted(email, PASSWORD);
    }

    private String loginAndGetJwt(String email, String password) throws Exception
    {
        String body = """
            {
                "email": "%s",
                "password": "%s"
            }
            """.formatted(email, password);

        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        int start = responseBody.indexOf("\"jwt\":\"") + 7;
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    @Test
    @DisplayName("Registration with an allowed email returns 201")
    void registrationWithAllowedEmailReturns201() throws Exception
    {
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildCreateBody(ALLOWED_EMAIL)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Registration with a disallowed email returns 403")
    void registrationWithDisallowedEmailReturns403() throws Exception
    {
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildCreateBody(DISALLOWED_EMAIL)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Login with a disallowed email returns 401 even if the account exists")
    void loginWithDisallowedEmailReturns401() throws Exception
    {
        // Create an account at a disallowed domain directly in the repository.
        createVerifiedEventHost(DISALLOWED_EMAIL, PASSWORD);

        String body = """
            {
                "email": "%s",
                "password": "%s"
            }
            """.formatted(DISALLOWED_EMAIL, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Changing email to a disallowed address returns 403")
    void emailChangeToDisallowedReturns403() throws Exception
    {
        createVerifiedEventHost(ALLOWED_EMAIL, PASSWORD);
        String jwt = loginAndGetJwt(ALLOWED_EMAIL, PASSWORD);

        String body = """
            {
                "email": "%s"
            }
            """.formatted(DISALLOWED_EMAIL);

        mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }
}
