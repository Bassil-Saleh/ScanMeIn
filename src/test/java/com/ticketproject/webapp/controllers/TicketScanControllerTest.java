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
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TicketScanControllerTest contains integration tests for the
 * TicketScanController, covering ticket scanning and retrieval
 * of scanned tickets routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketScanControllerTest
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

    private static final String BASE_PATH = ApiPaths.BASE + ApiPaths.TicketScans.ROOT;
    private static final String TEST_EMAIL = "ticketscantest@example.com";
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
            .firstName("TicketScan")
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

    private Attendee createAttendee()
    {
        return createAttendee("Jon", "Smith", "jon@example.com");
    }

    /**
     * Creates and persists an Attendee with the given details.
     *
     * @param firstName the attendee's first name
     * @param lastName the attendee's last name
     * @param email the attendee's email address
     * @return the persisted Attendee entity
     */
    private Attendee createAttendee(String firstName, String lastName, String email)
    {
        Attendee attendee = new Attendee.Builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .build();

        return attendeeRepository.save(attendee);
    }

    /**
     * Creates and persists a Ticket for the given event.
     *
     * @param event the event the ticket belongs to
     * @return the persisted Ticket entity
     */
    private Ticket createTicket(Event event, Attendee attendee)
    {
        Ticket ticket = ticketService.createSignedTicket(event);
        ticket.setAttendee(attendee);
        return ticketRepository.save(ticket);
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
     * Scans a ticket through the POST /api/v1/ticket-scans API route.
     *
     * @param jwt the JWT of the event host performing the scan
     * @param ticket the ticket to scan
     * @throws Exception if the request fails
     */
    private void scanTicket(String jwt, Ticket ticket) throws Exception
    {
        String requestBody = """
            {
                "publicToken": "%s"
            }
            """.formatted(ticket.getPublicToken());

        mockMvc.perform(post(BASE_PATH)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("POST /api/v1/ticket-scans")
    class ScanTicketTests
    {
        @Test
        @DisplayName("Successful ticket scan returns 201")
        void successfulScanReturns201() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Your ticket has been scanned."));
        }

        @Test
        @DisplayName("Ticket scan without JWT returns 401")
        void scanWithoutJwtReturns401() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);

            String requestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());

            mockMvc.perform(post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Scanning non-existent ticket returns 404")
        void nonExistentTicketReturns404() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());
            
            // Once the event is deleted, the format of the ticket's
            // public token will still be syntactically valid, but it
            // will contain a token identifier to an event that no longer exists.
            eventRepository.delete(event);
            eventRepository.flush();

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Scanning ticket with incorrect public token format returns 400")
        void incorrectTicketReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicToken": "incorrect_token_format"
                }
                """;

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Scanning already scanned ticket returns 409")
        void alreadyScannedTicketReturns409() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());

            // Scan the ticket the first time.
            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());

            // Scan the same ticket again.
            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("Ticket scan with blank public token returns 400")
        void blankPublicTokenReturns400() throws Exception
        {
            createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            String requestBody = """
                {
                    "publicToken": ""
                }
                """;

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ticket-scans")
    class GetScannedTicketsTests
    {
        @Test
        @DisplayName("Successful retrieval of scanned tickets returns 200")
        void successfulRetrievalReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee();
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            // First scan a ticket.
            String scanRequestBody = """
                {
                    "publicToken": "%s"
                }
                """.formatted(ticket.getPublicToken());

            mockMvc.perform(post(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(scanRequestBody))
                .andExpect(status().isCreated());

            // Then retrieve the scanned tickets.
            mockMvc.perform(get(BASE_PATH)
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedTickets").isArray())
                .andExpect(jsonPath("$.scannedTickets.length()").value(1));
        }

        @Test
        @DisplayName("Retrieval of scanned tickets without JWT returns 401")
        void retrievalWithoutJwtReturns401() throws Exception
        {
            mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Retrieval of scanned tickets with invalid JWT returns 401")
        void retrievalWithInvalidJwtReturns401() throws Exception
        {
            mockMvc.perform(get(BASE_PATH)
                    .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ticket-scans/{publicId}")
    class GetScannedTicketsByEventPublicIdTests
    {
        @Test
        @DisplayName("Successful retrieval of tickets scanned for an event returns 200")
        void successfulRetrievalReturns200() throws Exception
        {
            EventHost host = createVerifiedEventHost(TEST_EMAIL, TEST_PASSWORD);
            Event event = createEvent(host, EventType.PUBLIC);
            Attendee attendee = createAttendee("Jon", "Smith", "jon@example.com");
            attendee.setMiddleName("Marie");
            attendeeRepository.save(attendee);
            Ticket ticket = createTicket(event, attendee);
            String jwt = loginAndGetJwt(TEST_EMAIL, TEST_PASSWORD);

            // First scan the ticket.
            scanTicket(jwt, ticket);

            // Then retrieve the tickets scanned for the event.
            mockMvc.perform(get(BASE_PATH + "/" + event.getPublicId())
                    .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedTickets").isArray())
                .andExpect(jsonPath("$.scannedTickets.length()").value(1))
                .andExpect(jsonPath("$.scannedTickets[0].attendeeFirstName").value("Jon"))
                .andExpect(jsonPath("$.scannedTickets[0].attendeeMiddleName").value("Marie"))
                .andExpect(jsonPath("$.scannedTickets[0].attendeeLastName").value("Smith"))
                .andExpect(jsonPath("$.scannedTickets[0].attendeeEmail").value("jon@example.com"))
                .andExpect(jsonPath("$.scannedTickets[0].eventName").value("Test Event"))
                .andExpect(jsonPath("$.scannedTickets[0].eventDescription").value("A test event description"))
                .andExpect(jsonPath("$.scannedTickets[0].scannedAt").exists())
                .andExpect(jsonPath("$.scannedTickets[0].eventStartDateTime").exists())
                .andExpect(jsonPath("$.scannedTickets[0].eventEndDateTime").exists());
        }
    }
}