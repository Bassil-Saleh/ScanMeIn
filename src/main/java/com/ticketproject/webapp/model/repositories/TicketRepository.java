package com.ticketproject.webapp.model.repositories;

import com.ticketproject.webapp.model.entities.Ticket;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * TicketRepository is used to perform CRUD operations with Ticket entities.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>
{
    /**
     * Check whether an Attendee is registered for an Event
     * based on the ID of a given Event.
     * @param emailBlindIndex the blind index of an Attendee's email address
     * @param eventId the ID of an Event
     * @return true if the Attendee has a registration for the given Event, false otherwise
     */
    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.attendee.emailBlindIndex = :emailBlindIndex AND t.event.id = :eventId")
    boolean attendeeRegistrationExistsByEventId
    (
        @Param("emailBlindIndex")
        byte[] emailBlindIndex,

        @Param("eventId")
        Long eventId
    );

    /**
     * Find and retrieve a Ticket based on a given token identifier value.
     * @param tokenIdentifier a token identifier
     * @return an Optional<Ticket>
     */
    @Query("SELECT t FROM Ticket t JOIN FETCH t.event WHERE t.tokenIdentifier = :tokenIdentifier LIMIT 1")
    Optional<Ticket> findByTokenIdentifier(@Param("tokenIdentifier") String tokenIdentifier);

    /**
     * Get the number of registrations for a given Event identified by an Event's ID.
     * @param eventId the ID of an Event
     * @return the number of registrations for the given Event
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event.id = :eventId")
    long getRegistrationCountByEventId(@Param("eventId") Long eventId);

    /**
     * Find and retrieve all Ticket (and Attendee) entities for a given Event
     * identified by its ID, such that each Ticket entity is not marked as deleted.
     * @param eventId the ID of an Event
     * @return a list of Ticket entities which are not marked as deleted
     */
    @Query("SELECT t FROM Ticket t JOIN FETCH t.attendee WHERE t.event.id = :eventId AND t.deletedAt IS NULL")
    List<Ticket> findAllActiveTicketsByEventId(@Param("eventId") Long eventId);

    /**
     * Find and retrieve all active Ticket entities for a given Event
     * using the event's ID and a list of email blind indexes.
     * @param emailBlindIndexes a list of email blind indexes
     * @param eventId the ID of an event
     * @return a list of Ticket entities which are not marked as deleted
     */
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :eventId AND t.deletedAt IS NULL AND t.attendee.emailBlindIndex IN :emailBlindIndexes")
    List<Ticket> findAllActiveTicketsByEmailBlindIndex
    (
        @Param("emailBlindIndexes")
        List<byte[]> emailBlindIndexes,

        @Param("eventId")
        Long eventId
    );

    /**
     * Find and retrieve an active Ticket entity for a given Event
     * using the event's ID and an email blind index.
     * @param emailBlindIndex an email blind index
     * @param eventId the ID of an event
     * @return a Ticket entity
     */
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :eventId AND t.deletedAt IS NULL AND t.attendee.emailBlindIndex = :emailBlindIndex")
    Optional<Ticket> findActiveTicketByEmailBlindIndexAndEventId
    (
        @Param("emailBlindIndex")
        byte[] emailBlindIndex,

        @Param("eventId")
        Long eventId
    );
}
