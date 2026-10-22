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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EventHostControllerTest contains integration tests for the
 * EventHostController, covering account creation, verification,
 * profile retrieval, name/email/password editing, and account deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventHostControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventHostRepository eventHostRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.EventHosts.ROOT;
    private static final String TEST_EMAIL = "eventhosttest@example.com";
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
            .firstName("EventHost")
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
     * Builds a JSON request body for creating a new event host.
     *
     * @param firstName the first name
     * @param lastName the last name
     * @param email the email address
     * @param password the password
     * @param dateOfBirth the date of birth in ISO format
     * @return a JSON string representing the create event host request
     */
    private String buildCreateEventHostRequestBody
    (
        String firstName,
        String lastName,
        String email,
        String password,
        String dateOfBirth
    )
    {
        return """
            {
                "firstName": "%s",
                "lastName": "%s",
                "email": "%s",
                "password": "%s",
                "dateOfBirth": "%s"
            }
            """.formatted(firstName, lastName, email, password, dateOfBirth);
    }

    /**
     * Performs a login request and returns the JWT from the response.
     *
     * @param email the email address
     * @param password the password
     * @return the JWT string from the login response
     * @throws Exception if the request fails
     */
    private String loginAndGetJwt(String email, String password) throws Exception
    {
        String loginPath = ApiPaths.BASE + ApiPaths.Sessions.ROOT + ApiPaths.Sessions.LOGIN;
        String requestBody = """
            {
                "email": "%s",
                "password": "%s"
            }
            """.formatted(email, password);

        MvcResult result = mockMvc.perform(post(loginPath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jwt").isNotEmpty())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        int jwtStart = responseBody.indexOf("\"jwt\":\"") + 7;
        int jwtEnd = responseBody.indexOf("\"", jwtStart);
        return responseBody.substring(jwtStart, jwtEnd);
    }

    @Nested
    @DisplayName("POST /api/v1/event-hosts")
    class CreateEventHostTests
    {
        @Test
        @DisplayName("Successful account creation returns 201 and sends verification email")
        void successfulCreationReturns201() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").isNotEmpty());

            // Verify that the verification email was sent.
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendVerificationEmail(emailCaptor.capture(), tokenCaptor.capture());

            assertThat(emailCaptor.getValue()).isEqualTo(TEST_EMAIL);
            assertThat(tokenCaptor.getValue()).isNotBlank();
        }

        @Test
        @DisplayName("Creating account with duplicate email returns 409")
        void duplicateEmailReturns409() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "Jane", "Smith", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("Creating account with underage date of birth returns 400")
        void underageReturns400() throws Exception
        {
            String underageDob = LocalDate.now().minusYears(17).toString();

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "Young", "Person", "young@example.com", TEST_PASSWORD, underageDob)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Creating account with blank first name returns 400")
        void blankFirstNameReturns400() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "", "Doe", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Creating account with blank last name returns 400")
        void blankLastNameReturns400() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Creating account with invalid email returns 400")
        void invalidEmailReturns400() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", "not-an-email", TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Creating account with too-short password returns 400")
        void tooShortPasswordReturns400() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", TEST_EMAIL, "short", "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Creating account with a long but weak password returns 400 and creates nothing")
        void weakPasswordReturns400() throws Exception
        {
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", TEST_EMAIL, WEAK_PASSWORD, "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Password is too weak")))
                .andExpect(jsonPath("$.message", containsString("minimum required score is 3")));

            // Neither a verification email nor an account should exist.
            verifyNoInteractions(emailService);
            assertThat(eventHostRepository.count()).isZero();
        }

        @Test
        @DisplayName("Creating account with null date of birth returns 400")
        void nullDateOfBirthReturns400() throws Exception
        {
            String requestBody = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/event-hosts/verification")
    class VerifyEventHostTests
    {
        @Test
        @DisplayName("Successful verification returns 200")
        void successfulVerificationReturns200() throws Exception
        {
            // Create an unverified account via the API to get a real verification token.
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isCreated());

            // Capture the verification token from the mocked email service.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendVerificationEmail(eq(TEST_EMAIL), tokenCaptor.capture());
            String verificationToken = tokenCaptor.getValue();

            // Use the token to verify the account.
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.VERIFICATION)
                    .param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your account has been successfully verified."));
        }

        @Test
        @DisplayName("Verification with invalid token returns 404")
        void invalidTokenReturns404() throws Exception
        {
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.VERIFICATION)
                    .param("token", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Verifying an already verified account returns 200 with appropriate message")
        void alreadyVerifiedReturns200() throws Exception
        {
            // Create an unverified account via the API.
            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildCreateEventHostRequestBody(
                        "John", "Doe", TEST_EMAIL, TEST_PASSWORD, "1990-01-01")))
                .andExpect(status().isCreated());

            // Capture the verification token.
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendVerificationEmail(eq(TEST_EMAIL), tokenCaptor.capture());
            String verificationToken = tokenCaptor.getValue();

            // Verify the account the first time.
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.VERIFICATION)
                    .param("token", verificationToken))
                .andExpect(status().isOk());

            // Verify the account again.
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.VERIFICATION)
                    .param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your account has already been verified."));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/event-hosts/profile")
    class GetEventHostProfileTests
    {
        @Test
        @DisplayName("Successful profile retrieval returns 200 with profile info")
        void successfulProfileRetrievalReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.PROFILE)
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("EventHost"))
                .andExpect(jsonPath("$.lastName").value("Tester"))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.lastLogin").isNotEmpty());
        }

        @Test
        @DisplayName("Profile retrieval without JWT returns 401")
        void profileWithoutJwtReturns401() throws Exception
        {
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Profile retrieval with invalid JWT returns 401")
        void profileWithInvalidJwtReturns401() throws Exception
        {
            mockMvc.perform(get(BASE_PATH + ApiPaths.EventHosts.PROFILE)
                    .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/event-hosts/name")
    class EditEventHostNameTests
    {
        @Test
        @DisplayName("Successful name edit returns 200")
        void successfulNameEditReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "firstName": "NewFirst",
                    "middleName": "NewMiddle",
                    "lastName": "NewLast"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.FULL_NAME)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your name has been updated."));
        }

        @Test
        @DisplayName("Name edit without JWT returns 401")
        void nameEditWithoutJwtReturns401() throws Exception
        {
            String requestBody = """
                {
                    "firstName": "NewFirst",
                    "lastName": "NewLast"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.FULL_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Name edit with blank first name returns 400")
        void blankFirstNameReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "firstName": "",
                    "lastName": "NewLast"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.FULL_NAME)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Name edit with blank last name returns 400")
        void blankLastNameReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "firstName": "NewFirst",
                    "lastName": ""
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.FULL_NAME)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/event-hosts/password")
    class EditEventHostPasswordTests
    {
        @Test
        @DisplayName("Successful password edit returns 200")
        void successfulPasswordEditReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "password": "newSecurePassword456"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.PASSWORD)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been updated."));
        }

        @Test
        @DisplayName("Password edit without JWT returns 401")
        void passwordEditWithoutJwtReturns401() throws Exception
        {
            String requestBody = """
                {
                    "password": "newSecurePassword456"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Password edit with too-short password returns 400")
        void tooShortPasswordReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "password": "short"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.PASSWORD)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Password edit with a long but weak password returns 400 and keeps the old password")
        void weakPasswordReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "password": "%s"
                }
                """.formatted(WEAK_PASSWORD);

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.PASSWORD)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Password is too weak")))
                .andExpect(jsonPath("$.message", containsString("minimum required score is 3")));

            // The original password must still work, proving nothing changed.
            loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/event-hosts/email")
    class EditEventHostEmailTests
    {
        @Test
        @DisplayName("Successful email edit returns 200")
        void successfulEmailEditReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "email": "newemail@example.com"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your email address has been updated."));
        }

        @Test
        @DisplayName("Email edit without JWT returns 401")
        void emailEditWithoutJwtReturns401() throws Exception
        {
            String requestBody = """
                {
                    "email": "newemail@example.com"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Email edit with duplicate email returns 409")
        void duplicateEmailReturns409() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createVerifiedEventHost("other@example.com", TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "email": "other@example.com"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("Email edit with same email returns 200 with appropriate message")
        void sameEmailReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "email": "%s"
                }
                """.formatted(TEST_EMAIL);

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("The provided email address is the same as your current email address."));
        }

        @Test
        @DisplayName("Email edit with invalid email returns 400")
        void invalidEmailReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "email": "not-an-email"
                }
                """;

            mockMvc.perform(patch(BASE_PATH + ApiPaths.EventHosts.EMAIL)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/event-hosts")
    class DeleteEventHostTests
    {
        @Test
        @DisplayName("Successful account deletion returns 200")
        void successfulDeletionReturns200() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your account has been successfully deleted."));
        }

        @Test
        @DisplayName("Account deletion without JWT returns 401")
        void deletionWithoutJwtReturns401() throws Exception
        {
            mockMvc.perform(delete(BASE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Account deletion with invalid JWT returns 401")
        void deletionWithInvalidJwtReturns401() throws Exception
        {
            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }
    }
}