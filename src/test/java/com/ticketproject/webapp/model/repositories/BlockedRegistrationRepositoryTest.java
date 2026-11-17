package com.ticketproject.webapp.model.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.sql.SQLException;
import java.sql.Connection;
import javax.sql.DataSource;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.ticketproject.webapp.bridges.SpringContextBridge;
import com.ticketproject.webapp.model.entities.Attendee;
import com.ticketproject.webapp.model.entities.BlockedRegistration;
import com.ticketproject.webapp.model.entities.Event;
import com.ticketproject.webapp.model.entities.EventAddress;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.model.entities.EventSigningKey;
import com.ticketproject.webapp.model.enums.EventStatus;
import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.model.enums.RegistrationStatus;
import com.ticketproject.webapp.services.database.BlindIndexService;
import com.ticketproject.webapp.services.database.CryptoService;
import com.ticketproject.webapp.services.database.HashingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BlockedRegistrationRepositoryTest contains integration tests for the
 * BlockedRegistration entity and BlockedRegistrationRepository, covering
 * rollback on failure, data constraints, data integrity, and commit atomicity.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SpringContextBridge.class, CryptoService.class, BlindIndexService.class, HashingService.class})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BlockedRegistrationRepositoryTest
{
    @Autowired
    private BlockedRegistrationRepository blockedRegistrationRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventHostRepository eventHostRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Attendee savedAttendee;
    private Event savedEvent;
    private EventHost savedHost;
    private TransactionTemplate txTemplate;

    /**
     * Creates and persists shared entities used across tests.
     */
    @BeforeEach
    void setUp()
    {
        // Initialize a TransactionTemplate to programmatically manage transactions
        txTemplate = new TransactionTemplate(transactionManager);

        savedAttendee = attendeeRepository.saveAndFlush
        (
            new Attendee.Builder()
                .firstName("Blocked")
                .lastName("User")
                .email("blocked-" + UUID.randomUUID() + "@example.com")
                .build()
        );

        EventHost host = new EventHost.Builder()
            .firstName("Blocker")
            .lastName("Host")
            .dateOfBirth(LocalDate.of(1980, 12, 1))
            .email("blocker-" + UUID.randomUUID() + "@example.com")
            .plaintextPassword("password123")
            .build();
        host.generateVerificationToken();
        savedHost = eventHostRepository.saveAndFlush(host);

        EventAddress address = new EventAddress.Builder()
            .addressLine1("100 Block St")
            .city("Blocktown")
            .state("CA")
            .postalCode("90001")
            .country("USA")
            .build();

        Event event = new Event.Builder()
            .publicId(UUID.randomUUID().toString())
            .eventHost(savedHost)
            .name("Blocked Event")
            .description("An event with blocked registrations")
            .startDateTime(LocalDateTime.now().plusDays(30))
            .endDateTime(LocalDateTime.now().plusDays(30).plusHours(2))
            .eventType(EventType.PUBLIC)
            .maxAttendees(200L)
            .build();

        EventSigningKey signingKey = CryptoService.createSigningKey(event);

        event.setEventAddress(address);
        event.setRegistrationStatus(RegistrationStatus.OPEN);
        event.setEventStatus(EventStatus.PUBLISHED);
        event.setSigningKey(signingKey);
        savedEvent = eventRepository.saveAndFlush(event);
    }

    /**
     * Helper method to create a valid BlockedRegistration entity.
     * @return a new BlockedRegistration entity (not yet persisted)
     */
    private BlockedRegistration createBlock()
    {
        return new BlockedRegistration.Builder()
            .attendee(savedAttendee)
            .event(savedEvent)
            .blockedBy(savedHost)
            .reason("Spam registration")
            .build();
    }

    @Nested
    @DisplayName("Data constraints")
    class DataConstraints
    {
        @Test
        @DisplayName("Saving block with null attendee violates NOT NULL constraint")
        void nullAttendeeThrowsException()
        {
            BlockedRegistration block = createBlock();
            block.setAttendee(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(block))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Saving block with null event violates NOT NULL constraint")
        void nullEventThrowsException()
        {
            BlockedRegistration block = createBlock();
            block.setEvent(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(block))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Saving block with null blockedBy violates NOT NULL constraint")
        void nullBlockedByThrowsException()
        {
            BlockedRegistration block = createBlock();
            block.setBlockedBy(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(block))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Saving block with null reason is allowed (optional field)")
        void nullReasonIsAllowed()
        {
            BlockedRegistration block = createBlock();
            block.setReason(null);

            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getReason()).isNull();
        }

        @Test
        @DisplayName("Updating block with null attendee violates NOT NULL constraint")
        void updateNullAttendeeThrowsException()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getAttendee()).isNotNull();
            saved.setAttendee(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(saved))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Updating block with null event violates NOT NULL constraint")
        void updateNullEventThrowsException()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getEvent()).isNotNull();
            saved.setEvent(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(saved))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Updating block with null blockedBy violates NOT NULL constraint")
        void updateNullBlockedByThrowsException()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getBlockedBy()).isNotNull();
            saved.setBlockedBy(null);

            assertThatThrownBy(() -> blockedRegistrationRepository.saveAndFlush(saved))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Updating block with null reason is allowed (optional field)")
        void updateNullReasonIsAllowed()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getReason()).isNotNull();
            saved.setReason(null);

            saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getReason()).isNull();
        }
    }

    @Nested
    @DisplayName("Data integrity")
    class DataIntegrity
    {
        @Test
        @DisplayName("Block references valid attendee, event, and blocker after save")
        void blockReferencesValidEntities()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();

            BlockedRegistration loaded = blockedRegistrationRepository.findById(saved.getId()).orElseThrow();
            assertThat(loaded).isNotNull();
            assertThat(loaded.getAttendee()).isNotNull();
            assertThat(loaded.getEvent()).isNotNull();
            assertThat(loaded.getBlockedBy()).isNotNull();
            assertThat(loaded.getAttendee().getId()).isNotNull();
            assertThat(loaded.getEvent().getId()).isNotNull();
            assertThat(loaded.getBlockedBy().getId()).isNotNull();

            assertThat(loaded.getAttendee().getId()).isEqualTo(savedAttendee.getId());
            assertThat(loaded.getEvent().getId()).isEqualTo(savedEvent.getId());
            assertThat(loaded.getBlockedBy().getId()).isEqualTo(savedHost.getId());
        }

        @Test
        @DisplayName("isActive returns true when revoked is null")
        void isActiveWhenNotRevoked()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);

            assertThat(saved).isNotNull();
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("isActive returns false after setting revoked timestamp")
        void isNotActiveAfterRevoked()
        {
            BlockedRegistration block = createBlock();
            BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();

            saved.setRevoked(LocalDateTime.now());
            saved = blockedRegistrationRepository.saveAndFlush(saved);
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();

            BlockedRegistration loaded = blockedRegistrationRepository.findById(saved.getId()).orElseThrow();
            assertThat(loaded).isNotNull();
            assertThat(loaded.isActive()).isFalse();
            assertThat(loaded.getRevoked()).isNotNull();
        }

        @Test
        @DisplayName("Multiple blocks for same attendee on different events are allowed")
        void multipleBlocksDifferentEvents()
        {
            long blockedRegistrationCountBefore = blockedRegistrationRepository.count();

            BlockedRegistration block1 = createBlock();
            block1 = blockedRegistrationRepository.saveAndFlush(block1);

            EventAddress address2 = new EventAddress.Builder()
                .addressLine1("200 Other St")
                .city("Othertown")
                .state("CA")
                .postalCode("90002")
                .country("USA")
                .build();

            Event event2 = new Event.Builder()
                .publicId(UUID.randomUUID().toString())
                .eventHost(savedHost)
                .name("Another Event")
                .description("Another event")
                .startDateTime(LocalDateTime.now().plusDays(60))
                .endDateTime(LocalDateTime.now().plusDays(60).plusHours(2))
                .eventType(EventType.PUBLIC)
                .maxAttendees(100L)
                .build();

            EventSigningKey signingKey2 = CryptoService.createSigningKey(event2);

            event2.setEventAddress(address2);
            event2.setRegistrationStatus(RegistrationStatus.OPEN);
            event2.setEventStatus(EventStatus.PUBLISHED);
            event2.setSigningKey(signingKey2);
            Event savedEvent2 = eventRepository.saveAndFlush(event2);

            BlockedRegistration block2 = new BlockedRegistration.Builder()
                .attendee(savedAttendee)
                .event(savedEvent2)
                .blockedBy(savedHost)
                .reason("Different reason")
                .build();

            BlockedRegistration saved2 = blockedRegistrationRepository.saveAndFlush(block2);
            assertThat(saved2).isNotNull();
            assertThat(saved2.getId()).isNotNull();
            assertThat(blockedRegistrationRepository.count()).isEqualTo(blockedRegistrationCountBefore + 2);
        }
    }

    @Nested
    @DisplayName("Rollback on failure")
    class RollbackOnFailure
    {
        @Test
        @DisplayName("Diagnostic: Verify JDBC auto-commit is disabled")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void verifyAutoCommitIsDisabled() throws SQLException
        {
            try (Connection conn = dataSource.getConnection())
            {
                boolean isAutoCommit = conn.getAutoCommit();

                // If this assertion fails, auto-commit is ON, which breaks rollback testing.
                assertThat(isAutoCommit)
                    .as("JDBC auto-commit must be false for transaction rollbacks to work")
                    .isFalse();
            }
        }

        @Test
        @DisplayName("Failed block save does not persist the block")
        @Transactional(propagation = Propagation.NOT_SUPPORTED) // Disable test-managed transaction for this test
        void failedSaveDoesNotPersist()
        {
            // Capture baseline count before attempting the save
            long blockedRegistrationCountBefore = blockedRegistrationRepository.count();

            assertThatThrownBy(() -> txTemplate.execute(status ->
            {
                BlockedRegistration block = createBlock();
                block.setAttendee(null);
                blockedRegistrationRepository.saveAndFlush(block);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class);

            // Assert that the baseline count has NOT increased.
            assertThat(blockedRegistrationRepository.count()).isEqualTo(blockedRegistrationCountBefore);
        }

        @Test
        @DisplayName("Constraint violation rolls back prior save in same transaction")
        @Transactional(propagation = Propagation.NOT_SUPPORTED) // Disable test-managed transaction for this test
        void constraintViolationRollsBackPriorSave()
        {
            // Capture baseline count before attempting the save
            long blockedRegistrationCountBefore = blockedRegistrationRepository.count();

            assertThatThrownBy(() -> txTemplate.execute(status ->
            {
                BlockedRegistration block1 = createBlock();
                BlockedRegistration saved1 = blockedRegistrationRepository.saveAndFlush(block1);
                // Verify that the first block was saved
                assertThat(saved1).isNotNull();
                assertThat(saved1.getId()).isNotNull();

                // Now try to save a second block that will force a NOT NULL violation
                BlockedRegistration block2 = createBlock();
                block2.setEvent(null);
                blockedRegistrationRepository.saveAndFlush(block2);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class);

            // Assert that the baseline count has NOT increased.
            assertThat(blockedRegistrationRepository.count()).isEqualTo(blockedRegistrationCountBefore);
        }
    }

    @Nested
    @DisplayName("Isolation")
    class Isolation
    {
        @Test
        @DisplayName("Rolled back block is not visible in subsequent transaction")
        @Transactional(propagation = Propagation.NOT_SUPPORTED) // Disable test-managed transaction for this test
        void rolledBackBlockNotVisible()
        {
            // Capture baseline count before attempting the save
            long blockedRegistrationCountBefore = blockedRegistrationRepository.count();

            txTemplate.execute(status ->
            {
                BlockedRegistration block = createBlock();
                BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
                assertThat(saved).isNotNull();
                assertThat(saved.getId()).isNotNull();
                assertThat(blockedRegistrationRepository.findById(saved.getId())).isPresent();

                status.setRollbackOnly();
                return null;
            });

            // Assert that the baseline count has NOT increased.
            assertThat(blockedRegistrationRepository.count()).isEqualTo(blockedRegistrationCountBefore);
        }

        @Test
        @DisplayName("Committed block is visible in subsequent transaction")
        @Transactional(propagation = Propagation.NOT_SUPPORTED) // Disable test-managed transaction for this test
        void committedBlockVisible()
        {
            Long blockId = txTemplate.execute(status ->
            {
                BlockedRegistration block = createBlock();
                BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
                assertThat(saved).isNotNull();
                assertThat(saved.getId()).isNotNull();
                return saved.getId();
            });

            Optional<BlockedRegistration> loaded = blockedRegistrationRepository.findById(blockId);
            assertThat(loaded).isPresent();
            assertThat(loaded.get().getReason()).isEqualTo("Spam registration");
        }
    }

    @Nested
    @DisplayName("Query behavior")
    class QueryBehavior
    {
        @Test
        @DisplayName("findAllByEventId eagerly fetches attendee and blockedBy")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void findAllByEventIdFetchesAssociations()
        {
            txTemplate.execute(status ->
            {
                BlockedRegistration block = createBlock();
                BlockedRegistration saved = blockedRegistrationRepository.saveAndFlush(block);
                assertThat(saved).isNotNull();
                assertThat(saved.getId()).isNotNull();
                assertThat(blockedRegistrationRepository.findById(saved.getId())).isPresent();
                return null;
            });

            List<BlockedRegistration> blocks = txTemplate.execute(status ->
            {
                List<BlockedRegistration> found =
                    blockedRegistrationRepository.findAllByEventId(savedEvent.getId());
                found.forEach(b ->
                {
                    assertThat(Hibernate.isInitialized(b.getAttendee())).isTrue();
                    assertThat(Hibernate.isInitialized(b.getBlockedBy())).isTrue();
                });
                return found;
            });

            // Outside any transaction: these would throw LazyInitializationException
            // if the JOIN FETCH is missing.
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).getAttendee().getEmail()).isEqualTo(savedAttendee.getEmail());
            assertThat(blocks.get(0).getBlockedBy().getEmail()).isEqualTo(savedHost.getEmail());
        }
    }
}