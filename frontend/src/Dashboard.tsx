import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';

/** The possible statuses an event can have, as returned by the API. */
type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELED';

/** Display label and badge CSS class for each event status. */
const EVENT_STATUS_BADGES: Record<EventStatus, { label: string; className: string }> = {
    DRAFT: { label: 'Draft', className: 'event-status-badge event-status-badge--draft' },
    PUBLISHED: { label: 'Published', className: 'event-status-badge event-status-badge--published' },
    CANCELED: { label: 'Canceled', className: 'event-status-badge event-status-badge--canceled' },
};

/**
 * Returns the display label and badge CSS class for the given
 * event status, falling back to the raw status value if the
 * status is not recognized.
 * @param status the event's status
 * @returns the label and CSS class to use for the status badge
 */
function getEventStatusBadge(status: EventStatus): { label: string; className: string } {
    return EVENT_STATUS_BADGES[status] ?? { label: status, className: 'event-status-badge event-status-badge--draft' };
}

/** Shape of a single event returned by GET /api/v1/events. */
interface EventInfo {
    publicId: string;
    name: string;
    description: string;
    startDateTime: string;
    endDateTime: string;
    eventType: string;
    eventStatus: EventStatus;
    maxAttendees: number;
    addressLine1: string;
    addressLine2: string | null;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    latitude: number | null;
    longitude: number | null;
}

/**
 * Dashboard is for implementing a dashboard page to let a
 * logged in event host navigate to and view different pages.
 * The dashboard should remain visible even when different pages
 * referenced by the dashboard become visible (i.e. view profile,
 * list of events, etc.).
 * @returns JSX for the site's dashboard
 */
export function Dashboard() {
    const { isLoggedIn, authFetch } = useAuth();
    const navigate = useNavigate();

    const [events, setEvents] = useState<EventInfo[]>([]);
    const [isLoadingEvents, setIsLoadingEvents] = useState(true);
    const [eventsError, setEventsError] = useState('');
    // Message showing the result of a PATCH /api/v1/events/status request
    // (i.e. publishing or canceling an event from the actions menu).
    const [statusUpdateMessage, setStatusUpdateMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

    // If the user is not logged in, redirect to the home page.
    useEffect(() => {
        if (!isLoggedIn) {
            navigate('/', { replace: true });
        }
    }, [isLoggedIn, navigate]);

    // Fetch the event host's events on mount.
    useEffect(() => {
        if (!isLoggedIn) return;

        const fetchEvents = async () => {
            setIsLoadingEvents(true);
            setEventsError('');
            try {
                const response = await authFetch('/api/v1/events?count=500', {
                    method: 'GET',
                });
                const data = await response.json();

                if (response.ok && data.events) {
                    setEvents(data.events);
                } else {
                    setEventsError(data.message || 'Failed to load events.');
                }
            } catch {
                setEventsError('An unexpected error occurred while loading events.');
            } finally {
                setIsLoadingEvents(false);
            }
        };

        fetchEvents();
    }, [isLoggedIn, authFetch]);

    /**
     * Updates the eventStatus of the event with the given public ID in the
     * local events list and shows a success message. Called by an
     * EventActionsMenu after a successful PATCH /api/v1/events/status request.
     * @param publicId the event's public ID
     * @param newStatus the event's new status
     * @param message the success message returned by the API
     */
    const handleEventStatusChange = (publicId: string, newStatus: EventStatus, message: string) => {
        setEvents((previous) =>
            previous.map((event) =>
                event.publicId === publicId ? { ...event, eventStatus: newStatus } : event
            )
        );
        setStatusUpdateMessage({ type: 'success', text: message });
    };

    /**
     * Shows an error message. Called by an EventActionsMenu when
     * a request to PATCH /api/v1/events/status has failed.
     * @param message the error message to show
     */
    const handleEventStatusError = (message: string) => {
        setStatusUpdateMessage({ type: 'error', text: message });
    };

    /**
     * Formats an ISO date-time string into a human-readable format.
     */
    const formatDateTime = (isoString: string): string => {
        try {
            return new Date(isoString).toLocaleString(undefined, {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
            });
        } catch {
            return isoString;
        }
    };

    return (
        <main className="page-container">
            <div className="dashboard">
                <div className="dashboard__header">
                    <h1 className="dashboard__title">Dashboard</h1>
                </div>

                {/* Navigation links */}
                <nav className="dashboard__nav">
                    <Link to="/scan-tickets" className="dashboard__nav-link">
                        📷 Start Scanning Tickets
                    </Link>
                    <Link to="/profile" className="dashboard__nav-link">
                        👤 View Profile
                    </Link>
                    <Link to="/create-event" className="dashboard__nav-link">
                        ➕ Create Event
                    </Link>
                    <Link to="/scanned-tickets" className="dashboard__nav-link">
                        🎫 Scanned Tickets
                    </Link>
                </nav>

                {/* Events section */}
                <section className="dashboard__section">
                    <h2 className="dashboard__section-title">Your Events</h2>

                    {statusUpdateMessage && (
                        <div
                            className={`alert ${statusUpdateMessage.type === 'success' ? 'alert--success' : 'alert--error'}`}
                            role="alert"
                        >
                            {statusUpdateMessage.text}
                        </div>
                    )}

                    {isLoadingEvents && (
                        <div className="dashboard__empty">
                            <div className="spinner" aria-label="Loading events" />
                            <p>Loading your events...</p>
                        </div>
                    )}

                    {eventsError && !isLoadingEvents && (
                        <div className="alert alert--error" role="alert">
                            {eventsError}
                        </div>
                    )}

                    {!isLoadingEvents && !eventsError && events.length === 0 && (
                        <div className="dashboard__empty">
                            <p>You haven't created any events yet.</p>
                        </div>
                    )}

                    {!isLoadingEvents && !eventsError && events.length > 0 && (
                        <div>
                            {events.map((event) => {
                                const statusBadge = getEventStatusBadge(event.eventStatus);
                                return (
                                    <div key={event.publicId} className="profile__field">
                                        <div className="profile__field-info">
                                            <div className="profile__field-label">
                                                {event.eventType} &middot; {formatDateTime(event.startDateTime)}
                                            </div>
                                            <div className="profile__field-value">
                                                <span className={statusBadge.className}>
                                                    {statusBadge.label}
                                                </span>
                                                {event.name}
                                            </div>
                                        </div>
                                        <EventActionsMenu
                                            event={event}
                                            onStatusChange={handleEventStatusChange}
                                            onStatusError={handleEventStatusError}
                                        />
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </section>
            </div>
        </main>
    );
}

/** Props accepted by the per-event actions drop-down menu. */
interface EventActionsMenuProps {
    event: EventInfo;
    /** Called after a successful PATCH /api/v1/events/status request. */
    onStatusChange: (publicId: string, newStatus: EventStatus, message: string) => void;
    /** Called when a request to PATCH /api/v1/events/status fails. */
    onStatusError: (message: string) => void;
}

/**
 * EventActionsMenu is a drop-down menu providing the actions available
 * for a single event on the dashboard: viewing the event, editing the
 * event, creating an invitation for private events, publishing the event,
 * and canceling the event. Publishing and canceling each open a confirmation
 * dialog before sending a request to PATCH /api/v1/events/status. The menu
 * is closed when the user clicks outside of it or presses the Escape key.
 * @param props the component's props
 * @returns JSX for the event's actions drop-down menu
 */
function EventActionsMenu({ event, onStatusChange, onStatusError }: EventActionsMenuProps) {
    // TODO: Add an additional "Manage Registrations" action to
    //       take the user to the "Manage Event Registrations" page
    //       at ManageEventRegistrationsPage.tsx

    const { authFetch } = useAuth();

    const [isOpen, setIsOpen] = useState(false);
    // Which confirmation dialog (if any) is currently open.
    const [confirmAction, setConfirmAction] = useState<'publish' | 'cancel' | null>(null);
    const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
    const menuRef = useRef<HTMLDivElement | null>(null);

    // Close the menu when the user clicks outside of it or presses Escape.
    useEffect(() => {
        if (!isOpen) return;

        const handleMouseDown = (mouseEvent: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(mouseEvent.target as Node)) {
                setIsOpen(false);
            }
        };
        const handleKeyDown = (keyEvent: KeyboardEvent) => {
            if (keyEvent.key === 'Escape') {
                setIsOpen(false);
            }
        };

        document.addEventListener('mousedown', handleMouseDown);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('mousedown', handleMouseDown);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen]);

    /**
     * Sends a request to PATCH /api/v1/events/status to change the
     * event's status to the given new status, and reports the result
     * via the onStatusChange (on success) or onStatusError (on failure)
     * callback.
     * @param newStatus the event's new status (PUBLISHED or CANCELED)
     */
    const handleStatusUpdate = async (newStatus: 'PUBLISHED' | 'CANCELED') => {
        setIsUpdatingStatus(true);
        try {
            const response = await authFetch('/api/v1/events/status', {
                method: 'PATCH',
                body: JSON.stringify({
                    publicId: event.publicId,
                    status: newStatus,
                }),
            });
            const data = await response.json();

            if (response.ok) {
                onStatusChange(
                    event.publicId,
                    newStatus,
                    data.message || 'Event status has been updated.'
                );
            } else {
                onStatusError(data.message || 'Failed to update the event status.');
            }
        } catch {
            onStatusError('An unexpected error occurred while updating the event status.');
        } finally {
            setIsUpdatingStatus(false);
            setConfirmAction(null);
        }
    };

    return (
        <div className="dashboard__event-actions" ref={menuRef}>
            <button
                type="button"
                className="btn btn--outline btn--sm"
                aria-haspopup="menu"
                aria-expanded={isOpen}
                onClick={() => setIsOpen((previous) => !previous)}
            >
                Actions &#9662;
            </button>
            {isOpen && (
                <div
                    className="dashboard__event-menu"
                    role="menu"
                    aria-label={`Actions for ${event.name}`}
                >
                    <Link
                        to={`/events/${event.publicId}`}
                        className="dashboard__event-menu__item"
                        role="menuitem"
                        onClick={() => setIsOpen(false)}
                    >
                        View Event
                    </Link>
                    <Link
                        to={`/events/${event.publicId}/edit`}
                        className="dashboard__event-menu__item"
                        role="menuitem"
                        onClick={() => setIsOpen(false)}
                    >
                        Edit Event
                    </Link>
                    {event.eventType === 'PRIVATE' && (
                        <Link
                            to={`/events/${event.publicId}/invite`}
                            className="dashboard__event-menu__item"
                            role="menuitem"
                            onClick={() => setIsOpen(false)}
                        >
                            Create Invitation
                        </Link>
                    )}
                    {event.eventStatus !== 'PUBLISHED' && event.eventStatus !== 'CANCELED' && (
                        <button
                            type="button"
                            className="dashboard__event-menu__item dashboard__event-menu__item--button"
                            role="menuitem"
                            onClick={() => {
                                setIsOpen(false);
                                setConfirmAction('publish');
                            }}
                        >
                            Publish Event
                        </button>
                    )}
                    {event.eventStatus !== 'CANCELED' && (
                        <button
                            type="button"
                            className="dashboard__event-menu__item dashboard__event-menu__item--button"
                            role="menuitem"
                            onClick={() => {
                                setIsOpen(false);
                                setConfirmAction('cancel');
                            }}
                        >
                            Cancel Event
                        </button>
                    )}
                </div>
            )}

            {/* Publish Event confirmation dialog */}
            {confirmAction === 'publish' && (
                <div
                    className="modal-overlay"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby={`publish-confirm-title-${event.publicId}`}
                >
                    <div className="modal">
                        <h2 id={`publish-confirm-title-${event.publicId}`} className="modal__title">
                            Publish Event?
                        </h2>
                        <p className="modal__message">
                            Are you sure you want to publish "{event.name}"? Once an
                            event is published, it cannot be changed back into a draft,
                            and its type (public event or private event) cannot be
                            changed either.
                        </p>
                        <div className="modal__actions">
                            <button
                                type="button"
                                className="btn btn--primary"
                                onClick={() => handleStatusUpdate('PUBLISHED')}
                                disabled={isUpdatingStatus}
                            >
                                {isUpdatingStatus ? 'Publishing...' : 'Publish Event'}
                            </button>
                            <button
                                type="button"
                                className="btn btn--ghost"
                                onClick={() => setConfirmAction(null)}
                                disabled={isUpdatingStatus}
                            >
                                Go Back
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Cancel Event confirmation dialog */}
            {confirmAction === 'cancel' && (
                <div
                    className="modal-overlay"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby={`cancel-confirm-title-${event.publicId}`}
                >
                    <div className="modal">
                        <h2 id={`cancel-confirm-title-${event.publicId}`} className="modal__title">
                            Cancel Event?
                        </h2>
                        <p className="modal__message">
                            Are you sure you want to cancel "{event.name}"? Once an
                            event is canceled, the cancellation cannot be undone,
                            people will no longer be able to register for the event
                            or receive invitations to it, and no further edits to
                            the event will be possible.
                        </p>
                        <div className="modal__actions">
                            <button
                                type="button"
                                className="btn btn--danger"
                                onClick={() => handleStatusUpdate('CANCELED')}
                                disabled={isUpdatingStatus}
                            >
                                {isUpdatingStatus ? 'Canceling...' : 'Cancel Event'}
                            </button>
                            <button
                                type="button"
                                className="btn btn--ghost"
                                onClick={() => setConfirmAction(null)}
                                disabled={isUpdatingStatus}
                            >
                                Go Back
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}