package com.ticketproject.webapp.constants;

import java.time.format.DateTimeFormatter;

/**
 * AppConstants is a utility class for holding different constants
 * used throughout this project.
 */
public final class AppConstants
{
    private static final String NO_CONSTRUCTION_MSG = "This is a utility class which is not meant to be instantiated.";
    /**
     * Private constructor. Not meant to be used.
     * @throws UnsupportedOperationException
     */
    private AppConstants()
    {
        throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
    }

    /**
     * The Database inner class holds constants
     * related to the application's SQL database.
     */
    public static final class Database
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private Database()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        /**
         * AddressBookContacts holds constants related to the address book contacts database table.
         */
        public static final class AddressBookContacts
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private AddressBookContacts()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "address_book_contacts";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_ATTENDEE_ID = "attendee_id";
                public static final String COLUMN_EVENT_HOST_ID = "event_host_id";
                public static final String COLUMN_CREATED = "created";
            }
        }

        /**
         * PasswordResetTokens holds constants related to the password reset tokens database table.
         */
        public static final class PasswordResetTokens
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private PasswordResetTokens()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "password_reset_tokens";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_EVENT_HOST_ID = "event_host_id";
                public static final String COLUMN_TOKEN_HASH = "token_hash";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_USED = "used";
                public static final String COLUMN_EXPIRES = "expires";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final int MAX_PASSWORD_RESET_TOKEN_LENGTH = 2 * AppConstants.Crypto.RANDOM_TOKEN_LENGTH_BYTES;
                public static final int TOKEN_DURATION_HOURS = 1;
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String COLUMN_TOKEN_HASH = "VARBINARY(32)";
            }
        }

        /**
         * Attendees holds constants related to the attendees database table.
         */
        public static final class Attendees
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Attendees()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "attendees";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_FIRST_NAME = "first_name";
                public static final String COLUMN_MIDDLE_NAME = "middle_name";
                public static final String COLUMN_LAST_NAME = "last_name";
                public static final String COLUMN_EMAIL = "email";
                public static final String COLUMN_EMAIL_BLIND_INDEX = "email_blind_index";
                public static final String COLUMN_CREATED = "created";
            }

            public static final class MappedByNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private MappedByNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String MAPPED_BY_ATTENDEE = "attendee";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int MAX_NAME_LENGTH = AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH;
                public static final int MAX_EMAIL_LENGTH = AppConstants.Database.EventHosts.Sizes.MAX_EMAIL_LENGTH;
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String COLUMN_EMAIL = "VARBINARY(512)";
                public static final String COLUMN_EMAIL_BLIND_INDEX = "VARBINARY(32)";
            }
        }

        /**
         * BlockedRegistrations holds constants related to the blocked registrations database table.
         */
        public static final class BlockedRegistrations
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private BlockedRegistrations()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "blocked_registrations";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_ATTENDEE_ID = "attendee_id";
                public static final String COLUMN_EVENT_ID = "event_id";
                public static final String COLUMN_BLOCKED_BY = "blocked_by";
                public static final String COLUMN_REASON = "reason";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_REVOKED = "revoked";
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String COLUMN_REASON = "TEXT";
            }
        }

        /**
         * TicketScans holds constants related to the ticket scans database table.
         */
        public static final class TicketScans
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private TicketScans()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "ticket_scans";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_TICKET_ID = "ticket_id";
                public static final String COLUMN_SCANNED_BY = "scanned_by";
                public static final String COLUMN_SCANNED_AT = "scanned_at";
                public static final String COLUMN_DEVICE_INFO = "device_info";
            }

            public static final class Sizes
            {

                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int DEVICE_INFO_LENGTH = 255;
            }
        }

        /**
         * Tickets holds constants related to the tickets database table.
         */
        public static final class Tickets
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Tickets()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "tickets";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_PUBLIC_TOKEN = "public_token";
                public static final String COLUMN_TOKEN_IDENTIFIER = "token_identifier";
                public static final String COLUMN_ATTENDEE_ID = "attendee_id";
                public static final String COLUMN_EVENT_ID = "event_id";
                public static final String COLUMN_PRESENT = "present";
                public static final String COLUMN_INVITATION_STATUS = "invitation_status";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_LAST_UPDATED = "last_updated";
                public static final String COLUMN_DELETED_AT = "deleted_at";
            }

            public static final class MappedByNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private MappedByNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String MAPPED_BY_TICKET = "ticket";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final int PUBLIC_TOKEN_LENGTH = 512;
                public static final int TOKEN_IDENTIFIER_LENGTH = 36;
            }
        }

        /**
         * EventHosts holds constants related to the event hosts database table.
         */
        public static final class EventHosts
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private EventHosts()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "event_hosts";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_FIRST_NAME = "first_name";
                public static final String COLUMN_MIDDLE_NAME = "middle_name";
                public static final String COLUMN_LAST_NAME = "last_name";
                public static final String COLUMN_DATE_OF_BIRTH = "date_of_birth";
                public static final String COLUMN_EMAIL = "email";
                public static final String COLUMN_EMAIL_BLIND_INDEX = "email_blind_index";
                public static final String COLUMN_PASSWORD_HASH = "password_hash";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_LAST_LOGIN = "last_login";
                public static final String COLUMN_LAST_UPDATED = "last_updated";
                public static final String COLUMN_ACTIVE = "active";
                public static final String COLUMN_VERIFIED = "verified";
                public static final String COLUMN_VERIFICATION_KEY_HASH = "verification_key_hash";
                public static final String COLUMN_VERIFICATION_EXPIRES = "verification_expires";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int MAX_NAME_LENGTH = 100;
                public static final int MIN_PASSWORD_LENGTH = 12;
                public static final int MAX_PASSWORD_LENGTH = 128;
                public static final int MAX_EMAIL_LENGTH = 254;
                public static final int VERIFICATION_KEY_LENGTH = 36;
                public static final int VERIFICATION_DURATION_HOURS = 1;
                public static final int MAX_ACCOUNT_VERIFICATION_TOKEN_LENGTH = 2 * AppConstants.Crypto.RANDOM_TOKEN_LENGTH_BYTES;
            }

            public static final class MappedByNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private MappedByNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String MAPPED_BY_EVENT_HOST = "eventHost";
                public static final String MAPPED_BY_BLOCKED_BY = "blockedBy";
                public static final String MAPPED_BY_SCANNED_BY = "scannedBy";
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String COLUMN_DATE_OF_BIRTH = "VARBINARY(128)";
                public static final String COLUMN_EMAIL = "VARBINARY(512)";
                public static final String COLUMN_EMAIL_BLIND_INDEX = "VARBINARY(32)";
                public static final String COLUMN_PASSWORD_HASH = "VARCHAR(255)";
                public static final String COLUMN_VERIFICATION_KEY_HASH = "VARBINARY(32)";
                public static final String EMAIL_ADDRESS_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
            }
        }

        /**
         * Events holds constants related to the events database table.
         */
        public static final class Events
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Events()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "events";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_PUBLIC_ID = "public_id";
                public static final String COLUMN_EVENT_HOST_ID = "event_host_id";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_LAST_UPDATED = "last_updated";
                public static final String COLUMN_NAME = "name";
                public static final String COLUMN_DESCRIPTION = "description";
                public static final String COLUMN_EVENT_ADDRESS_ID = "event_address_id";
                public static final String COLUMN_START_DATE_TIME = "start_date_time";
                public static final String COLUMN_END_DATE_TIME = "end_date_time";
                public static final String COLUMN_EVENT_TYPE = "event_type";
                public static final String COLUMN_MAX_ATTENDEES = "max_attendees";
                public static final String COLUMN_REGISTRATION_STATUS = "registration_status";
                public static final String COLUMN_EVENT_STATUS = "event_status";
            }

            public static final class MappedByNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private MappedByNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String MAPPED_BY_EVENT = "event";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int PUBLIC_ID_LENGTH = 36;
                public static final int MIN_EVENT_DURATION_MINUTES = 30;
                public static final int NAME_LENGTH = 255;
                public static final int MIN_ATTENDEES = 1;
                public static final int DESCRIPTION_LENGTH = 5000;
                public static final int ADDRESS_LINE_LENGTH = 255;
                public static final int CITY_LENGTH = 100;
                public static final int STATE_LENGTH = 100;
                public static final int POSTAL_CODE_LENGTH = 20;
                public static final int COUNTRY_LENGTH = 100;
                public static final int LATITUDE_PRECISION = 10;
                public static final int LATITUDE_SCALE = 7;
                public static final int LONGITUDE_PRECISION = 10;
                public static final int LONGITUDE_SCALE = 7;
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String COLUMN_DESCRIPTION = "TEXT";
            }
        }

        /**
         * EventAddresses holds constants related to the event addresses database table.
         */
        public static final class EventAddresses
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private EventAddresses()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "event_addresses";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_LAST_UPDATED = "last_updated";
                public static final String COLUMN_ADDRESS_LINE_1 = "address_line_1";
                public static final String COLUMN_ADDRESS_LINE_2 = "address_line_2";
                public static final String COLUMN_CITY = "city";
                public static final String COLUMN_STATE = "state";
                public static final String COLUMN_POSTAL_CODE = "postal_code";
                public static final String COLUMN_COUNTRY = "country";
                public static final String COLUMN_LATITUDE = "latitude";
                public static final String COLUMN_LONGITUDE = "longitude";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int NAME_LENGTH = 255;
                public static final int ADDRESS_LINE_LENGTH = 255;
                public static final int CITY_LENGTH = 100;
                public static final int STATE_LENGTH = 100;
                public static final int POSTAL_CODE_LENGTH = 20;
                public static final int COUNTRY_LENGTH = 100;
                public static final int LATITUDE_PRECISION = 10;
                public static final int LATITUDE_SCALE = 7;
                public static final int LONGITUDE_PRECISION = 10;
                public static final int LONGITUDE_SCALE = 7;
            }

            public static final class MappedByNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private MappedByNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String MAPPED_BY_EVENT_ADDRESS = "eventAddress";
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String COLUMN_ADDRESS_LINE = "VARBINARY(1024)";
                public static final String COLUMN_CITY = "VARBINARY(1024)";
                public static final String COLUMN_STATE = "VARBINARY(1024)";
                public static final String COLUMN_POSTAL_CODE = "VARBINARY(1024)";
                public static final String COLUMN_COUNTRY = "VARBINARY(1024)";
                public static final String COLUMN_LATITUDE = "VARBINARY(1024)";
                public static final String COLUMN_LONGITUDE = "VARBINARY(1024)";
            }
        }

        /**
         * Sessions holds constants related to the sessions database table.
         */
        public static final class Sessions
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Sessions()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "sessions";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_EVENT_HOST_ID = "event_host_id";
                public static final String COLUMN_TOKEN_HASH = "token_hash";
                public static final String COLUMN_CLIENT_TYPE = "client_type";
                public static final String COLUMN_IP_ADDRESS = "ip_address";
                public static final String COLUMN_USER_AGENT = "user_agent";
                public static final String COLUMN_CREATED = "created";
                public static final String COLUMN_EXPIRES = "expires";
                public static final String COLUMN_REVOKED = "revoked";
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final int TOKEN_HASH_LENGTH = 64;
                public static final int IP_ADDRESS_LENGTH = 45;
                public static final int USER_AGENT_LENGTH = 512;
                /**
                 * The login session duration (AKA the expiration duration for JWTs)
                 * in hours.
                 */
                public static final int LOGIN_SESSION_DURATION_HOURS = 6;
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String COLUMN_TOKEN_HASH = "VARBINARY(32)";
            }
        }

        /**
         * EventSigningKeys holds constants related to the event signing keys database table.
         */
        public static final class EventSigningKeys
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private EventSigningKeys()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class TableNames
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private TableNames()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final String TABLE_NAME = "event_signing_keys";
                public static final String COLUMN_ID = "id";
                public static final String COLUMN_EVENT_ID = "event_id";
                public static final String COLUMN_PRIVATE_KEY = "private_key";
                public static final String COLUMN_PUBLIC_KEY = "public_key";
                public static final String COLUMN_CREATED = "created";
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final String COLUMN_PRIVATE_KEY = "VARBINARY(256)";
                public static final String COLUMN_PUBLIC_KEY = "VARBINARY(256)";
            }
        }
    }

    /**
     * The Crypto inner class holds constants related to things such as:
     * - Choice of encryption algorithm
     * - Choice of hash algorithm
     * - Key lengths
     * - Hash lengths
     * - How complex data such as dates and times should be
     *   represented in the database during encryption/decryption
     */
    public static final class Crypto
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private Crypto()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        public static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
        public static final String SECRET_KEY_ALGORITHM = "AES";
        public static final String HASH_ALGORITHM = "SHA-256";
        public static final String PUBLIC_PRIVATE_KEY_ALGORITHM = "Ed25519";
        public static final String TICKET_SIGNATURE_ALGORITHM = "Ed25519";
        public static final String TICKET_PAYLOAD_DECODED_SEPARATOR = "|";
        public static final String TICKET_PAYLOAD_DECODED_SEPARATOR_REGEX = "\\|";
        public static final String TICKET_PAYLOAD_ENCODED_SEPARATOR = ".";
        public static final String TICKET_PAYLOAD_ENCODED_SEPARATOR_REGEX = "\\.";
        public static final int GCM_IV_LENGTH_BYTES = 12;
        public static final int GCM_TAG_LENGTH_BITS = 128;
        public static final int HASH_LENGTH_BYTES = 32;
        public static final int RANDOM_TOKEN_LENGTH_BYTES = 32;
        public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
        public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        public static final String BLIND_INDEX_KEY_ALGORITHM = "HmacSHA256";
    }

    /**
     * The DTO inner class holds constants used in DTO definitions for
     * requests and responses to/from API routes used throughout this application.
     */
    public static final class DTO
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private DTO()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        public static final class EventHosts
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private EventHosts()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int MIN_AGE_YEARS = 18;
            }

            public static final class Definitions
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Definitions()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
            }
        }

        public static final class PasswordResetTokens
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private PasswordResetTokens()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

            }
        }

        public static final class Events
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Events()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
                public static final int MAX_GET_EVENTS_COUNT = 500;
            }
        }

        public static final class EventAddresses
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private EventAddresses()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }
            }
        }

        public static final class Tickets
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Tickets()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final class Sizes
            {
                /**
                 * Private constructor. Not meant to be used.
                 * @throws UnsupportedOperationException
                 */
                private Sizes()
                {
                    throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
                }

                public static final int MAX_MESSAGE_LENGTH = 5000;
            }
        }
    }

    /**
     * The Jwt inner class holds constants related to JSON Web Token
     * generation and validation.
     */
    public static final class Jwt
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private Jwt()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        /**
         * The issuer claim value for JWTs generated by this application.
         */
        public static final String ISSUER = "ScanMeIn.online";

        /**
         * The signing algorithm used for JWT digital signatures.
         */
        public static final String SIGNING_ALGORITHM = "HmacSHA256";

        public static final class Filter
        {
            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Filter()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }
            /**
             * The request attribute key under which the authenticated EventHost is stored.
             */
            public static final String AUTHENTICATED_EVENT_HOST_ATTRIBUTE = "authenticatedEventHost";

            /**
             * The request attribute key under which the authenticated Session is stored.
             */
            public static final String AUTHENTICATED_SESSION_ATTRIBUTE = "authenticatedSession";

            /**
             * The label of the HTTP header which includes the JWT sent by client devices.
             */
            public static final String AUTHORIZATION_HEADER = "Authorization";

            /**
             * The prefix that comes just before the JWT that gets embedded
             * by client devices into the Authorization HTTP header of requests
             * which they send to this application's API routes.
             */
            public static final String BEARER_PREFIX = "Bearer ";
        }
    }

    /**
     * The Email inner class holds constants used by this
     * application's services to send out emails to users.
     */
    public static final class Email
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private Email()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        /**
         * Default sender address used when {@code app.config.mail.from-address}
         * (env {@code MAIL_FROM_ADDRESS}) is not set, e.g. local/Mailpit use.
         * On Amazon SES this MUST be overridden with a verified identity/domain.
         */
        public static final String FROM_ADDRESS = "noreply@ticketproject.local";
        public static final String CHARACTER_ENCODING = "UTF-8";

        public static final class Subjects
        {

            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Subjects()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final String PASSWORD_RESET_EMAIL = AppConstants.Project.PROJECT_NAME + " Account Password Reset Request";
            public static final String VERIFICATION_EMAIL = "Verify Your " + AppConstants.Project.PROJECT_NAME + " Account";
            public static final String TICKET_EMAIL = "Your " + AppConstants.Project.PROJECT_NAME + " Event Ticket";
            public static final String INVITATION_EMAIL = AppConstants.Project.PROJECT_NAME + " Event Invitation";
            public static final String INVITATION_RESPONSE_EMAIL = "Invitation Response";
            public static final String EVENT_ADDRESS_CHANGED_EMAIL = "Event Address Changed";
            public static final String EVENT_DATES_CHANGED_EMAIL = "Event Dates Changed";
            public static final String EVENT_DESCRIPTION_CHANGED_EMAIL = "Event Description Changed";
            public static final String EVENT_MAX_ATTENDEES_CHANGED_EMAIL = "Event Attendance Cap Changed";
            public static final String EVENT_NAME_CHANGED_EMAIL = "Event Name Changed";
            public static final String EVENT_TYPE_CHANGED_EMAIL = "Event Type Changed";
        }

        public static final class Templates
        {

            /**
             * Private constructor. Not meant to be used.
             * @throws UnsupportedOperationException
             */
            private Templates()
            {
                throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
            }

            public static final String VERIFICATION_EMAIL = "email/verification-email";
            public static final String PASSWORD_RESET_EMAIL = "email/password-reset-email";
            public static final String TICKET_EMAIL = "email/ticket-email";
            public static final String INVITATION_EMAIL = "email/invitation-email";
            public static final String INVITATION_RESPONSE_WITH_MESSAGE_EMAIL = "email/invitation-response-with-message-email";
            public static final String INVITATION_RESPONSE_NO_MESSAGE_EMAIL = "email/invitation-response-no-message-email";
            public static final String EVENT_ADDRESS_CHANGED_EMAIL = "email/event-address-changed-email";
            public static final String EVENT_DATES_CHANGED_EMAIL = "email/event-dates-changed-email";
            public static final String EVENT_DESCRIPTION_CHANGED_EMAIL = "email/event-description-changed-email";
            public static final String EVENT_MAX_ATTENDEES_CHANGED_EMAIL = "email/event-max-attendees-changed-email";
            public static final String EVENT_NAME_CHANGED_EMAIL = "email/event-name-changed-email";
            public static final String EVENT_TYPE_CHANGED_EMAIL = "email/event-type-changed-email";
        }
    }

    /**
     * The Project inner class holds constants related to describing the application,
     * such as the public name of the application used when communicating to users.
     */
    public static final class Project
    {

        /**
         * Private constructor. Not meant to be used.
         * @throws UnsupportedOperationException
         */
        private Project()
        {
            throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
        }

        public static final String PROJECT_NAME = "ScanMeIn.online";
    }
}
