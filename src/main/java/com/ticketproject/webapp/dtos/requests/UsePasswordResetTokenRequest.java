package com.ticketproject.webapp.dtos.requests;

import com.ticketproject.webapp.constants.AppConstants;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UsePasswordResetTokenRequest specifies what a valid request
 * to use a password reset token should look like.
 * 
 * @param passwordResetToken the password reset token
 * @param password the new password
 */
@Schema(description = "Request body for using a password reset token to set a new password")
public record UsePasswordResetTokenRequest
(
    @Schema
    (
        description = "The password reset token received via email",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "abc123def456...",
        maximum = "64"
    )
    @NotBlank(message = "Password reset token is required")
    @Size
    (
        max = AppConstants.Database.PasswordResetTokens.Sizes.MAX_PASSWORD_RESET_TOKEN_LENGTH,
        message = "Password reset token cannot be longer than " +
        AppConstants.Database.PasswordResetTokens.Sizes.MAX_PASSWORD_RESET_TOKEN_LENGTH +
        " characters long"
    )
    String passwordResetToken,

    @Schema
    (
        description = "The new password for the account, must be between 12 and 128 characters and strong enough to resist guessing",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "NewSecureP@ssw0rd789",
        minimum = "12",
        maximum = "128"
    )
    @NotBlank(message = "New password is required")
    @Size
    (
        min = AppConstants.Database.EventHosts.Sizes.MIN_PASSWORD_LENGTH,
        max = AppConstants.Database.EventHosts.Sizes.MAX_PASSWORD_LENGTH,
        message = "New password must be between " +
        AppConstants.Database.EventHosts.Sizes.MIN_PASSWORD_LENGTH +
        " and " +
        AppConstants.Database.EventHosts.Sizes.MAX_PASSWORD_LENGTH +
        " characters long"
    )
    String password
)
{
}