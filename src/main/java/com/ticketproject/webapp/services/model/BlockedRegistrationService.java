package com.ticketproject.webapp.services.model;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreateBlockedRegistrationsRequest;
import com.ticketproject.webapp.dtos.responses.BlockedRegistrationInfo;
import com.ticketproject.webapp.dtos.responses.GetBlockedRegistrationsByEventPublicIdResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.model.entities.Attendee;
import com.ticketproject.webapp.model.entities.BlockedRegistration;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.repositories.AttendeeRepository;
import com.ticketproject.webapp.model.repositories.BlockedRegistrationRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.email.EmailService;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.exceptions.InvalidRequestException;
import com.ticketproject.webapp.exceptions.InvalidCredentialsException;

import jakarta.persistence.EntityNotFoundException;

import java.util.Optional;
import java.util.List;

/**
 * BlockedRegistrationService is a service used by controllers to handle
 * API route requests involving BlockedRegistration entities
 * (used to block attendees from specific events).
 */
@Service
@Transactional
public class BlockedRegistrationService
{
    // TODO: implement logic for a GET route to retrieve
    //       blocked registrations by event public ID.

    // TODO: implement logic for a POST route to block
    //       an attendee's registration to an event
    //       identified by the event's public ID.

    private final BlockedRegistrationRepository blockedRegistrationRepository;
    private final EventRepository eventRepository;
    private final AttendeeRepository attendeeRepository;
    private final BlindIndexService blindIndexService;
    private final EmailService emailService;

    public BlockedRegistrationService
    (
        BlockedRegistrationRepository blockedRegistrationRepository,
        EventRepository eventRepository,
        AttendeeRepository attendeeRepository,
        BlindIndexService blindIndexService,
        EmailService emailService
    )
    {
        this.blockedRegistrationRepository = blockedRegistrationRepository;
        this.eventRepository = eventRepository;
        this.attendeeRepository = attendeeRepository;
        this.blindIndexService = blindIndexService;
        this.emailService = emailService;
    }

    /**
     * Services a request to fetch a list of records on blocked registrations
     * for an event identified by its public ID. Only the event host who
     * created the event should be allowed to view and manage
     * the event's blocked registrations.
     * @param eventHost the logged in event host
     * @param publicId the event's public ID
     * @return a GetBlockedRegistrationsByEventPublicIdResponse on success
     */
    public GetBlockedRegistrationsByEventPublicIdResponse getBlockedRegistrationsByEventPublicId
    (EventHost eventHost, String publicId)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        if (publicId == null)
        {
            throw new InvalidRequestException("Event public id cannot be null.");
        }

        if (publicId.length() > AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH)
        {
            throw new InvalidRequestException
            (
                "Event public id cannot be longer than " +
                AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH +
                " characters."
            );
        }

        Optional<Event> event = eventRepository.findByPublicId(publicId);
        if (event.isEmpty())
        {
            throw new EntityNotFoundException("Could not find an event with the provided public id.");
        }

        Event foundEvent = event.get();

        if (Long.compare(foundEvent.getEventHost().getId(), eventHost.getId()) != 0)
        {
            throw new InvalidCredentialsException("Only the event host who created the event can manage its registrations.");
        }

        List<BlockedRegistration> foundBlocks = blockedRegistrationRepository.findAllByEventId(foundEvent.getId());
        List<BlockedRegistrationInfo> blockRecords = foundBlocks
            .stream()
            .map(block ->
            {
                BlockedRegistrationInfo record =
                    new BlockedRegistrationInfo
                    (
                        block.getAttendee().getFirstName(),
                        block.getAttendee().getMiddleName(),
                        block.getAttendee().getLastName(),
                        block.getAttendee().getEmail(),
                        block.getBlockedBy().getEmail(),
                        block.getReason(),
                        block.getCreated(),
                        block.getRevoked()
                    );
                return record;
            })
            .toList();
        
        return new GetBlockedRegistrationsByEventPublicIdResponse(blockRecords);
    }

    /**
     * Handles a request to let a logged in event host block multiple attendee registrations
     * from a specific event identified by the event's public ID.
     * Only the event host who created the event should be allowed to do this.
     * @param eventHost the logged in event host
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse blockRegistrations
    (EventHost eventHost, CreateBlockedRegistrationsRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        if (request.publicId() == null)
        {
            throw new InvalidRequestException("Event public id cannot be null.");
        }

        if (request.emails() == null)
        {
            throw new InvalidRequestException("List of emails cannot be null.");
        }

        if (request.emails().size() < 1)
        {
            throw new InvalidRequestException("List of emails cannot be empty.");
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

        if (Long.compare(foundEvent.getEventHost().getId(), eventHost.getId()) != 0)
        {
            throw new InvalidCredentialsException("Only the event host who created the event can manage its registrations.");
        }

        List<byte[]> emailBlindIndexes = request.emails()
            .stream()
            .map(email ->
            {
                byte[] emailBlindIndex = blindIndexService.computeIndex(email);
                return emailBlindIndex;
            })
            .toList();

        List<Attendee> attendees = attendeeRepository.findAllByEmailIndexes(emailBlindIndexes);

        int numOfBlockedRegistrations = 0;
        for (Attendee anAttendee : attendees)
        {
            Optional<BlockedRegistration> existingBlock = blockedRegistrationRepository
                .findByEventIdAndEmailBlindIndex(foundEvent.getId(), anAttendee.getEmailBlindIndex());

            if (existingBlock.isPresent())
                continue;

            ++numOfBlockedRegistrations;

            BlockedRegistration newBlock = new BlockedRegistration.Builder()
                .attendee(anAttendee)
                .event(foundEvent)
                .blockedBy(eventHost)
                .build();
            newBlock = blockedRegistrationRepository.save(newBlock);

            emailService.sendEventRegistrationBlockedEmail
            (anAttendee.getEmail(), foundEvent.getName(), null);
        }
        
        return new SingleMessageResponse("Blocked " + numOfBlockedRegistrations + " registrations.");
    }
}
