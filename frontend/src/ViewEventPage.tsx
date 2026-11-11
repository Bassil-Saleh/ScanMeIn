import { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';

/** Shape of a single event returned by GET /api/v1/events/{publicId}. */
interface EventInfo {
    publicId: string;
    name: string;
    description: string;
    startDateTime: string;
    endDateTime: string;
    eventType: string;
    eventStatus: string;
    numberOfRegisteredAttendees: number;
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
 * ViewEventPage is for implementing the page that lets a user
 * view an event's page given the event's public ID.
 * @returns JSX for the site's view event page
 */
export function ViewEventPage() {
    // TODO: If the event is in the Published status, show a button
    //       saying "Resend Ticket" under the event's information.

    // TODO: Clicking "Resend Ticket" should show a form containing:
    //       - A sentence telling them to enter their email address
    //         so that their ticket can be resent to them.
    //       - A text input field for the attendee to enter their email address.
    //       - A "Submit" button to send a request to the API route
    //         POST /api/v1/attendees/resends/{publicId}.
    //         The "Submit" button should only work when the
    //         text input field is non-empty and not blank.
    //       - A "Cancel" button to close the form.


    // TODO: After the user submits their email address through the
    //       "Resend Ticket" form, show a message to the user
    //       based on the request's response.

    const { publicId } = useParams<{ publicId: string }>();
    const { authFetch } = useAuth();

    const [event, setEvent] = useState<EventInfo | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');

    /**
     * Fetches the event's details using its public ID via
     * GET /api/v1/events/{publicId}. Authentication is optional:
     * published and canceled events are visible to everyone, while
     * draft events are only visible to the event host who created
     * them (any other requester receives a 404). The JWT is attached
     * (if any) so that a logged in event host can preview their own
     * draft events.
     */
    const fetchEvent = async () => {
        if (!publicId) return;
        setIsLoading(true);
        setLoadError('');
        try {
            const response = await authFetch(`/api/v1/events/${encodeURIComponent(publicId)}`);
            const data = await response.json();

            if (response.ok) {
                setEvent(data);
            } else {
                setLoadError(data.message || 'Failed to load the event.');
            }
        } catch {
            setLoadError('An unexpected error occurred while loading the event.');
        } finally {
            setIsLoading(false);
        }
    };

    // Fetch the event on mount (and refetch if the authentication state changes).
    useEffect(() => {
        fetchEvent();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [publicId, authFetch]);

    /**
     * Formats an ISO date-time string into a human-readable format.
     */
    const formatDateTime = (isoString: string): string => {
        try {
            return new Date(isoString).toLocaleString(undefined, {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
            });
        } catch {
            return isoString;
        }
    };

    /**
     * Builds a multi-line address display from the event data.
     */
    const getAddressLines = (): string[] => {
        if (!event) return [];
        const lines: string[] = [event.addressLine1];
        if (event.addressLine2) {
            lines.push(event.addressLine2);
        }
        lines.push(`${event.city}, ${event.state} ${event.postalCode}`);
        lines.push(event.country);
        return lines;
    };

    /**
     * Determines whether registration for the event is closed.
     * Since the API does not expose an explicit registration
     * status field, registration is treated as closed once the
     * event's end date/time has passed. Canceled events are
     * always closed for registration.
     */
    const isRegistrationClosed = (): boolean => {
        if (!event) return true;
        return event.eventStatus === 'CANCELED' ||
            new Date(event.endDateTime).getTime() <= Date.now();
    };

    if (isLoading) {
        return (
            <main className="status-page">
                <div className="spinner spinner--lg" aria-label="Loading event" />
                <h2 className="status-page__title">Loading Event...</h2>
            </main>
        );
    }

    if (loadError || !event) {
        return (
            <main className="status-page">
                <div className="status-page__icon" aria-hidden="true">🔍</div>
                <h2 className="status-page__title">Event Not Found</h2>
                <p className="status-page__message">
                    {loadError || 'The event you are looking for does not exist or may have been removed.'}
                </p>
                <Link to="/" className="btn btn--outline btn--lg">
                    Back to Home
                </Link>
            </main>
        );
    }

    const registrationClosed = isRegistrationClosed();
    const isDraft = event.eventStatus === 'DRAFT';
    const isCanceled = event.eventStatus === 'CANCELED';

    return (
        <main className="page-container">
            <div className="view-event">
                <div className="view-event__header">
                    <div>
                        <span className="view-event__type-badge">
                            {event.eventType === 'PUBLIC' ? 'Public Event' : 'Private Event'}
                        </span>
                        {isDraft && (
                            <span className="view-event__type-badge">Draft</span>
                        )}
                        {isCanceled && (
                            <span className="view-event__type-badge">Canceled</span>
                        )}
                        <h1 className="view-event__title">{event.name}</h1>
                    </div>

                    {/* Only public, non-draft, non-canceled events show a Register button. */}
                    {event.eventType === 'PUBLIC' && !isDraft && !isCanceled && (
                        <div className="view-event__register">
                            <Link
                                to={registrationClosed ? '#' : `/events/${event.publicId}/register`}
                                className={`btn btn--accent btn--lg${registrationClosed ? ' btn--disabled' : ''}`}
                                aria-disabled={registrationClosed}
                                title={registrationClosed ? 'Registration is closed' : undefined}
                                onClick={(e) => {
                                    if (registrationClosed) {
                                        e.preventDefault();
                                    }
                                }}
                            >
                                Register
                            </Link>
                        </div>
                    )}
                </div>

                <div className="view-event__body">
                    {isDraft && (
                        <div className="alert alert--info" role="alert">
                            This event is still a draft. Only you (the event host who
                            created it) can see it until you publish it.
                        </div>
                    )}

                    {isCanceled && (
                        <div className="alert alert--warning" role="alert">
                            This event has been canceled by its host. Registration is closed.
                        </div>
                    )}

                    <section className="view-event__section">
                        <h2 className="view-event__section-title">About This Event</h2>
                        <p className="view-event__description">{event.description}</p>
                    </section>

                    <section className="view-event__section">
                        <h2 className="view-event__section-title">Date & Time</h2>
                        <p>
                            <strong>Starts:</strong> {formatDateTime(event.startDateTime)}
                        </p>
                        <p>
                            <strong>Ends:</strong> {formatDateTime(event.endDateTime)}
                        </p>
                    </section>

                    <section className="view-event__section">
                        <h2 className="view-event__section-title">Location</h2>
                        {getAddressLines().map((line, index) => (
                            <p key={index}>{line}</p>
                        ))}
                        {event.latitude !== null && event.longitude !== null && (
                            <p className="view-event__coordinates">
                                Coordinates: {event.latitude}, {event.longitude}
                            </p>
                        )}
                    </section>

                    <section className="view-event__section">
                        <h2 className="view-event__section-title">Capacity</h2>
                        <p>Current / maximum attendees: {event.numberOfRegisteredAttendees} / {event.maxAttendees}</p>
                    </section>
                </div>
            </div>
        </main>
    );
}