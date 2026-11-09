package com.ticketproject.webapp.services.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.ScanTicketRequest;
import com.ticketproject.webapp.dtos.responses.GetScannedTicketsByEventHostResponse;
import com.ticketproject.webapp.dtos.responses.ScannedTicketInfo;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.EventEndedException;
import com.ticketproject.webapp.exceptions.InvalidRequestException;
import com.ticketproject.webapp.exceptions.SigningKeyNotFoundException;
import com.ticketproject.webapp.exceptions.TicketAlreadyScannedException;
import com.ticketproject.webapp.exceptions.TicketScanFailedException;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.Ticket;
import com.ticketproject.webapp.model.entities.TicketScan;
import com.ticketproject.webapp.model.repositories.TicketScanRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class TicketScanService
{
    private final TicketScanRepository ticketScanRepository;
    private final EventRepository eventRepository;
    private final TicketService ticketService;

    public TicketScanService
    (
        TicketScanRepository ticketScanRepository,
        EventRepository eventRepository,
        TicketService ticketService
    )
    {
        this.ticketScanRepository = ticketScanRepository;
        this.eventRepository = eventRepository;
        this.ticketService = ticketService;
    }

    /**
     * Services a request to let a logged in event host scan an attendee's ticket.
     * @param eventHost the logged in event host
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse scanTicket(EventHost eventHost, ScanTicketRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        Ticket foundTicket;
        try
        {
            foundTicket = ticketService.validateTokenIdentifier(request.publicToken());
        }
        catch
        (
            InvalidRequestException |
            EntityNotFoundException |
            TicketAlreadyScannedException |
            EventEndedException |
            SigningKeyNotFoundException |
            TicketScanFailedException e
        )
        {
            throw e;
        }

        LocalDateTime scannedAt = LocalDateTime.now();
        foundTicket.setPresent(true);

        TicketScan ticketScan = new TicketScan.Builder()
            .ticket(foundTicket)
            .scannedAt(scannedAt)
            .scannedBy(eventHost)
            .build();
        
        ticketScanRepository.save(ticketScan);

        return new SingleMessageResponse("Your ticket has been scanned.");
    }

    /**
     * Services a request to let a logged in event host
     * retrieve info on a list of tickets they've scanned.
     * @param eventHost the logged in event host
     * @return a GetScannedTicketsByEventHostResponse on success
     */
    public GetScannedTicketsByEventHostResponse getScannedTicketsByEventHost(EventHost eventHost)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        List<TicketScan> scannedTickets = ticketScanRepository.findAllByEventHostId(eventHost.getId());

        List<ScannedTicketInfo> scannedTicketInfo = scannedTickets
            .stream()
            .map
            (ticket ->
            {
                ScannedTicketInfo record = new ScannedTicketInfo
                (
                    ticket.getScannedAt(),
                    ticket.getTicket().getAttendee().getFirstName(),
                    ticket.getTicket().getAttendee().getMiddleName(),
                    ticket.getTicket().getAttendee().getLastName(),
                    ticket.getTicket().getAttendee().getEmail(),
                    ticket.getTicket().getEvent().getName(),
                    ticket.getTicket().getEvent().getDescription(),
                    ticket.getTicket().getEvent().getStartDateTime(),
                    ticket.getTicket().getEvent().getEndDateTime()
                );
                return record;
            })
            .toList();

        return new GetScannedTicketsByEventHostResponse(scannedTicketInfo);
    }

    /**
     * Services a request to let a logged in event host
     * retrieve info on a list of tickets scanned
     * for a specific event (identified by the event's public ID).
     * @param eventHost the logged in event host
     * @param publicId the event's public ID
     * @return a GetScannedTicketsByEventHostResponse on success
     */
    public GetScannedTicketsByEventHostResponse getScannedTicketsByEventPublicId(EventHost eventHost, String publicId)
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

        List<TicketScan> scannedTickets = ticketScanRepository.findAllByEventPublicId(foundEvent.getPublicId());
        List<ScannedTicketInfo> scannedTicketInfo = scannedTickets
            .stream()
            .map
            (ticket ->
            {
                ScannedTicketInfo record = new ScannedTicketInfo
                (
                    ticket.getScannedAt(),
                    ticket.getTicket().getAttendee().getFirstName(),
                    ticket.getTicket().getAttendee().getMiddleName(),
                    ticket.getTicket().getAttendee().getLastName(),
                    ticket.getTicket().getAttendee().getEmail(),
                    ticket.getTicket().getEvent().getName(),
                    ticket.getTicket().getEvent().getDescription(),
                    ticket.getTicket().getEvent().getStartDateTime(),
                    ticket.getTicket().getEvent().getEndDateTime()
                );
                return record;
            })
            .toList();

        return new GetScannedTicketsByEventHostResponse(scannedTicketInfo);
    }
}
