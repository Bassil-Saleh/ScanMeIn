package com.ticketproject.webapp.services.model;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.responses.BlockedRegistrationInfo;
import com.ticketproject.webapp.dtos.responses.GetBlockedRegistrationsByEventPublicIdResponse;
import com.ticketproject.webapp.model.entities.BlockedRegistration;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.repositories.BlockedRegistrationRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
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

    public BlockedRegistrationService
    (
        BlockedRegistrationRepository blockedRegistrationRepository,
        EventRepository eventRepository
    )
    {
        this.blockedRegistrationRepository = blockedRegistrationRepository;
        this.eventRepository = eventRepository;
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
}
