package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.repositories.EventHostRepository;
import com.ticketproject.webapp.services.email.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PasswordResetTokenControllerTest contains integration tests for the
 * PasswordResetTokenController, covering password reset token creation
 * and usage routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetTokenControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventHostRepository eventHostRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.PasswordResetTokens.ROOT;
    private static final String TEST_EMAIL = "resettest@example.com";
    private static final String TEST_PASSWORD = "Correct-Horse-Battery-Staple-42!";

    /**
     * A password which satisfies the 12-128 character length requirement but
     * only scores 1 out of 4 in zxcvbn, so it must be rejected as too weak.
     */
    private static final String WEAK_PASSWORD = "password1234";

    /**
     * Ensure that each test starts from a fresh state so that
     * when multiple tests attempt to create EventHost entities
     * with the same email address, executing those tests successively
     * won't result in a unique constraint violation.
     */
    @BeforeEach
    void setUp()
    {
        eventHostRepository.deleteAll();
    }

    /**
     * Creates and persists a verified EventHost for use in tests.
     *
     * @param email the email address for the event host
     * @param password the plaintext password for the event host
     * @return the persisted EventHost entity
     */
    private EventHost createVerifiedEventHost(String email, String password)
    {
        EventHost host = new EventHost.Builder()
            .firstName("Reset")
            .lastName("Tester")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .email(email)
            .plaintextPassword(password)
            .build();
        host.generateVerificationToken();
        host.setVerified(true);
        return eventHostRepository.save(host);
    }

    /**
     * Builds a JSON request body for using a password reset token.
     *
     * @param resetToken the password reset token
     * @param password the new password
     * @return a JSON string representing the use password reset token request
     */
    private String buildUseTokenRequestBody(String resetToken, String password)
    {
        return """
            {
                "passwordResetToken": "%s",
                "password": "%s"
            }
            """.formatted(resetToken, password);
    }

    @Nested
    @DisplayName("POST /api/v1/password-reset-tokens")
    class CreatePasswordResetTokenTests
    {
        @Test
        @DisplayName("Requesting reset token for existing account returns 201 and sends email")
        void existingAccountReturns201AndSendsEmail() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "email": "%s"
                }
                """.formatted(TEST_EMAIL);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(
                    "If an account with the provided email address exists, a password reset token will be sent to that address."));

            // Verify that the password reset email was sent.
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(emailCaptor.capture(), tokenCaptor.capture());

            assertThat(emailCaptor.getValue()).isEqualTo(TEST_EMAIL);
            assertThat(tokenCaptor.getValue()).isNotBlank();
        }

        @Test
        @DisplayName("Requesting reset token for non-existent account returns 201 with same message")
        void nonExistentAccountReturns201WithSameMessage() throws Exception
        {
            String requestBody = """
                {
                    "email": "nonexistent@example.com"
                }
                """;

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(
                    "If an account with the provided email address exists, a password reset token will be sent to that address."));
        }

        @Test
        @DisplayName("Requesting reset token with blank email returns 400")
        void blankEmailReturns400() throws Exception
        {
            String requestBody = """
                {
                    "email": ""
                }
                """;

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Requesting reset token with invalid email returns 400")
        void invalidEmailReturns400() throws Exception
        {
            String requestBody = """
                {
                    "email": "not-an-email"
                }
                """;

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/password-reset-tokens")
    class UsePasswordResetTokenTests
    {
        @Test
        @DisplayName("Using valid reset token returns 200 and resets password")
        void validTokenReturns200AndResetsPassword() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);

            // Request a password reset token.
            String createRequestBody = """
                {
                    "email": "%s"
                }
                """.formatted(TEST_EMAIL);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestBody))
                .andExpect(status().isCreated());

            // Capture the reset token from the mocked email service.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), tokenCaptor.capture());
            String resetToken = tokenCaptor.getValue();

            // Use the reset token to change the password.
            String useRequestBody = """
                {
                    "passwordResetToken": "%s",
                    "password": "newSecurePassword456"
                }
                """.formatted(resetToken);

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(useRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been reset."));
        }

        @Test
        @DisplayName("Using invalid reset token returns 401")
        void invalidTokenReturns401() throws Exception
        {
            String requestBody = """
                {
                    "passwordResetToken": "invalid-token",
                    "password": "newSecurePassword456"
                }
                """;

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Using reset token with blank token returns 400")
        void blankTokenReturns400() throws Exception
        {
            String requestBody = """
                {
                    "passwordResetToken": "",
                    "password": "newSecurePassword456"
                }
                """;

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Using reset token with too-short password returns 400")
        void tooShortPasswordReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);

            // Request a password reset token.
            String createRequestBody = """
                {
                    "email": "%s"
                }
                """.formatted(TEST_EMAIL);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestBody))
                .andExpect(status().isCreated());

            // Capture the reset token from the mocked email service.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), tokenCaptor.capture());
            String resetToken = tokenCaptor.getValue();

            // Try to use the reset token with a too-short password.
            String useRequestBody = """
                {
                    "passwordResetToken": "%s",
                    "password": "short"
                }
                """.formatted(resetToken);

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(useRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Using reset token with a long but weak password returns 400 and leaves the token usable")
        void weakPasswordReturns400AndDoesNotConsumeToken() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);

            // Request a password reset token.
            String createRequestBody = """
                {
                    "email": "%s"
                }
                """.formatted(TEST_EMAIL);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestBody))
                .andExpect(status().isCreated());

            // Capture the reset token from the mocked email service.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), tokenCaptor.capture());
            String resetToken = tokenCaptor.getValue();

            // Try to use the reset token with a long but weak password.
            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildUseTokenRequestBody(resetToken, WEAK_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Password is too weak")))
                .andExpect(jsonPath("$.message", containsString("minimum required score is 3")));

            // The rejected request must not have consumed the token, so the
            // same token still works when a strong password is supplied.
            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildUseTokenRequestBody(resetToken, "newSecurePassword456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been reset."));
        }

        @Test
        @DisplayName("Using reset token with blank password returns 400")
        void blankPasswordReturns400() throws Exception
        {
            String requestBody = """
                {
                    "passwordResetToken": "some-token",
                    "password": ""
                }
                """;

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }
}