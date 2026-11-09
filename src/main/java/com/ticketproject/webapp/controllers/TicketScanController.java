package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.services.model.TicketScanService;
import com.ticketproject.webapp.dtos.requests.ScanTicketRequest;
import com.ticketproject.webapp.dtos.responses.GetScannedTicketsByEventHostResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * TicketScanController is a REST controller that routes requests
 * concerning TicketScan entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.TicketScans.ROOT)
@Tag(name = "Ticket Scans", description = "Endpoints for scanning attendee tickets and retrieving scan history")
public class TicketScanController
{
    private TicketScanService ticketScanService;

    public TicketScanController(TicketScanService ticketScanService)
    {
        this.ticketScanService = ticketScanService;
    }

    /**
     * Handles a request to let a logged in user scan
     * an attendee's ticket for an event.
     * @param request the request body
     * @param servletRequest the HTTP Servlet request
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Scan an attendee's ticket",
        description =
            "Scans an attendee's ticket by providing the public token " +
            "encoded in the ticket's QR code. The ticket is validated " +
            "against the event host's events, and if valid, the attendee " +
            "is marked as present. Requires authentication."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse scanTicket
    (
        @Valid
        @RequestBody ScanTicketRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return ticketScanService.scanTicket(eventHost, request);
    }

    /**
     * Handles a request to let a logged in user retrieve a list of
     * all attendee tickets which they have scanned.
     * @param request the request body
     * @return a GetScannedTicketsByEventHostResponse on success
     */
    @Operation
    (
        summary = "Get all tickets scanned by the authenticated event host",
        description =
            "Retrieves a list of all attendee tickets that have been " +
            "scanned by the authenticated event host, including details " +
            "about each scanned ticket such as attendee information, " +
            "event details, and scan timestamp. Requires authentication."
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public GetScannedTicketsByEventHostResponse getScannedTicketsByEventHost(HttpServletRequest request)
    {
        EventHost eventHost = (EventHost) request.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return ticketScanService.getScannedTicketsByEventHost(eventHost);
    }

    /**
     * Handles a request to let a logged in user retrieve a list of
     * attendee tickets scanned for a specific event
     * (identified by the event's public ID).
     * @param publicId the event's public ID
     * @param servletRequest the HTTP Servlet request
     * @return a GetScannedTicketsByEventHostResponse on success
     */
    @Operation
    (
        summary = "Get tickets scanned for a specific event",
        description =
            "Retrieves a list of attendee tickets that have been " +
            "scanned for an event (identified by its public ID). " +
            "Includes details about each scanned ticket such as " +
            "attendee information, event details, and scan timestamp. " +
            "Requires authentication."
    )
    @GetMapping(ApiPaths.TicketScans.BY_EVENT_PUBLIC_ID)
    @ResponseStatus(HttpStatus.OK)
    public GetScannedTicketsByEventHostResponse getScannedTicketsByEventPublicId
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
        return ticketScanService.getScannedTicketsByEventPublicId(eventHost, publicId);
    }
}