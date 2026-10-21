package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.dtos.requests.CreateEventHostRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostEmailRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostNameRequest;
import com.ticketproject.webapp.dtos.requests.EditEventHostPasswordRequest;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.dtos.responses.GetEventHostProfileResponse;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.services.model.EventHostService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * EventHostController is a REST controller that routes requests
 * concerning EventHost entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.EventHosts.ROOT)
@Tag(name = "Event Hosts", description = "Endpoints for creating, verifying, and managing event host accounts")
public class EventHostController
{
    private final EventHostService eventHostService;

    public EventHostController(EventHostService eventHostService)
    {
        this.eventHostService = eventHostService;
    }

    /**
     * Handles a request to create a new EventHost.
     * @param request a CreateEventHostRequest from the client
     * @return a SingleMessageResponse indicating the result of the request
     */
    @Operation
    (
        summary = "Create a new event host account",
        description =
            "Registers a new event host account with the provided personal " +
            "details, email address, password, and date of birth. The password " +
            "must be between 12 and 128 characters long and must also be strong " +
            "enough to resist guessing (it is scored from 0 to 4 by zxcvbn, and " +
            "scores below the configured minimum are rejected). A " +
            "verification email will be sent to the provided email address. " +
            "This endpoint does not require authentication."
    )
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse createEventHost
    (
        @Valid
        @RequestBody CreateEventHostRequest request
    )
    {
        return eventHostService.createEventHost(request);
    }

    /**
     * Handles a request to verify a new EventHost.
     * This endpoint is accessed via a clickable link in the verification email.
     * @param verificationToken the verification token from the email link
     * @return a SingleMessageResponse indicating the result of the request
     */
    @Operation
    (
        summary = "Verify a new event host account",
        description =
            "Verifies a newly created event host account using the " +
            "verification token sent via email. This endpoint is typically " +
            "accessed by clicking the link in the verification email. " +
            "This endpoint does not require authentication."
    )
    @SecurityRequirements
    @GetMapping(ApiPaths.EventHosts.VERIFICATION)
    @ResponseStatus(HttpStatus.OK)
    public SingleMessageResponse verifyEventHost
    (
        @Parameter
        (
            description = "The verification token received in the verification email",
            required = true,
            example = "abc123def456..."
        )
        @RequestParam("token") String verificationToken
    )
    {
        return eventHostService.verifyEventHost(verificationToken);
    }

    /**
     * Handles a request to retrieve profile info on a logged in EventHost.
     * @param request an HttpServletRequest from the client
     * containing a JWT in the Authorization header
     * @return a GetEventHostProfileResponse containing profile info
     * on the logged in EventHost
     */
    @Operation
    (
        summary = "Get the authenticated event host's profile",
        description =
            "Retrieves profile information for the authenticated event host, " +
            "including their name, email address, and last login time. " +
            "Requires authentication."
    )
    @GetMapping(ApiPaths.EventHosts.PROFILE)
    public GetEventHostProfileResponse getEventHostProfile(HttpServletRequest request)
    {
        EventHost eventHost = (EventHost) request.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return eventHostService.getEventHostProfile(eventHost);
    }

    /**
     * Handles a request to change the full name of the logged in EventHost.
     * @param request the request body
     * @param servletRequest an HttpServletRequest from the client
     * containing a JWT in the Authorization header
     * @return a SingleMessageResponse describing the request's result
     */
    @Operation
    (
        summary = "Edit the authenticated event host's name",
        description =
            "Updates the full name (first, middle, and last) of the " +
            "authenticated event host. Requires authentication."
    )
    @PatchMapping(ApiPaths.EventHosts.FULL_NAME)
    public SingleMessageResponse editEventHostName
    (
        @Valid
        @RequestBody
        EditEventHostNameRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return eventHostService.editEventHostName(eventHost, request);
    }

    /**
     * Handles a request to change the password of the logged in EventHost.
     * @param request the request body
     * @param servletRequest an HttpServletRequest from the client
     * @return a SingleMessageResponse describing the request's result
     */
    @Operation
    (
        summary = "Edit the authenticated event host's password",
        description =
            "Updates the password of the authenticated event host. " +
            "The new password must meet the minimum and maximum length " +
            "requirements, and must also be strong enough to resist " +
            "guessing (it is scored from 0 to 4 by zxcvbn, and scores " +
            "below the configured minimum are rejected). " +
            "Requires authentication."
    )
    @PatchMapping(ApiPaths.EventHosts.PASSWORD)
    public SingleMessageResponse editEventHostPassword
    (
        @Valid
        @RequestBody
        EditEventHostPasswordRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return eventHostService.editEventHostPassword(eventHost, request);
    }

    /**
     * Handles a request to change the email address of the logged in EventHost.
     * @param request the request body
     * @param servletRequest an HttpServletRequest from the client
     * @return a SingleMessageResponse describing the request's result
     */
    @Operation
    (
        summary = "Edit the authenticated event host's email address",
        description =
            "Updates the email address of the authenticated event host. " +
            "The new email address must be valid and not already in use " +
            "by another account. Requires authentication."
    )
    @PatchMapping(ApiPaths.EventHosts.EMAIL)
    public SingleMessageResponse editEventHostEmail
    (
        @Valid
        @RequestBody
        EditEventHostEmailRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return eventHostService.editEventHostEmail(eventHost, request);
    }

    /**
     * Handles a reques to delete the logged in event host's account
     * (which should also delete any other data related to said account).
     * @param request the HttpServletRequest from the client
     * @return a SingleMessageResponse describing the request's result
     */
    @Operation
    (
        summary = "Delete the authenticated event host's account",
        description =
            "Permanently deletes the authenticated event host's account " +
            "and all associated data, including events, sessions, and " +
            "other related records. Requires authentication."
    )
    @DeleteMapping
    public SingleMessageResponse deleteEventHost(HttpServletRequest request)
    {
        EventHost eventHost = (EventHost) request.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return eventHostService.deleteEventHost(eventHost);
    }
}