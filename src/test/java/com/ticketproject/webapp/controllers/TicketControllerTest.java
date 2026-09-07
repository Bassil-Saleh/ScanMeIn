package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.model.entities.Attendee;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventAddress;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.EventSigningKey;
import com.ticketproject.webapp.model.entities.Ticket;
import com.ticketproject.webapp.model.enums.EventStatus;
import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.model.enums.RegistrationStatus;
import com.ticketproject.webapp.model.repositories.AttendeeRepository;
import com.ticketproject.webapp.model.repositories.EventHostRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.model.repositories.TicketRepository;
import com.ticketproject.webapp.services.database.CryptoService;
import com.ticketproject.webapp.services.email.EmailService;
import com.ticketproject.webapp.services.model.TicketService;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TicketControllerTest contains integration tests for the TicketController,
 * covering routes that perform work on Ticket entities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventHostRepository eventHostRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private TicketService ticketService;

    @MockitoBean
    private EmailService emailService;

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.Tickets.ROOT + ApiPaths.Tickets.INVITATION;
    private static final String DELETE_BASE_PATH = ApiPaths.BASE + ApiPaths.Tickets.ROOT;
    private static final String TEST_EMAIL = "tickettest@example.com";
    private static final String TEST_PASSWORD = "securePassword123";

    /**
     * Ensure that each test starts from a fresh state so that
     * when multiple tests attempt to create EventHost entities
     * with the same email address, executing those tests successively
     * won't result in a unique constraint violation.
     */
    @BeforeEach
    void setUp()
    {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        eventHostRepository.deleteAll();
        attendeeRepository.deleteAll();
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
            .firstName("Ticket")
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
        Event event = new Event.Builder()
            .name("Test Event")
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
     * Creates and persists an Attendee with the default test email.
     *
     * @return the persisted Attendee entity
     */
    private Attendee createAttendee()
    {
        return createAttendee("jon@example.com");
    }

    /**
     * Creates and persists an Attendee with the given email address.
     *
     * @param email the attendee's email address
     * @return the persisted Attendee entity
     */
    private Attendee createAttendee(String email)
    {
        Attendee attendee = new Attendee.Builder()
            .firstName("Jon")
            .lastName("Smith")
            .email(email)
            .build();

        return attendeeRepository.save(attendee);
    }

    /**
     * Creates and persists a Ticket for the given event and attendee.
     *
     * @param event the event the ticket belongs to
     * @param attendee the attendee the ticket belongs to
     * @return the persisted Ticket entity
     */
    private Ticket createTicket(Event event, Attendee attendee)
    {
        Ticket ticket = ticketService.createSignedTicket(event);
        ticket.setAttendee(attendee);
        return ticketRepository.save(ticket);
    }

    /**
     * Builds a JSON request body for responding to an invitation.
     *
     * @param publicToken the ticket's public token
     * @param invitationResponse the invitation response (ACCEPTED, REJECTED, or PENDING)
     * @param message the optional message (may be null)
     * @return a JSON string representing the respond to invitation request
     */
    private String buildRespondToInvitationRequestBody
    (
        String publicToken,
        String invitationResponse,
        String message
    )
    {
        if (message == null)
        {
            return """
                {
                    "publicToken": "%s",
                    "invitationResponse": "%s"
                }
                """.formatted(publicToken, invitationResponse);
        }
        return """
            {
                "publicToken": "%s",
                "invitationResponse": "%s",
                "message": "%s"
            }
            """.formatted(publicToken, invitationResponse, message);
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
     * Builds a JSON request body for deleting tickets.
     *
     * @param publicId the event's public ID
     * @param emails the list of attendee emails whose tickets should be deleted
     * @return a JSON string representing the delete tickets request
     */
    private String buildDeleteTicketsRequestBody(String publicId, List<String> emails)
    {
        String emailsJson = emails
            .stream()
            .map(email -> "\"" + email + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
        return """
            {
                "publicId": "%s",
                "emails": [%s]
            }
            """.formatted(publicId, emailsJson);
    }

    @Nested
    @DisplayName("PATCH /api/v1/tickets/invitation")
    class RespondToInvitationTests
    {
        @Test
        @DisplayName("Successful accept response returns 200 and sends response email")
        void successfulAcceptResponseReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Responded to invitation with response: ACCEPTED"));

            // Verify that the invitation response email was sent with the correct arguments.
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendInvitationResponseEmail
            (
                emailCaptor.capture(),
                anyString(),
                anyString(),
                responseCaptor.capture(),
                anyString()
            );

            assertThat(emailCaptor.getValue()).isEqualTo(TEST_EMAIL);
            assertThat(responseCaptor.getValue()).isEqualTo("accepted");
        }

        @Test
        @DisplayName("Successful reject response returns 200")
        void successfulRejectResponseReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "REJECTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Responded to invitation with response: REJECTED"));
        }

        @Test
        @DisplayName("Responding with PENDING returns 400")
        void respondingWithPendingReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "PENDING",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding for a public event ticket returns 400")
        void respondingForPublicEventReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding with non-existent ticket returns 404")
        void nonExistentTicketReturns404() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String publicToken = ticket.getPublicToken();

            // Once the event is deleted, the format of the ticket's
            // public token will still be syntactically valid, but it
            // will contain a token identifier to a ticket that no longer exists.
            eventRepository.delete(event);
            eventRepository.flush();

            String requestBody = buildRespondToInvitationRequestBody
            (
                publicToken,
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Responding with invalid token format returns 400")
        void invalidTokenFormatReturns400() throws Exception
        {
            String requestBody = buildRespondToInvitationRequestBody
            (
                "incorrect_token_format",
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding with blank public token returns 400")
        void blankPublicTokenReturns400() throws Exception
        {
            String requestBody = buildRespondToInvitationRequestBody
            (
                "",
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding with null invitation response returns 400")
        void nullInvitationResponseReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding with message longer than 5000 characters returns 400")
        void messageTooLongReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String longMessage = "a".repeat(5001);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "ACCEPTED",
                longMessage
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Responding to already scanned ticket returns 409")
        void alreadyScannedTicketReturns409() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            // Mark the ticket as already scanned (attendee is present).
            ticket.setPresent(true);
            ticket = ticketRepository.save(ticket);

            String requestBody = buildRespondToInvitationRequestBody
            (
                ticket.getPublicToken(),
                "ACCEPTED",
                null
            );

            mockMvc.perform(patch(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tickets")
    class DeleteTicketsTests
    {
        @Test
        @DisplayName("Successful deletion of a single ticket returns 200")
        void successfulSingleTicketDeletionReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee("jon@example.com");
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deleted 1 ticket."));

            // Verify that the ticket was removed from the database.
            assertThat(ticketRepository.findAllActiveTicketsByEventId(event.getId())).isEmpty();
        }

        @Test
        @DisplayName("Successful deletion of multiple tickets returns 200")
        void successfulMultipleTicketDeletionReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee1 = createAttendee("jon@example.com");
            Attendee attendee2 = createAttendee("jane@example.com");
            Ticket ticket1 = createTicket(event, attendee1);
            Ticket ticket2 = createTicket(event, attendee2);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("jon@example.com", "jane@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deleted 2 tickets."));

            // Verify that both tickets were removed from the database.
            assertThat(ticketRepository.findAllActiveTicketsByEventId(event.getId())).isEmpty();
        }

        @Test
        @DisplayName("Ticket deletion without JWT returns 401")
        void deletionWithoutJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee("jon@example.com");
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Ticket deletion with invalid JWT returns 401")
        void deletionWithInvalidJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            Attendee attendee = createAttendee("jon@example.com");
            Ticket ticket = createTicket(event, attendee);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer invalid.jwt.token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Deleting another host's tickets returns 401")
        void deletingAnotherHostsTicketsReturns401() throws Exception
        {
            EventHost host1 = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host1, EventType.PRIVATE);
            Attendee attendee = createAttendee("jon@example.com");
            Ticket ticket = createTicket(event, attendee);

            createVerifiedEventHost("other@example.com", TEST_PASSWORD);
            String otherJwt = loginAndGetJwt("other@example.com", TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + otherJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Deleting tickets for a non-existent event returns 404")
        void nonExistentEventReturns404() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                UUID.randomUUID().toString(),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Deleting tickets with emails that match no active tickets returns 404")
        void noMatchingTicketsReturns404() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of("nonexistent@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Request with blank publicId returns 400")
        void blankPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                "",
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Request with missing publicId returns 400")
        void missingPublicIdReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "emails": ["jon@example.com"]
                }
                """;

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Request with an empty emails list returns 400")
        void emptyEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                event.getPublicId(),
                List.of()
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Request with a missing emails field returns 400")
        void missingEmailsReturns400() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PRIVATE);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicId": "%s"
                }
                """.formatted(event.getPublicId());

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Request with a publicId longer than 36 characters returns 400")
        void publicIdTooLongReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = buildDeleteTicketsRequestBody
            (
                "a".repeat(37),
                List.of("jon@example.com")
            );

            mockMvc.perform(delete(DELETE_BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }
}