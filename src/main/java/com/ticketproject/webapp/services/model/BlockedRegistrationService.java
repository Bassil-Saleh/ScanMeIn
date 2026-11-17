package com.ticketproject.webapp.services.model;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreateBlockedRegistrationRequest;
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

    public BlockedRegistrationService
    (
        BlockedRegistrationRepository blockedRegistrationRepository,
        EventRepository eventRepository,
        AttendeeRepository attendeeRepository,
        BlindIndexService blindIndexService
    )
    {
        this.blockedRegistrationRepository = blockedRegistrationRepository;
        this.eventRepository = eventRepository;
        this.attendeeRepository = attendeeRepository;
        this.blindIndexService = blindIndexService;
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
     * Handles a request to let a logged in event host block an attendee's registration
     * from a specific event identified by the event's public ID.
     * Only the event host who created the event should be allowed to do this.
     * @param eventHost the logged in event host
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse blockRegistration(EventHost eventHost, CreateBlockedRegistrationRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

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

        if (Long.compare(foundEvent.getEventHost().getId(), eventHost.getId()) != 0)
        {
            throw new InvalidCredentialsException("Only the event host who created the event can manage its registrations.");
        }

        byte[] emailBlindIndex = blindIndexService.computeIndex(request.email());

        Optional<Attendee> attendee = attendeeRepository.findByEmailIndex(emailBlindIndex);

        if (attendee.isEmpty())
        {
            throw new EntityNotFoundException("No existing registration record exists for this attendee.");
        }

        Attendee foundAttendee = attendee.get();

        Optional<BlockedRegistration> existingBlock =
            blockedRegistrationRepository.findByEventIdAndEmailBlindIndex(foundEvent.getId(), emailBlindIndex);
        
        if (!existingBlock.isEmpty())
        {
            return new SingleMessageResponse("This attendee's registration is already blocked from the event.");
        }

        BlockedRegistration newBlock = new BlockedRegistration.Builder()
            .attendee(foundAttendee)
            .event(foundEvent)
            .blockedBy(eventHost)
            .reason(request.reason())
            .build();
        
        newBlock = blockedRegistrationRepository.save(newBlock);

        // TODO: send an email to the attendee that their registration
        //       was blocked, and include the message from the event host
        //       explaining why the attendee's registration was blocked.

        return new SingleMessageResponse("The attendee's registration has been blocked.");
    }
}
