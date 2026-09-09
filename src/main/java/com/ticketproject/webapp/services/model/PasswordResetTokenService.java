package com.ticketproject.webapp.services.model;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.dtos.requests.CreatePasswordResetTokenRequest;
import com.ticketproject.webapp.dtos.requests.UsePasswordResetTokenRequest;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.InvalidCredentialsException;
import com.ticketproject.webapp.model.repositories.PasswordResetTokenRepository;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.database.HashingService;
import com.ticketproject.webapp.services.email.EmailService;
import com.ticketproject.webapp.services.access.AccessControlService;
import com.ticketproject.webapp.model.entities.PasswordResetToken;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.repositories.EventHostRepository;

/**
 * PasswordResetTokenService is a service used by controllers to handle API route requests
 * involving PasswordResetToken entities (i.e. "Forgot Password?" link).
 */
@Service
@Transactional
public class PasswordResetTokenService
{
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EventHostRepository eventHostRepository;
    private final EmailService emailService;
    private final BlindIndexService blindIndexService;
    private final HashingService hashingService;
    private final AccessControlService accessControlService;

    public PasswordResetTokenService
    (
        PasswordResetTokenRepository passwordResetTokenRepository,
        EventHostRepository eventHostRepository,
        EmailService emailService,
        BlindIndexService blindIndexService,
        HashingService hashingService,
        AccessControlService accessControlService
    )
    {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.eventHostRepository = eventHostRepository;
        this.emailService = emailService;
        this.blindIndexService = blindIndexService;
        this.hashingService = hashingService;
        this.accessControlService = accessControlService;
    }

    /**
     * Service a request to reset the password of an event host account by
     * creating a password reset token and emailing it to the address
     * specified in the request.
     * 
     * @param request the request body
     * @return a SingleMessageResponse on success
     * @throws EmailSendFailedException if the email cannot be sent for whatever reason
     */
    public SingleMessageResponse createPasswordResetToken(CreatePasswordResetTokenRequest request)
    {
        // If the email is not on the allowlist, return the same generic message
        // used when no account exists, without creating or sending anything.
        // This avoids revealing whether an account exists for a disallowed address.
        if (!accessControlService.isEmailAllowed(request.email()))
        {
            return new SingleMessageResponse
            ("If an account with the provided email address exists, a password reset token will be sent to that address.");
        }

        // Check if there exists an event host account with the provided email address.
        byte[] emailBlindIndex = blindIndexService.computeIndex(request.email());
        Optional<EventHost> foundEventHost = eventHostRepository.findByEmailIndex(emailBlindIndex);

        if (foundEventHost.isEmpty())
        {
            return new SingleMessageResponse
            ("If an account with the provided email address exists, a password reset token will be sent to that address.");
        }

        EventHost eventHost = foundEventHost.get();

        PasswordResetToken passwordResetToken = new PasswordResetToken.Builder()
            .tokenHash(new byte[]{})
            .eventHost(eventHost)
            .build();
        String rawToken = passwordResetToken.generateToken();

        // Don't persist a PasswordResetToken entity to the database unless the email has been successfully sent.
        emailService.sendPasswordResetEmail(request.email(), rawToken);

        // At this point, the email should have been sent successfully.
        passwordResetToken = passwordResetTokenRepository.save(passwordResetToken);

        return new SingleMessageResponse
        ("If an account with the provided email address exists, a password reset token will be sent to that address.");
    }

    /**
     * Services a request to use a password reset token.
     * 
     * @param request the request body
     * @return a SingleMessageResponse on success
     * @throws InvalidCredentialsException if no account is found
     * with the password reset token provided in the request
     */
    public SingleMessageResponse usePasswordResetToken(UsePasswordResetTokenRequest request)
    {
        byte[] tokenHash = hashingService.hashToken(request.passwordResetToken());
        Optional<PasswordResetToken> passwordResetToken = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (passwordResetToken.isEmpty())
        {
            throw new InvalidCredentialsException("No account found with that password reset token");
        }

        PasswordResetToken foundToken = passwordResetToken.get();
        EventHost foundEventHost = foundToken.getEventHost();

        foundEventHost.setPassword(request.password());
        foundEventHost.setLastUpdated(LocalDateTime.now());
        foundToken.setUsed(true);
        foundToken.setEventHost(foundEventHost);

        foundToken = passwordResetTokenRepository.save(foundToken);
        foundEventHost = eventHostRepository.save(foundEventHost);

        return new SingleMessageResponse("Your password has been reset.");
    }
}
