package com.ticketproject.webapp.dtos.requests;

import com.ticketproject.webapp.constants.AppConstants;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EditEventHostPasswordRequest specifies what a valid request to
 * change the logged in event host's password should look like.
 * 
 * @param password the new password
 */
@Schema(description = "Request body for editing the authenticated event host's password")
public record EditEventHostPasswordRequest
(
    @Schema
    (
        description = "The new password for the event host account, must be between 12 and 128 characters and strong enough to resist guessing",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "NewSecureP@ssw0rd456",
        minimum = "12",
        maximum = "128"
    )
    @NotBlank(message = "Password is required")
    @Size
    (
        min = AppConstants.Database.EventHosts.Sizes.MIN_PASSWORD_LENGTH,
        max = AppConstants.Database.EventHosts.Sizes.MAX_PASSWORD_LENGTH,
        message = "Password must be between " +
        AppConstants.Database.EventHosts.Sizes.MIN_PASSWORD_LENGTH +
        " and " +
        AppConstants.Database.EventHosts.Sizes.MAX_PASSWORD_LENGTH +
        " characters long"
    )
    String password
)
{
}