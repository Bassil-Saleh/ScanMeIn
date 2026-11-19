package com.ticketproject.webapp.services.model;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.DeleteTicketsRequest;
import com.ticketproject.webapp.dtos.requests.RespondToInvitationRequest;
import com.ticketproject.webapp.dtos.responses.EventTicketInfo;
import com.ticketproject.webapp.dtos.responses.GetTicketsByEventPublicIdResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.TicketGenerationException;
import com.ticketproject.webapp.exceptions.InvalidRequestException;
import com.ticketproject.webapp.exceptions.TicketAlreadyScannedException;
import com.ticketproject.webapp.exceptions.TicketScanFailedException;
import com.ticketproject.webapp.exceptions.AttendeeBlockedException;
import com.ticketproject.webapp.exceptions.EventEndedException;
import com.ticketproject.webapp.exceptions.SigningKeyNotFoundException;
import com.ticketproject.webapp.exceptions.InvalidSignatureException;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.exceptions.InvalidCredentialsException;
import com.ticketproject.webapp.model.entities.BlockedRegistration;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.EventSigningKey;
import com.ticketproject.webapp.model.entities.Ticket;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.model.enums.InvitationStatus;
import com.ticketproject.webapp.model.repositories.BlockedRegistrationRepository;
import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.model.repositories.EventSigningKeyRepository;
import com.ticketproject.webapp.model.repositories.TicketRepository;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.email.EmailService;

/**
 * TicketService is used to generate Ticket entities for attendees.
 */
@Service
@Transactional
public class TicketService
{
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final EventSigningKeyRepository eventSigningKeyRepository;
    private final BlockedRegistrationRepository blockedRegistrationRepository;
    private final EmailService emailService;
    private final BlindIndexService blindIndexService;

    public TicketService
    (
        TicketRepository ticketRepository,
        EventRepository eventRepository,
        EventSigningKeyRepository eventSigningKeyRepository,
        BlockedRegistrationRepository blockedRegistrationRepository,
        EmailService emailService,
        BlindIndexService blindIndexService
    )
    {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.eventSigningKeyRepository = eventSigningKeyRepository;
        this.blockedRegistrationRepository = blockedRegistrationRepository;
        this.emailService = emailService;
        this.blindIndexService = blindIndexService;
    }

    /**
     * Create a Ticket with a public token generated using
     * the EventSigningKey of the supplied Event.
     * 
     * @return a Ticket entity
     */
    public Ticket createSignedTicket(Event event)
    {
        try
        {
            String tokenIdentifier = UUID.randomUUID().toString();
            LocalDateTime created = LocalDateTime.now();
            String payload =
                tokenIdentifier +
                AppConstants.Crypto.TICKET_PAYLOAD_DECODED_SEPARATOR +
                created.toString();

            Signature signatureBuilder = Signature
                .getInstance(AppConstants.Crypto.TICKET_SIGNATURE_ALGORITHM);
            signatureBuilder.initSign(event.getSigningKey().getPrivateKey());
            signatureBuilder.update(payload.getBytes(StandardCharsets.UTF_8));

            byte[] signature = signatureBuilder.sign();

            String publicToken =
                Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) +
                AppConstants.Crypto.TICKET_PAYLOAD_ENCODED_SEPARATOR +
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
            Ticket signedTicket = new Ticket.Builder()
                .publicToken(publicToken)
                .tokenIdentifier(tokenIdentifier)
                .event(event)
                .invitationStatus(InvitationStatus.PENDING)
                .build();
            
            signedTicket.setPresent(false);
            return signedTicket;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new TicketGenerationException("Cannot generate ticket - signature algorithm not supported");
        }
        catch (InvalidKeyException e)
        {
            throw new TicketGenerationException("Cannot generate ticket - invalid private key");
        }
        catch (SignatureException e)
        {
            throw new TicketGenerationException("Cannot generate ticket - signature generator not initialized properly");
        }
    }

    /**
     * Used to check that a given public token from a digital ticket
     * has a structurally valid payload and contains a valid signature.
     * @param publicToken the public token
     * @returns the Ticket entity associated with the public token
     * @throws InvalidRequestException if the public token is not in
     * a valid format
     * @throws EntityNotFoundException if the public token is not associated
     * with any existing event
     * @throws TicketAlreadyScannedException if the public token has already
     * been used to scan an attendee into an event
     * @throws EventEndedException if the public token is for an event that
     * has already ended
     * @throws SigningKeyNotFoundException if the signing key for the
     * related event could not be found
     * @throws TicketScanFailedException if the signature could not be verified
     * @throws InvalidSignatureException if the signature is either invalid or forged
     */
    public Ticket validateTokenIdentifier(String publicToken)
    throws InvalidRequestException, EntityNotFoundException,
    TicketAlreadyScannedException, EventEndedException,
    SigningKeyNotFoundException, TicketScanFailedException,
    InvalidSignatureException
    {
        String[] tokenParts = publicToken.split(AppConstants.Crypto.TICKET_PAYLOAD_ENCODED_SEPARATOR_REGEX);
        if (tokenParts.length != 2)
        {
            throw new InvalidRequestException("Scanned ticket is in an invalid format.");
        }

        byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[0]);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(tokenParts[1]);

        String payload = new String(payloadBytes);
        String tokenIdentifier = payload
            .split(AppConstants.Crypto.TICKET_PAYLOAD_DECODED_SEPARATOR_REGEX)[0];
        
        Optional<Ticket> ticket = ticketRepository.findByTokenIdentifier(tokenIdentifier);

        if (ticket.isEmpty())
        {
            throw new EntityNotFoundException("The scanned ticket is not associated with any existing event.");
        }

        Ticket foundTicket = ticket.get();

        if (foundTicket.isPresent())
        {
            throw new TicketAlreadyScannedException("The ticket has already been scanned.");
        }

        Event foundEvent = foundTicket.getEvent();

        if (foundEvent.getEndDateTime().isBefore(LocalDateTime.now()))
        {
            throw new EventEndedException("The event which this ticket is for has already ended.");
        }

        Optional<EventSigningKey> signingKeys = eventSigningKeyRepository.findByEventId(foundEvent.getId());
        if (signingKeys.isEmpty())
        {
            throw new SigningKeyNotFoundException("Could not verify scanned ticket. Please try again later.");
        }

        EventSigningKey foundSigningKeys = signingKeys.get();

        boolean isValidSignature = false;
        try
        {
            Signature signatureInstance = Signature.getInstance(AppConstants.Crypto.TICKET_SIGNATURE_ALGORITHM);
            signatureInstance.initVerify(foundSigningKeys.getPublicKey());
            signatureInstance.update(payloadBytes);
            isValidSignature = signatureInstance.verify(signatureBytes);
        }
        catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e)
        {
            throw new TicketScanFailedException("Could not scan ticket.");
        }
        if (!isValidSignature)
        {
            throw new InvalidSignatureException("Signature of scanned ticket is either invalid or forged.");
        }

        return foundTicket;
    }

    /**
     * Services a request to respond to an invitation to a private event.
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse respondToInvitation(RespondToInvitationRequest request)
    {
        Ticket foundTicket;
        try
        {
            foundTicket = validateTokenIdentifier(request.publicToken());
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

        // If the attendee is already blocked from the private event,
        // then it doesn't make sense to let them respond to the invitation
        // because the TicketScanService won't accept their ticket anyway.
        Optional<BlockedRegistration> existingBlock =
            blockedRegistrationRepository.findByEventIdAndEmailBlindIndex
            (foundTicket.getEvent().getId(), foundTicket.getAttendee().getEmailBlindIndex());

        if (existingBlock.isPresent())
        {
            throw new AttendeeBlockedException
            ("Cannot respond to an invitation when you are already blocked from the event.");
        }

        InvitationStatus previousInvitationStatus = foundTicket.getInvitationStatus();

        // Responding to an invitation only makes sense if
        // the ticket is for a private event, not a public event.
        if (foundTicket.getEvent().getEventType() != EventType.PRIVATE)
        {
            throw new InvalidRequestException("This event is not a private event.");
        }

        // I don't think it makes sense to change an invitation status back into PENDING,
        // since that's the state that invitations are initialized to.
        if (request.invitationResponse() == InvitationStatus.PENDING)
        {
            throw new InvalidRequestException("Cannot respond to an invitation with PENDING.");
        }

        if
        (
            previousInvitationStatus != request.invitationResponse() ||
            (request.message() != null && !request.message().isBlank())
        )
        {
            // Send an email to the event host notifying them of the invitee's response.
            String inviteeName = foundTicket.getAttendee().getFirstName();
            if (foundTicket.getAttendee().getMiddleName() != null && !foundTicket.getAttendee().getMiddleName().isBlank())
            {
                inviteeName = inviteeName + " " + foundTicket.getAttendee().getMiddleName();
            }
            inviteeName = inviteeName + " " + foundTicket.getAttendee().getLastName();

            emailService.sendInvitationResponseEmail
            (
                foundTicket.getEvent().getEventHost().getEmail(),
                foundTicket.getEvent().getName(),
                inviteeName,
                (request.invitationResponse() == InvitationStatus.ACCEPTED) ? "accepted" : "rejected",
                (request.message() != null) ? request.message() : ""
            );
        }

        foundTicket.setInvitationStatus(request.invitationResponse());
        foundTicket.setLastUpdated(LocalDateTime.now());

        foundTicket = ticketRepository.save(foundTicket);

        return new SingleMessageResponse("Responded to invitation with response: " + request.invitationResponse());
    }

    /**
     * Services a request to let a logged in event host retrieve
     * a list of records on tickets for a specific event.
     * Only the event host who created the event should be
     * allowed to manage those records.
     * @param eventHost the logged in event host
     * @param publicId the event's public ID
     * @return a GetTicketsByEventPublicIdResponse on success
     */
    public GetTicketsByEventPublicIdResponse getTicketsByEventPublicId(EventHost eventHost, String publicId)
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

        List<Ticket> foundTickets = ticketRepository.findAllActiveTicketsByEventId(foundEvent.getId());
        List<EventTicketInfo> ticketRecords = foundTickets
            .stream()
            .map(ticket ->
            {
                EventTicketInfo record =
                    new EventTicketInfo
                    (
                        ticket.getAttendee().getFirstName(),
                        ticket.getAttendee().getMiddleName(),
                        ticket.getAttendee().getLastName(),
                        ticket.getAttendee().getEmail(),
                        ticket.isPresent(),
                        ticket.getInvitationStatus(),
                        ticket.getCreated(),
                        ticket.getDeletedAt(),
                        ticket.getLastUpdated()
                    );
                return record;
            })
            .toList();

        return new GetTicketsByEventPublicIdResponse(ticketRecords);
    }

    /**
     * Handles a request to let a logged in event host delete
     * a list of tickets for a specific event (identified by a
     * list of email addresses which the tickets are designated for).
     * Only the event host who created the event should be allowed
     * to delete those tickets.
     * @param eventHost the logged in event host
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    public SingleMessageResponse deleteTickets(EventHost eventHost, DeleteTicketsRequest request)
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

        if (request.emails().isEmpty())
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
            throw new InvalidCredentialsException("Only the event host who created the event can delete its tickets.");
        }

        List<byte[]> emailBlindIndexes = request
            .emails()
            .stream()
            .map(email ->
            {
                byte[] blindIndex = blindIndexService.computeIndex(email);
                return blindIndex;
            })
            .toList();
        List<Ticket> foundTickets = ticketRepository
            .findAllActiveTicketsByEmailBlindIndex(emailBlindIndexes, foundEvent.getId());

        if (foundTickets.isEmpty())
        {
            throw new EntityNotFoundException("Could not find any active tickets using the provided emails and event public ID.");
        }

        List<Long> foundTicketIds = foundTickets
            .stream()
            .map(ticket -> ticket.getId())
            .toList();
        ticketRepository.deleteAllById(foundTicketIds);

        // TODO: Send an email to each person notifying them
        // that their ticket for the event was deleted.

        return new SingleMessageResponse
        ("Deleted " + foundTicketIds.size() + " ticket" + (foundTicketIds.size() > 1 ? "s" : "") + ".");
    }
}