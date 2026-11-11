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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that the config-driven email allowlist gates
 * the ticket resend endpoint at the HTTP layer. The allowlist is enabled for
 * this whole class via {@link TestPropertySource}, so only addresses at
 * {@code allowed.com} may request a ticket resend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.access.allowed-email-domains=allowed.com")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendeeControllerAllowlistTest
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

    private static final String RESENDS_PATH = ApiPaths.BASE + ApiPaths.Attendees.ROOT + ApiPaths.Attendees.RESENDS;
    private static final String RESEND_SUCCESS_MESSAGE =
        "If a registration exists for that email address, then a ticket has been sent to it.";
    private static final String ALLOWED_EMAIL = "me@allowed.com";
    private static final String DISALLOWED_EMAIL = "eve@other.com";
    private static final String TEST_PASSWORD = "securePassword123";

    /**
     * Ensure that each test starts from a fresh state so that tests which
     * create attendees, tickets, events, and event hosts do not interfere
     * with one another.
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
     * @return the persisted EventHost entity
     */
    private EventHost createVerifiedEventHost(String email)
    {
        EventHost host = new EventHost.Builder()
            .firstName("Attendee")
            .lastName("Tester")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .email(email)
            .plaintextPassword(TEST_PASSWORD)
            .build();
        host.generateVerificationToken();
        host.setVerified(true);
        return eventHostRepository.save(host);
    }

    /**
     * Creates and persists a published public Event with its EventAddress
     * and EventSigningKey.
     *
     * @param eventHost the event host who created the event
     * @return the persisted Event entity
     */
    private Event createEvent(EventHost eventHost)
    {
        Event event = new Event.Builder()
            .name("Test Event")
            .description("A test event description")
            .startDateTime(LocalDateTime.now().plusDays(1))
            .endDateTime(LocalDateTime.now().plusDays(1).plusHours(2))
            .maxAttendees(100L)
            .eventHost(eventHost)
            .eventType(EventType.PUBLIC)
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
     * Creates and persists an active Ticket for the given event, registering
     * the attendee identified by the given email address for that event.
     *
     * @param event the event the ticket belongs to
     * @param email the attendee's email address
     * @return the persisted Ticket entity
     */
    private Ticket createTicketForAttendee(Event event, String email)
    {
        Attendee attendee = createAttendee(email);
        Ticket ticket = ticketService.createSignedTicket(event);
        ticket.setAttendee(attendee);
        return ticketRepository.save(ticket);
    }

    /**
     * Builds a JSON request body for resending a ticket email.
     *
     * @param email the attendee's email address
     * @return a JSON string representing the resend request
     */
    private String buildResendRequestBody(String email)
    {
        return """
            {
                "email": "%s"
            }
            """.formatted(email);
    }

    @Test
    @DisplayName("Resend with an allowed email returns 202 and sends the ticket email")
    void resendWithAllowedEmailReturns202() throws Exception
    {
        EventHost host = createVerifiedEventHost("host@allowed.com");
        Event event = createEvent(host);
        Ticket ticket = createTicketForAttendee(event, ALLOWED_EMAIL);

        mockMvc.perform(post(RESENDS_PATH + "/" + event.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildResendRequestBody(ALLOWED_EMAIL)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RESEND_SUCCESS_MESSAGE));

        verify(emailService).sendTicketEmail
        (
            eq(ALLOWED_EMAIL),
            eq(ticket.getPublicToken()),
            eq(event.getName()),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            eq("Attendee Tester"),
            eq("Test City"),
            eq("Test State"),
            eq("Test Country"),
            eq("123 Test Street"),
            isNull(),
            eq("12345")
        );
    }

    @Test
    @DisplayName("Resend with a disallowed email returns 403")
    void resendWithDisallowedEmailReturns403() throws Exception
    {
        EventHost host = createVerifiedEventHost("host@allowed.com");
        Event event = createEvent(host);
        createTicketForAttendee(event, DISALLOWED_EMAIL);

        mockMvc.perform(post(RESENDS_PATH + "/" + event.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildResendRequestBody(DISALLOWED_EMAIL)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Resend with a disallowed email is rejected by the allowlist before the event is looked up")
    void resendWithDisallowedEmailAndUnknownEventReturns403() throws Exception
    {
        mockMvc.perform(post(RESENDS_PATH + "/" + UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildResendRequestBody(DISALLOWED_EMAIL)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Resend with an email at a subdomain of an allowed domain returns 202")
    void resendWithAllowedSubdomainEmailReturns202() throws Exception
    {
        String subdomainEmail = "me@mail.allowed.com";
        EventHost host = createVerifiedEventHost("host@allowed.com");
        Event event = createEvent(host);
        createTicketForAttendee(event, subdomainEmail);

        mockMvc.perform(post(RESENDS_PATH + "/" + event.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildResendRequestBody(subdomainEmail)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RESEND_SUCCESS_MESSAGE));

        verify(emailService).sendTicketEmail
        (
            eq(subdomainEmail),
            anyString(),
            eq(event.getName()),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            isNull(),
            anyString()
        );
    }
}
