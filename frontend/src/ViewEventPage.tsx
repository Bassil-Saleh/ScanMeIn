import { useState, useEffect, type SubmitEvent } from 'react';
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
    const { publicId } = useParams<{ publicId: string }>();
    const { authFetch } = useAuth();

    const [event, setEvent] = useState<EventInfo | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');

    // "Resend Ticket" form state
    const [showResendForm, setShowResendForm] = useState(false);
    const [resendEmail, setResendEmail] = useState('');
    const [isResending, setIsResending] = useState(false);
    const [resendSuccessMessage, setResendSuccessMessage] = useState('');
    const [resendErrorMessage, setResendErrorMessage] = useState('');

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

    // Close the "Resend Ticket" form and clear its state when the
    // user navigates to a different event.
    useEffect(() => {
        setShowResendForm(false);
        setResendEmail('');
        setResendSuccessMessage('');
        setResendErrorMessage('');
    }, [publicId]);

    /**
     * Handles the "Resend Ticket" form submission. Calls
     * POST /api/v1/attendees/resends/{publicId} with the attendee's
     * email address so that their ticket for this event is resent
     * to them. The API responds with the same generic message
     * whether or not a registration exists for the email address,
     * so this page never reveals whether someone is registered.
     */
    const handleResendSubmit = async (e: SubmitEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (!publicId) return;

        const email = resendEmail.trim();
        if (email === '') return;

        setResendSuccessMessage('');
        setResendErrorMessage('');
        setIsResending(true);

        try {
            const response = await authFetch(
                `/api/v1/attendees/resends/${encodeURIComponent(publicId)}`,
                {
                    method: 'POST',
                    body: JSON.stringify({ email }),
                }
            );
            // The response body may not be JSON (e.g. if the request is
            // intercepted by a proxy), so fall back to an empty object.
            const data = await response.json().catch(() => ({}));

            if (response.ok) {
                setResendSuccessMessage(
                    data.message ||
                    'If a registration exists for that email address, then a ticket has been sent to it.'
                );
                // Close the form and clear the input after a successful request.
                setShowResendForm(false);
                setResendEmail('');
            } else {
                setResendErrorMessage(
                    data.message || 'Failed to resend the ticket. Please try again.'
                );
            }
        } catch {
            setResendErrorMessage('An unexpected error occurred. Please try again.');
        } finally {
            setIsResending(false);
        }
    };

    /**
     * Opens the "Resend Ticket" form and clears any message left
     * over from a previous attempt.
     */
    const openResendForm = () => {
        setResendSuccessMessage('');
        setResendErrorMessage('');
        setShowResendForm(true);
    };

    /**
     * Closes the "Resend Ticket" form and discards anything the
     * attendee typed into it.
     */
    const handleResendCancel = () => {
        setShowResendForm(false);
        setResendEmail('');
        setResendErrorMessage('');
    };

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
    const isPublished = event.eventStatus === 'PUBLISHED';

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

                    {/* Only published events let attendees resend their ticket. */}
                    {isPublished && (
                        <section className="view-event__section">
                            <h2 className="view-event__section-title">Lost Your Ticket?</h2>

                            {resendSuccessMessage && (
                                <div className="alert alert--success" role="alert">
                                    {resendSuccessMessage}
                                </div>
                            )}

                            {resendErrorMessage && (
                                <div className="alert alert--error" role="alert">
                                    {resendErrorMessage}
                                </div>
                            )}

                            {!showResendForm ? (
                                <button
                                    type="button"
                                    className="btn btn--outline"
                                    onClick={openResendForm}
                                    aria-expanded={false}
                                    aria-controls="resend-ticket-form"
                                >
                                    Resend Ticket
                                </button>
                            ) : (
                                <form id="resend-ticket-form" onSubmit={handleResendSubmit} noValidate>
                                    <p>
                                        Enter the email address you used to register for this
                                        event, and we will resend your ticket to it.
                                    </p>

                                    <div className="form-group">
                                        <label className="form-label" htmlFor="resend-ticket-email">
                                            Email Address
                                        </label>
                                        <input
                                            id="resend-ticket-email"
                                            className="form-input"
                                            type="email"
                                            value={resendEmail}
                                            onChange={(e) => setResendEmail(e.target.value)}
                                            placeholder="jane.smith@example.com"
                                            maxLength={254}
                                            autoComplete="email"
                                            required
                                        />
                                        <div className="form-hint">
                                            Your ticket will be sent to this email address.
                                        </div>
                                    </div>

                                    <div className="form-actions">
                                        <button
                                            type="submit"
                                            className="btn btn--primary"
                                            disabled={isResending || resendEmail.trim() === ''}
                                        >
                                            {isResending ? 'Sending...' : 'Submit'}
                                        </button>
                                        <button
                                            type="button"
                                            className="btn btn--ghost"
                                            onClick={handleResendCancel}
                                            disabled={isResending}
                                        >
                                            Cancel
                                        </button>
                                    </div>
                                </form>
                            )}
                        </section>
                    )}
                </div>
            </div>
        </main>
    );
}