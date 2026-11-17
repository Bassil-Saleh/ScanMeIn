package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.services.model.TicketService;
import com.ticketproject.webapp.dtos.requests.DeleteTicketsRequest;
import com.ticketproject.webapp.dtos.requests.RespondToInvitationRequest;
import com.ticketproject.webapp.dtos.responses.GetTicketsByEventPublicIdResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.model.entities.EventHost;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * TicketController is a REST controller that routes requests
 * concerning Ticket entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.Tickets.ROOT)
@Tag
(
    name = "Tickets",
    description = "Endpoints for managing and modifying ticket records"
)
public class TicketController
{
    private TicketService ticketService;

    public TicketController(TicketService ticketService)
    {
        this.ticketService = ticketService;
    }

    /**
     * Handles a request to respond to an invitation to a private event.
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Respond to an invitation for a private event",
        description =
            "Records a response to an invitation for a private event. " +
            "This endpoint does not require authentication."
    )
    @SecurityRequirements
    @PatchMapping(ApiPaths.Tickets.INVITATION)
    @ResponseStatus(HttpStatus.OK)
    public SingleMessageResponse respondToInvitation
    (
        @Valid
        @RequestBody RespondToInvitationRequest request
    )
    {
        return ticketService.respondToInvitation(request);
    }

    /**
     * Handles a request to let a logged in event host retrieve
     * a list of records on tickets for a specific event.
     * Only the event host who created the event should be
     * allowed to manage those records.
     * @param publicId the event's public ID
     * @param servletRequest the HTTP Servlet request
     * @return a GetTicketsByEventPublicIdResponse on success
     */
    @Operation
    (
        summary = "Retrieve info on a list of tickets for a specific event",
        description =
            "Retrieves a list of records on tickets for a specific event. " +
            "Only the event host who created the event should be allowed to see those records. " +
            "Requires authentication."
    )
    @GetMapping(ApiPaths.Tickets.BY_EVENT_PUBLIC_ID)
    @ResponseStatus(HttpStatus.OK)
    public GetTicketsByEventPublicIdResponse getTicketsByEventPublicId
    (
        @PathVariable String publicId,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return ticketService.getTicketsByEventPublicId(eventHost, publicId);
    }

    /**
     * Handles a request to let a logged in event host delete a
     * list of tickets for a specific event (using a list of
     * email addresses which the tickets are designated to).
     * Only the event host who created the event should be
     * allowed to delete those tickets.
     * @param request the request body
     * @param servletRequest the HTTP Servlet request
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Delete a list of tickets for a specific event",
        description =
            "Deletes a list of tickets (using a list of email addresses which " +
            "the tickets are designated to) for a specific event. " +
            "Only the event host who created the event should be allowed " +
            "to delete those tickets. Requires authentication."
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public SingleMessageResponse deleteTickets
    (
        @Valid
        @RequestBody DeleteTicketsRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return ticketService.deleteTickets(eventHost, request);
    }
}
