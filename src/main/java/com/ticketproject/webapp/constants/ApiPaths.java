package com.ticketproject.webapp.constants;

/**
 * ApiPaths is a utility class for holding String constants to
 * construct various API routes used throughout this project.
 */
public final class ApiPaths
{
    private static final String NO_CONSTRUCTION_MSG = "This is a utility class which is not meant to be instantiated.";
    /**
     * Private constructor. Not meant to be used.
     * @throws UnsupportedOperationException
     */
    private ApiPaths()
    {
        throw new UnsupportedOperationException(NO_CONSTRUCTION_MSG);
    }

    public static final String API = "/api";
    public static final String VERSION = "/v1";
    public static final String BASE = API + VERSION;

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

        public static final String ROOT = "/event-hosts";
        public static final String VERIFICATION = "/verification";
        public static final String PROFILE = "/profile";
        public static final String FULL_NAME = "/name";
        public static final String EMAIL = "/email";
        public static final String PASSWORD = "/password";
    }

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

        public static final String ROOT = "/sessions";
        public static final String LOGIN = "/login";
        public static final String LOGOUT = "/logout";
        public static final String LOGOUT_ALL_DEVICES = "/logout-all-devices";
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

        public static final String ROOT = "/password-reset-tokens";
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

        public static final String ROOT = "/events";
        public static final String BY_PUBLIC_ID = "/{publicId}";
        public static final String ADDRESS = "/address";
        public static final String NAME = "/name";
        public static final String DESCRIPTION = "/description";
        public static final String DATES = "/dates";
        public static final String MAX_ATTENDEES = "/max-attendees";
        public static final String CHANGE_TO_PUBLIC = "/change-to-public";
        public static final String CHANGE_TO_PRIVATE = "/change-to-private";
        public static final String STATUS = "/status";
    }

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

        public static final String ROOT = "/attendees";
        public static final String REGISTRATION = "/registration";
        public static final String INVITATION = "/invitation";
        public static final String RESENDS = "/resends";
        public static final String BY_EVENT_PUBLIC_ID = "/{publicId}";
    }

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

        public static final String ROOT = "/ticket-scans";
        public static final String BY_EVENT_PUBLIC_ID = "/{publicId}";
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

        public static final String ROOT = "/tickets";
        public static final String INVITATION = "/invitation";
        public static final String BY_EVENT_PUBLIC_ID = "/{publicId}";
    }
}
