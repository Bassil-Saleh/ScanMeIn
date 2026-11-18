package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.model.entities.Attendee;
import com.ticketproject.webapp.model.entities.BlockedRegistration;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventAddress;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.EventSigningKey;
import com.ticketproject.webapp.model.enums.EventStatus;
import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.model.enums.RegistrationStatus;
import com.ticketproject.webapp.model.repositories.AttendeeRepository;
import com.ticketproject.webapp.model.repositories.BlockedRegistrationRepository;
import com.ticketproject.webapp.model.repositories.EventHostRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.services.database.CryptoService;
import com.ticketproject.webapp.services.email.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BlockedRegistrationControllerTest contains integration tests for the
 * BlockedRegistrationController, covering retrieval of blocked registrations
 * for an event as well as blocking and unblocking attendee registrations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BlockedRegistrationControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventHostRepository eventHostRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private BlockedRegistrationRepository blockedRegistrationRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.BlockedRegistrations.ROOT;
    private static final String TEST_EMAIL = "blockedregtest@example.com";
    private static final String OTHER_HOST_EMAIL = "otherblockedhost@example.com";
    private static final String TEST_PASSWORD = "securePassword123";

    /**
     * Ensure that each test starts from a fresh state so that entities created
     * by one test (especially EventHost and Attendee records with unique
     * email addresses) do not interfere with other tests.
     */
    @BeforeEach
    void setUp()
    {
        // Blocked registrations reference attendees, events, and event hosts,
        // so they must be removed before those referenced entities.
        blockedRegistrationRepository.deleteAll();
        attendeeRepository.deleteAll();
        eventRepository.deleteAll();
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
            .firstName("BlockedRegistration")
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
     * Creates and persists an Event with its EventAddress and EventSigningKey.
     *
     * @param eventHost the event host who created the event
     * @param eventType the type of the event
     * @return the persisted Event entity
     */
    private Event createEvent(EventHost eventHost, EventType eventType)
    {
        return createEvent(eventHost, eventType, "Test Event");
    }

    /**
     * Creates and persists an Event with its EventAddress and EventSigningKey.
     *
     * @param eventHost the event host who created the event
     * @param eventType the type of the event
     * @param name the name of the event
     * @return the persisted Event entity
     */
    private Event createEvent(EventHost eventHost, EventType eventType, String name)
    {
        Event event = new Event.Builder()
            .name(name)
            .description("A test event description")
            .startDateTime(LocalDateTime.now().plusDays(1))
            .endDateTime(LocalDateTime.now().plusDays(1).plusHours(2))
            .maxAttendees(100L)
            .eventHost(eventHost)
            .eventType(eventType)
            .publicId(UUID.randomUUID().toString())
            .build();

        event.setEventStatus(EventStatus.PUBLISHED);
        event.setRegistrationStatus(RegistrationStatus.OPEN);

        EventAddress address = new EventAddress.Builder()
            .addressLine1("123 Test Street")
            .city("Test City")
            .state("Test State")
            .postalCode("12345")
            .country("Test Country")
            .build();

        EventSigningKey signingKey = CryptoService.createSigningKey(event);
        event.setSigningKey(signingKey);
        event.setEventAddress(address);

        return eventRepository.save(event);
    }

    /**
     * Creates and persists an Attendee without a middle name.
     *
     * @param firstName the attendee's first name
     * @param lastName the attendee's last name
     * @param email the attendee's email address
     * @return the persisted Attendee entity
     */
    private Attendee createAttendee(String firstName, String lastName, String email)
    {
        return createAttendee(firstName, null, lastName, email);
    }

    /**
     * Creates and persists an Attendee with the given details.
     *
     * @param firstName the attendee's first name
     * @param middleName the attendee's middle name (may be null)
     * @param lastName the attendee's last name
     * @param email the attendee's email address
     * @return the persisted Attendee entity
     */
    private Attendee createAttendee(String firstName, String middleName, String lastName, String email)
    {
        Attendee attendee = new Attendee.Builder()
            .firstName(firstName)
            .middleName(middleName)
            .lastName(lastName)
            .email(email)
            .build();

        return attendeeRepository.save(attendee);
    }

    /**
     * Creates and persists a BlockedRegistration record.
     *
     * @param event the event the attendee is blocked from
     * @param attendee the blocked attendee
     * @param blockedBy the event host who authored the block
     * @param reason why the attendee was blocked (may be null)
     * @return the persisted BlockedRegistration entity
     */
    private BlockedRegistration createBlockedRegistration
    (
        Event event,
        Attendee attendee,
        EventHost blockedBy,
        String reason
    )
    {
        BlockedRegistration block = new BlockedRegistration.Builder()
            .attendee(attendee)
            .event(event)
            .blockedBy(blockedBy)
            .reason(reason)
            .build();

        return blockedRegistrationRepository.save(block);
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

    /**
     * Builds a JSON array of email addresses for a request body.
     *
     * @param emails the email addresses
     * @return a JSON array of strings
     */
    private String toEmailsJson(String... emails)
    {
        return Arrays.stream(emails)
            .map(email -> "\"" + email + "\"")
            .collect(Collectors.joining(", "));
    }

    /**
     * Builds a JSON request body containing an event public ID and emails.
     *
     * @param publicId the event's public ID
     * @param emails the email addresses to block or unblock
     * @return a JSON string representing the request body
     */
    private String buildRequestBody(String publicId, String... emails)
    {
        return """
            {
                "publicId": "%s",
                "emails": [%s]
            }
            """.formatted(publicId, toEmailsJson(emails));
    }

    /**
     * Builds a JSON request body that omits the emails field entirely.
     *
     * @param publicId the event's public ID
     * @return a JSON string missing the emails field
     */
    private String buildRequestBodyWithoutEmails(String publicId)
    {
        return """
            {
                "publicId": "%s"
            }
            """.formatted(publicId);
    }

    /**
     * Builds a JSON request body that omits the publicId field entirely.
     *
     * @param emails the email addresses to block or unblock
     * @return a JSON string missing the publicId field
     */
    private String buildRequestBodyWithoutPublicId(String... emails)
    {
        return """
            {
                "emails": [%s]
            }
            """.formatted(toEmailsJson(emails));
    }

    @Nested
    @DisplayName("GET /api/v1/blocked-registrations/{publicId}")
    class GetBlockedRegistrationsTests
    {
        @Test
        @DisplayName("Retrieval of blocked registrations returns 200 with block records")
        void retrievalReturns200WithBlockRecords() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jane", "Marie", "Smith", "jane.smith@example.com");
            createBlockedRegistration(event, attendee, host, "Spam registration");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedRegistrations").isArray())
                .andExpect(jsonPath("$.blockedRegistrations.length()").value(1))
                .andExpect(jsonPath("$.blockedRegistrations[0].firstName").value("Jane"))
                .andExpect(jsonPath("$.blockedRegistrations[0].middleName").value("Marie"))
                .andExpect(jsonPath("$.blockedRegistrations[0].lastName").value("Smith"))
                .andExpect(jsonPath("$.blockedRegistrations[0].email").value("jane.smith@example.com"))
                .andExpect(jsonPath("$.blockedRegistrations[0].blockedByEmail").value(TEST_EMAIL))
                .andExpect(jsonPath("$.blockedRegistrations[0].reason").value("Spam registration"))
                .andExpect(jsonPath("$.blockedRegistrations[0].created").isNotEmpty());
        }

        @Test
        @DisplayName("Retrieval includes the revoked timestamp for a revoked block")
        void retrievalIncludesRevokedTimestampForRevokedBlock() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jon", "Smith", "jon@example.com");

            BlockedRegistration block = createBlockedRegistration(event, attendee, host, null);
            block.setRevoked(LocalDateTime.now());
            blockedRegistrationRepository.save(block);

            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedRegistrations.length()").value(1))
                .andExpect(jsonPath("$.blockedRegistrations[0].revoked").isNotEmpty());
        }

        @Test
        @DisplayName("Retrieval only returns blocks belonging to the requested event")
        void retrievalReturnsOnlyBlocksForRequestedEvent() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event firstEvent = createEvent(host, EventType.PUBLIC, "First Event");
            Event secondEvent = createEvent(host, EventType.PUBLIC, "Second Event");
            Attendee firstAttendee = createAttendee("Jon", "Smith", "jon@example.com");
            Attendee secondAttendee = createAttendee("Jane", "Smith", "jane@example.com");
            createBlockedRegistration(firstEvent, firstAttendee, host, null);
            createBlockedRegistration(secondEvent, secondAttendee, host, null);

            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + firstEvent.getPublicId())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedRegistrations.length()").value(1))
                .andExpect(jsonPath("$.blockedRegistrations[0].email").value("jon@example.com"));
        }

        @Test
        @DisplayName("Retrieval for an event with no blocks returns an empty list")
        void retrievalWithNoBlocksReturnsEmptyList() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedRegistrations").isEmpty());
        }

        @Test
        @DisplayName("Retrieval without JWT returns 401")
        void retrievalWithoutJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Retrieval with invalid JWT returns 401")
        void retrievalWithInvalidJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Retrieval of a non-existent event returns 404")
        void retrievalOfNonExistentEventReturns404() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Retrieval of another host's event returns 401")
        void retrievalOfAnotherHostsEventReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);

            createVerifiedEventHost(OTHER_HOST_EMAIL, TEST_PASSWORD);
            String otherJwt = loginAndGetJwt(OTHER_HOST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer " + otherJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Retrieval with an event public ID longer than 36 characters returns 400")
        void retrievalWithPublicIdTooLongReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(get(BASE_PATH + "/" + "a".repeat(37))
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/blocked-registrations")
    class BlockRegistrationsTests
    {
        @Test
        @DisplayName("Blocking attendee registrations returns 201")
        void blockingAttendeesReturns201() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");
            createAttendee("Jane", "Smith", "jane@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com", "jane@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 2 registrations."));

            List<BlockedRegistration> blocks = blockedRegistrationRepository.findAllByEventId(event.getId());
            assertThat(blocks).hasSize(2);
            assertThat(blocks.stream().map(block -> block.getAttendee().getEmail()).toList())
                .containsExactlyInAnyOrder("jon@example.com", "jane@example.com");

            verify(emailService).sendEventRegistrationBlockedEmail(eq("jon@example.com"), eq(event.getName()), isNull());
            verify(emailService).sendEventRegistrationBlockedEmail(eq("jane@example.com"), eq(event.getName()), isNull());
        }

        @Test
        @DisplayName("Blocking ignores emails with no matching attendee")
        void blockingIgnoresEmailsWithNoMatchingAttendee() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com", "ghost@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 1 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(event.getId())).hasSize(1);

            verify(emailService).sendEventRegistrationBlockedEmail(eq("jon@example.com"), eq(event.getName()), isNull());
            verify(emailService, never())
                .sendEventRegistrationBlockedEmail(eq("ghost@example.com"), eq(event.getName()), isNull());
        }

        @Test
        @DisplayName("Blocking an already blocked attendee does not create a duplicate")
        void blockingAlreadyBlockedAttendeeDoesNotCreateDuplicate() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jon", "Smith", "jon@example.com");
            createBlockedRegistration(event, attendee, host, null);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 0 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(event.getId())).hasSize(1);
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("Blocking the same attendee for two events creates separate blocks")
        void blockingSameAttendeeForTwoEventsCreatesSeparateBlocks() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event firstEvent = createEvent(host, EventType.PUBLIC, "First Event");
            Event secondEvent = createEvent(host, EventType.PUBLIC, "Second Event");
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(firstEvent.getPublicId(), "jon@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 1 registrations."));

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(secondEvent.getPublicId(), "jon@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 1 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(firstEvent.getId())).hasSize(1);
            assertThat(blockedRegistrationRepository.findAllByEventId(secondEvent.getId())).hasSize(1);
        }

        @Test
        @DisplayName("Blocking with a mixed-case email still blocks the attendee")
        void blockingWithMixedCaseEmailStillBlocksAttendee() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "  JON@Example.COM  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Blocked 1 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(event.getId())).hasSize(1);
        }

        @Test
        @DisplayName("Blocking without JWT returns 401")
        void blockingWithoutJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Blocking for a non-existent event returns 404")
        void blockingForNonExistentEventReturns404() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(UUID.randomUUID().toString(), "jon@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Blocking for another host's event returns 401")
        void blockingForAnotherHostsEventReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");

            createVerifiedEventHost(OTHER_HOST_EMAIL, TEST_PASSWORD);
            String otherJwt = loginAndGetJwt(OTHER_HOST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + otherJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Blocking with a blank public ID returns 400")
        void blockingWithBlankPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody("   ", "jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Blocking without a public ID returns 400")
        void blockingWithMissingPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBodyWithoutPublicId("jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Blocking with an empty list of emails returns 400")
        void blockingWithEmptyEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Blocking without a list of emails returns 400")
        void blockingWithMissingEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBodyWithoutEmails(event.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Blocking with an event public ID longer than 36 characters returns 400")
        void blockingWithPublicIdTooLongReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody("a".repeat(37), "jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/blocked-registrations")
    class UnblockRegistrationsTests
    {
        @Test
        @DisplayName("Unblocking attendee registrations returns 200 and deletes the blocks")
        void unblockingAttendeesReturns200AndDeletesBlocks() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee jon = createAttendee("Jon", "Smith", "jon@example.com");
            Attendee jane = createAttendee("Jane", "Smith", "jane@example.com");
            createBlockedRegistration(event, jon, host, null);
            createBlockedRegistration(event, jane, host, null);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com", "jane@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unblocked 2 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(event.getId())).isEmpty();

            verify(emailService).sendEventRegistrationUnblockedEmail("jon@example.com", event.getName());
            verify(emailService).sendEventRegistrationUnblockedEmail("jane@example.com", event.getName());
        }

        @Test
        @DisplayName("Unblocking an attendee with no active block returns 200 with zero")
        void unblockingEmailWithNoActiveBlockReturns200WithZero() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unblocked 0 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(event.getId())).isEmpty();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("Unblocking only affects the requested event")
        void unblockingOnlyAffectsRequestedEvent() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event firstEvent = createEvent(host, EventType.PUBLIC, "First Event");
            Event secondEvent = createEvent(host, EventType.PUBLIC, "Second Event");
            Attendee firstAttendee = createAttendee("Jon", "Smith", "jon@example.com");
            Attendee secondAttendee = createAttendee("Jane", "Smith", "jane@example.com");
            createBlockedRegistration(firstEvent, firstAttendee, host, null);
            createBlockedRegistration(secondEvent, secondAttendee, host, null);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(firstEvent.getPublicId(), "jon@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unblocked 1 registrations."));

            assertThat(blockedRegistrationRepository.findAllByEventId(firstEvent.getId())).isEmpty();
            assertThat(blockedRegistrationRepository.findAllByEventId(secondEvent.getId())).hasSize(1);
        }

        @Test
        @DisplayName("Unblocking without JWT returns 401")
        void unblockingWithoutJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jon", "Smith", "jon@example.com");
            createBlockedRegistration(event, attendee, host, null);

            mockMvc.perform(delete(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Unblocking for a non-existent event returns 404")
        void unblockingForNonExistentEventReturns404() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(UUID.randomUUID().toString(), "jon@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Unblocking for another host's event returns 401")
        void unblockingForAnotherHostsEventReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jon", "Smith", "jon@example.com");
            createBlockedRegistration(event, attendee, host, null);

            createVerifiedEventHost(OTHER_HOST_EMAIL, TEST_PASSWORD);
            String otherJwt = loginAndGetJwt(OTHER_HOST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + otherJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId(), "jon@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Unblocking with a blank public ID returns 400")
        void unblockingWithBlankPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody("   ", "jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Unblocking without a public ID returns 400")
        void unblockingWithMissingPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBodyWithoutPublicId("jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Unblocking with an empty list of emails returns 400")
        void unblockingWithEmptyEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody(event.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Unblocking without a list of emails returns 400")
        void unblockingWithMissingEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBodyWithoutEmails(event.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Unblocking with an event public ID longer than 36 characters returns 400")
        void unblockingWithPublicIdTooLongReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            createAttendee("Jon", "Smith", "jon@example.com");
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(delete(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildRequestBody("a".repeat(37), "jon@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }
}
