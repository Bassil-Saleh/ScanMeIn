package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.dtos.requests.CreatePasswordResetTokenRequest;
import com.ticketproject.webapp.dtos.requests.UsePasswordResetTokenRequest;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.services.model.PasswordResetTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * PasswordResetTokenController is a REST controller that routes requests
 * concerning PasswordResetToken entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.PasswordResetTokens.ROOT)
@Tag(name = "Password Reset Tokens", description = "Endpoints for requesting and using password reset tokens")
public class PasswordResetTokenController
{
    private final PasswordResetTokenService passwordResetTokenService;

    public PasswordResetTokenController(PasswordResetTokenService passwordResetTokenService)
    {
        this.passwordResetTokenService = passwordResetTokenService;
    }

    /**
     * Handles a request to reset the password of an event host account.
     * 
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Request a password reset token",
        description =
            "Initiates a password reset for an event host account by sending " +
            "a password reset link to the provided email address. If the " +
            "email address is associated with an existing account, a reset " +
            "token will be generated and emailed. This endpoint does not " +
            "require authentication."
    )
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse createPasswordResetToken
    (
        @Valid
        @RequestBody CreatePasswordResetTokenRequest request
    )
    {
        return passwordResetTokenService.createPasswordResetToken(request);
    }

    /**
     * Handles a request to use a password reset token.
     * 
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Use a password reset token to set a new password",
        description =
            "Completes the password reset process by validating the provided " +
            "password reset token and setting a new password for the " +
            "associated account. The token must not be expired or already " +
            "used. The new password must be between 12 and 128 characters " +
            "long and must also be strong enough to resist guessing (it is " +
            "scored from 0 to 4 by zxcvbn, and scores below the configured " +
            "minimum are rejected). This endpoint does not require " +
            "authentication."
    )
    @SecurityRequirements
    @PatchMapping
    @ResponseStatus(HttpStatus.OK)
    public SingleMessageResponse usePasswordResetToken
    (
        @Valid
        @RequestBody UsePasswordResetTokenRequest request
    )
    {
        return passwordResetTokenService.usePasswordResetToken(request);
    }
}