package com.ticketproject.webapp.services.model;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreatePrivateEventInvitationRequest;
import com.ticketproject.webapp.dtos.requests.CreatePublicEventRegistrationRequest;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.EmailAlreadyExistsException;
import com.ticketproject.webapp.exceptions.EventAlreadyCanceledException;
import com.ticketproject.webapp.exceptions.EventRegistrationException;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.exceptions.InvalidRequestException;
import com.ticketproject.webapp.exceptions.InvalidCredentialsException;
import com.ticketproject.webapp.model.entities.Attendee;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventAddress;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.Ticket;
import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.model.enums.EventStatus;
import com.ticketproject.webapp.model.enums.RegistrationStatus;
import com.ticketproject.webapp.model.repositories.AttendeeRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.model.repositories.TicketRepository;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.email.EmailService;
import com.ticketproject.webapp.services.access.AccessControlService;

import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AttendeeService is a service used by controllers to handle
 * API route requests involving Attendee entities
 * (i.e. public event registrations, private event invitations).
 */
@Service
@Transactional
public class AttendeeService
{
    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final BlindIndexService blindIndexService;
    private final EmailService emailService;
    private final AccessControlService accessControlService;

    public AttendeeService
    (
        AttendeeRepository attendeeRepository,
        EventRepository eventRepository,
        TicketRepository ticketRepository,
        TicketService ticketService,
        BlindIndexService blindIndexService,
        EmailService emailService,
        AccessControlService accessControlService
    )
    {
        this.attendeeRepository = attendeeRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.blindIndexService = blindIndexService;
        this.emailService = emailService;
        this.accessControlService = accessControlService;
    }

    /**
     * Services a request to let an attendee register for a public event.
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse createPublicEventRegistration
    (
        CreatePublicEventRegistrationRequest request
    )
    {
        // Enforce the email allowlist for the registering attendee.
        accessControlService.requireEmailAllowed(request.email());

        Optional<Event> event = eventRepository.findByPublicId(request.publicId());

        if (event.isEmpty())
        {
            throw new EntityNotFoundException("Could not find an event with the provided public id.");
        }

        Event foundEvent = event.get();

        if (foundEvent.getEventType() != EventType.PUBLIC)
        {
            throw new EventRegistrationException("This is not a public event.");
        }

        byte[] emailBlindIndex = blindIndexService.computeIndex(request.email());

        if (ticketRepository.attendeeRegistrationExistsByEventId(emailBlindIndex, foundEvent.getId()))
        {
            throw new EmailAlreadyExistsException("A registration with the same email address already exists for this event.");
        }

        if (foundEvent.getRegistrationStatus() == RegistrationStatus.CLOSED)
        {
            throw new EventRegistrationException("The registration period for this event is already closed.");
        }

        if (foundEvent.getEventStatus() == EventStatus.CANCELED)
        {
            throw new EventAlreadyCanceledException("Cannot register for the event because it is already canceled.");
        }

        if (foundEvent.getMaxAttendees() != null && ticketRepository.getRegistrationCountByEventId(foundEvent.getId()) >= foundEvent.getMaxAttendees())
        {
            throw new EventRegistrationException("The event is currently at maximum capacity, so no further registrations are possible.");
        }

        if (foundEvent.getStartDateTime().isBefore(LocalDateTime.now()))
        {
            throw new EventRegistrationException("Cannot register after the event has already begun.");
        }

        Ticket signedTicket = ticketService.createSignedTicket(foundEvent);

        // If the attendee isn't already in the database,
        // create a new Attendee record for them,
        // otherwise use a pre-existing Attendee record.
        Optional<Attendee> attendee = attendeeRepository.findByEmailIndex(emailBlindIndex);
        if (attendee.isEmpty())
        {
            Attendee newAttendee = new Attendee.Builder()
                .firstName(request.firstName())
                .middleName(request.middleName())
                .lastName(request.lastName())
                .email(request.email())
                .build();
            
            signedTicket.setEvent(foundEvent);
            signedTicket.setAttendee(newAttendee);

            newAttendee = attendeeRepository.save(newAttendee);
            signedTicket = ticketRepository.save(signedTicket);
        }
        else
        {
            Attendee foundAttendee = attendee.get();

            signedTicket.setEvent(foundEvent);
            signedTicket.setAttendee(foundAttendee);

            foundAttendee = attendeeRepository.save(foundAttendee);
            signedTicket = ticketRepository.save(signedTicket);
        }

        // Send the ticket email with a QR code to the attendee.
        // If the email fails to send, the @Transactional annotation
        // will roll back the entire transaction (no DB persistence).
        EventHost eventHost = foundEvent.getEventHost();
        String eventHostName = eventHost.getFirstName() + " " + eventHost.getLastName();
        EventAddress eventAddress = foundEvent.getEventAddress();

        emailService.sendTicketEmail
        (
            request.email(),
            signedTicket.getPublicToken(),
            foundEvent.getName(),
            foundEvent.getStartDateTime(),
            foundEvent.getEndDateTime(),
            eventHostName,
            eventAddress.getCity(),
            eventAddress.getState(),
            eventAddress.getCountry(),
            eventAddress.getAddressLine1(),
            eventAddress.getAddressLine2(),
            eventAddress.getPostalCode()
        );

        return new SingleMessageResponse
        ("Registration completed. A ticket with a QR code has been sent to your email.");
    }

    /**
     * Services a request to let a logged in event host create an invitation
     * to a private event. Only the event host who created the event should
     * be able to create invitations for the event.
     * @param eventHost the logged in event host
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse createPrivateEventInvitation(EventHost eventHost, CreatePrivateEventInvitationRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        // Enforce the email allowlist for the invited attendee.
        accessControlService.requireEmailAllowed(request.email());

        if (request.publicId() == null)
        {
            throw new InvalidRequestException("Event public id cannot be null.");
        }

        if (request.publicId().length() > AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH)
        {
            throw new InvalidRequestException
            (
                "Event public id cannot be longer than " +
                AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH +
                " characters."
            );
        }

        Optional<Event> event = eventRepository.findByPublicId(request.publicId());
        if (event.isEmpty())
        {
            throw new EntityNotFoundException("Could not find an event with the provided public id.");
        }

        Event foundEvent = event.get();

        if (foundEvent.getEventType() != EventType.PRIVATE)
        {
            throw new EventRegistrationException("This is not a private event.");
        }

        byte[] emailBlindIndex = blindIndexService.computeIndex(request.email());

        if (ticketRepository.attendeeRegistrationExistsByEventId(emailBlindIndex, foundEvent.getId()))
        {
            throw new EmailAlreadyExistsException("An invitation designated to the same email address already exists for this event.");
        }

        if (Long.compare(foundEvent.getEventHost().getId(), eventHost.getId()) != 0)
        {
            throw new InvalidCredentialsException("Only the event host who created the event can create invitations for it.");
        }

        if (foundEvent.getEventStatus() == EventStatus.CANCELED)
        {
            throw new EventAlreadyCanceledException("Cannot create new invitations because the event is already canceled.");
        }

        if (foundEvent.getStartDateTime().isBefore(LocalDateTime.now()))
        {
            throw new EventRegistrationException("Cannot create new invitations after the event has already begun.");
        }

        Ticket signedTicket = ticketService.createSignedTicket(foundEvent);

        // If the attendee isn't already in the database,
        // create a new Attendee record for them,
        // otherwise use a pre-existing Attendee record.
        Optional<Attendee> attendee = attendeeRepository.findByEmailIndex(emailBlindIndex);
        if (attendee.isEmpty())
        {
            Attendee newAttendee = new Attendee.Builder()
                .firstName(request.firstName())
                .middleName(request.middleName())
                .lastName(request.lastName())
                .email(request.email())
                .build();
            
            signedTicket.setEvent(foundEvent);
            signedTicket.setAttendee(newAttendee);

            newAttendee = attendeeRepository.save(newAttendee);
            signedTicket = ticketRepository.save(signedTicket);
        }
        else
        {
            Attendee foundAttendee = attendee.get();

            signedTicket.setEvent(foundEvent);
            signedTicket.setAttendee(foundAttendee);

            foundAttendee = attendeeRepository.save(foundAttendee);
            signedTicket = ticketRepository.save(signedTicket);
        }

        // Send the ticket email with a QR code to the attendee.
        // If the email fails to send, the @Transactional annotation
        // will roll back the entire transaction (no DB persistence).
        String eventHostName = eventHost.getFirstName() + " " + eventHost.getLastName();
        EventAddress eventAddress = foundEvent.getEventAddress();

        emailService.sendInvitationEmail
        (
            request.email(),
            signedTicket.getPublicToken(),
            foundEvent.getName(),
            foundEvent.getStartDateTime(),
            foundEvent.getEndDateTime(),
            eventHostName,
            eventAddress.getCity(),
            eventAddress.getState(),
            eventAddress.getCountry(),
            eventAddress.getAddressLine1(),
            eventAddress.getAddressLine2(),
            eventAddress.getPostalCode()
        );

        return new SingleMessageResponse
        ("Invitation created. A ticket with a QR code has been sent to your recipient's email.");
    }
}
