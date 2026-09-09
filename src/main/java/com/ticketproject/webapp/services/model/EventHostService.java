package com.ticketproject.webapp.services.model;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreateEventHostRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostEmailRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostNameRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostPasswordRequest;
import com.ticketproject.webapp.dtos.responses.GetEventHostProfileResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.EmailAlreadyExistsException;
import com.ticketproject.webapp.exceptions.EventHostToVerifyNotFoundException;
import com.ticketproject.webapp.exceptions.EventHostUnderageException;
import com.ticketproject.webapp.exceptions.EventHostVerificationPeriodExpiredException;
import com.ticketproject.webapp.exceptions.PendingEventsExistException;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.exceptions.EventHostInactiveException;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.repositories.EventHostRepository;

import com.ticketproject.webapp.model.repositories.EventRepository;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.database.HashingService;
import com.ticketproject.webapp.services.email.EmailService;
import com.ticketproject.webapp.services.access.AccessControlService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventHostService is a service used by controllers to handle API route requests
 * involving EventHost entities (i.e. new account creation).
 */
@Service
@Transactional
public class EventHostService
{
    private final EventRepository eventRepository;
    private final EventHostRepository eventHostRepository;
    private final BlindIndexService blindIndexService;
    private final HashingService hashingService;
    private final EmailService emailService;
    private final AccessControlService accessControlService;

    public EventHostService
    (
        EventHostRepository eventHostRepository,
        BlindIndexService blindIndexService,
        HashingService hashingService,
        EventRepository eventRepository,
        EmailService emailService,
        AccessControlService accessControlService
    )
    {
        this.eventHostRepository = eventHostRepository;
        this.blindIndexService = blindIndexService;
        this.hashingService = hashingService;
        this.eventRepository = eventRepository;
        this.emailService = emailService;
        this.accessControlService = accessControlService;
    }

    /**
     * Service a request to create a new event host account.
     * @param request request containing info to create a new event host account
     * @return a SingleMessageResponse on success
     * @throws EmailAlreadyExistsException if the request contains
     * an email address already used by a preexisting event host account
     * @throws EventHostUnderageException if the request contains a date of birth
     * less than 18 years old
     */
    public SingleMessageResponse createEventHost(CreateEventHostRequest request)
    {
        // Enforce the email allowlist before doing any other work.
        accessControlService.requireEmailAllowed(request.email());

        // Check if there already exists an event host account
        // using that same email address.
        byte[] emailBlinxIndex = blindIndexService.computeIndex(request.email());
        if (eventHostRepository.existsByEmailIndex(emailBlinxIndex))
        {
            throw new EmailAlreadyExistsException("An account with the provided email address already exists");
        }

        // Check that the user is at least 18 years old before
        // creating a new event host account.
        LocalDate today = LocalDate.now();
        long yearsOld = ChronoUnit.YEARS.between(request.dateOfBirth(), today);
        if (yearsOld < AppConstants.DTO.EventHosts.Sizes.MIN_AGE_YEARS)
        {
            throw new EventHostUnderageException
            ("Must be at least " + AppConstants.DTO.EventHosts.Sizes.MIN_AGE_YEARS + " years old to create a new account");
        }

        // Create a new EventHost entity.
        // The EventHost constructor should already handle
        // encryption and hashing of sensitive fields.
        EventHost newEventHost = new EventHost.Builder()
            .firstName(request.firstName())
            .middleName(request.middleName())
            .lastName(request.lastName())
            .dateOfBirth(request.dateOfBirth())
            .email(request.email())
            .plaintextPassword(request.password())
            .build();
        String accountVerificationToken = newEventHost.generateVerificationToken();

        // Send the verification email FIRST, before persisting to the database.
        // If the email fails to send, the @Transactional annotation will roll back
        // the transaction and nothing will be persisted.
        emailService.sendVerificationEmail(request.email(), accountVerificationToken);

        // Only persist the entity after the email has been successfully sent.
        newEventHost = eventHostRepository.save(newEventHost);

        String responseMessage = 
            "An account verification link has been sent to your email address. " +
            "Please click it to verify your account. " +
            "The link expires in " +
            AppConstants.Database.EventHosts.Sizes.VERIFICATION_DURATION_HOURS +
            " hour" + 
            (AppConstants.Database.EventHosts.Sizes.VERIFICATION_DURATION_HOURS > 1 ? "s" : "") +
            ".";

        // Return a response to the user.
        return new SingleMessageResponse(responseMessage);
    }

    /**
     * Service a request to verify an event host account.
     * @param verificationToken the verification token from the email link
     * @return a SingleMessageResponse on success
     * @throws EventHostToVerifyNotFoundException if no existing event host account
     * using the verification token was found
     * @throws EventHostVerificationPeriodExpiredException if the verification token
     * has expired
     * @throws EventHostInactiveException if the event host account associated
     * with the verification token is currently inactive
     */
    public SingleMessageResponse verifyEventHost(String verificationToken)
    {
        // Hash the verification token and search for an EventHost with a matching verification token hash.
        byte[] hashedVerificationToken = hashingService.hashToken(verificationToken);
        Optional<EventHost> foundEventHost = eventHostRepository.findByVerificationKeyHash(hashedVerificationToken);
        if (foundEventHost.isEmpty())
        {
            throw new EventHostToVerifyNotFoundException("No existing event host account using that verification token was found");
        }

        EventHost eventHostToVerify = foundEventHost.get();

        // Check whether the verification token has already expired.
        if (!eventHostToVerify.isVerified() && LocalDateTime.now().isAfter(eventHostToVerify.getVerificationExpires()))
        {
            throw new EventHostVerificationPeriodExpiredException("The verification token has expired");
        }

        // Check whether the event host account is inactive.
        if (!eventHostToVerify.isActive())
        {
            throw new EventHostInactiveException("The event host account is no longer active");
        }

        // Check whether the account is already verified.
        if (eventHostToVerify.isVerified())
        {
            return new SingleMessageResponse("Your account has already been verified.");
        }

        // Mark the account as verified.
        eventHostToVerify.setVerified(true);
        eventHostToVerify.setLastUpdated(LocalDateTime.now());
        eventHostRepository.save(eventHostToVerify);

        return new SingleMessageResponse("Your account has been successfully verified.");
    }

    /**
     * Service a request to get an event host account's profile info.
     * 
     * @param eventHost the authenticated EventHost
     * @return a GetEventHostProfileResponse on success
     * @throws UnauthorizedException if eventHost is null (implying that the 
     * JWT authentication filter could not authenticate an EventHost)
     */
    public GetEventHostProfileResponse getEventHostProfile(EventHost eventHost)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return new GetEventHostProfileResponse
        (
            eventHost.getFirstName(),
            eventHost.getMiddleName(),
            eventHost.getLastName(),
            eventHost.getEmail(),
            eventHost.getLastLogin()
        );
    }

    /**
     * Service a request to edit an event host's full name.
     * @param eventHost the authenticated EventHost
     * @param request the request body
     * @return a SingleMessageResponse on success
     * @throws UnauthorizedException if eventHost is null (implying that the 
     * JWT authentication filter could not authenticate an EventHost)
     */
    public SingleMessageResponse editEventHostName(EventHost eventHost, EditEventHostNameRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        eventHost.setFirstName(request.firstName());
        eventHost.setMiddleName(request.middleName());
        eventHost.setLastName(request.lastName());
        eventHost.setLastUpdated(LocalDateTime.now());
        eventHostRepository.save(eventHost);

        return new SingleMessageResponse("Your name has been updated.");
    }

    /**
     * Service a reques to change an event host's password.
     * @param eventHost the authenticated EventHost
     * @param request the request body
     * @return a SingleMessageResponse on success
     * @throws UnauthorizedException if eventHost is null (implying that the 
     * JWT authentication filter could not authenticate an EventHost)
     */
    public SingleMessageResponse editEventHostPassword(EventHost eventHost, EditEventHostPasswordRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        eventHost.setPassword(request.password());
        eventHost.setLastUpdated(LocalDateTime.now());
        eventHostRepository.save(eventHost);

        return new SingleMessageResponse("Your password has been updated.");
    }

    /**
     * Services a request to change an event host's email address.
     * @param eventHost the authenticated EventHost
     * @param request the request body
     * @return a SingleMessageResponse on success
     * @throws UnauthorizedException if eventHost is null (implying that the 
     * JWT authentication filter could not authenticate an EventHost)
     * @throws EditEventHostEmailResponse if the new email address is
     * already in use by a different, preexisting event host account
     */
    public SingleMessageResponse editEventHostEmail(EventHost eventHost, EditEventHostEmailRequest request)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        // Enforce the email allowlist for the new address.
        accessControlService.requireEmailAllowed(request.email());

        byte[] newEmailBlindIndex = blindIndexService.computeIndex(request.email());
        if (Arrays.equals(eventHost.getEmailBlindIndex(), newEmailBlindIndex))
        {
            return new SingleMessageResponse("The provided email address is the same as your current email address.");
        }

        if (eventHostRepository.existsByEmailIndex(newEmailBlindIndex))
        {
            throw new EmailAlreadyExistsException("The provided email address is already in use by a different account.");
        }

        eventHost.setEmail(request.email());
        eventHost.setLastUpdated(LocalDateTime.now());
        eventHostRepository.save(eventHost);

        return new SingleMessageResponse("Your email address has been updated.");
    }

    /**
     * Services a request to delete an event host's account.
     * @param eventHost the authenticated EventHost
     * @return a SingleMessageResponse on success
     * @throws UnauthorizedException if eventHost is null (implying that the 
     * JWT authentication filter could not authenticate an EventHost)
     * @throws PendingEventsExistException if there exists at least one event
     * under the event host's account which has not ended yet
     */
    public SingleMessageResponse deleteEventHost(EventHost eventHost)
    {
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }

        if (eventRepository.activeEventsByEventHostIdExist(eventHost.getId()))
        {
            throw new PendingEventsExistException
            ("You have at least one event under your account that has not ended yet. Please cancel those events before deleting your account.");
        }

        eventHostRepository.delete(eventHost);
        return new SingleMessageResponse("Your account has been successfully deleted.");
    }
}