import { useState, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';

/** Shape of a single scanned ticket returned by GET /api/v1/ticket-scans/{publicId}. */
interface ScannedTicketInfo {
    scannedAt: string;
    attendeeFirstName: string;
    attendeeMiddleName: string | null;
    attendeeLastName: string;
    attendeeEmail: string;
    eventName: string;
    eventDescription: string;
    eventStartDateTime: string;
    eventEndDateTime: string;
}

/**
 * ViewScannedTicketsPage is for implementing the page
 * that lets a logged in event host view a list of tickets
 * scanned for one of their events (identified by the event's
 * public ID in the URL).
 * @returns JSX for the site's view scanned tickets page
 */
export function ViewScannedTicketsPage() {
    const { isLoggedIn, authFetch } = useAuth();
    const navigate = useNavigate();
    const { publicId } = useParams<{ publicId: string }>();

    const [scannedTickets, setScannedTickets] = useState<ScannedTicketInfo[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');

    // If the user is not logged in, redirect to the home page.
    useEffect(() => {
        if (!isLoggedIn) {
            navigate('/', { replace: true });
        }
    }, [isLoggedIn, navigate]);

    /**
     * Fetches the list of tickets scanned for the event with the given
     * public ID from GET /api/v1/ticket-scans/{publicId}.
     */
    const fetchScannedTickets = async () => {
        if (!publicId) {
            setIsLoading(false);
            setLoadError('No event was specified, so its scanned tickets cannot be loaded.');
            return;
        }

        setIsLoading(true);
        setLoadError('');
        try {
            const response = await authFetch(
                `/api/v1/ticket-scans/${encodeURIComponent(publicId)}`,
                { method: 'GET' }
            );
            const data = await response.json();

            if (response.ok && data.scannedTickets) {
                setScannedTickets(data.scannedTickets);
            } else {
                setLoadError(data.message || 'Failed to load scanned tickets.');
            }
        } catch {
            setLoadError('An unexpected error occurred while loading scanned tickets.');
        } finally {
            setIsLoading(false);
        }
    };

    // Fetch the event's scanned tickets on mount, and whenever the event changes.
    useEffect(() => {
        if (isLoggedIn) {
            fetchScannedTickets();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isLoggedIn, publicId]);

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

    /**
     * Builds an attendee's full name from their name parts.
     */
    const getAttendeeFullName = (ticket: ScannedTicketInfo): string => {
        const parts = [ticket.attendeeFirstName, ticket.attendeeMiddleName, ticket.attendeeLastName];
        return parts.filter(Boolean).join(' ');
    };

    if (isLoading) {
        return (
            <main className="status-page">
                <div className="spinner spinner--lg" aria-label="Loading scanned tickets" />
                <h2 className="status-page__title">Loading Scanned Tickets...</h2>
            </main>
        );
    }

    if (loadError) {
        return (
            <main className="status-page">
                <div className="status-page__icon" aria-hidden="true">❌</div>
                <h2 className="status-page__title">Error Loading Scanned Tickets</h2>
                <p className="status-page__message">{loadError}</p>
                <button className="btn btn--primary" onClick={fetchScannedTickets}>
                    Try Again
                </button>
            </main>
        );
    }

    return (
        <main className="page-container">
            <div className="scanned-tickets">
                <div className="scanned-tickets__header">
                    <h1 className="scanned-tickets__title">Tickets You've Scanned</h1>
                    <Link to="/dashboard" className="btn btn--ghost">
                        Back To Dashboard
                    </Link>
                </div>

                {scannedTickets.length === 0 && (
                    <div className="dashboard__empty">
                        <p>No tickets have been scanned for this event yet.</p>
                    </div>
                )}

                {scannedTickets.length > 0 && (
                    <div className="scanned-tickets__list">
                        {scannedTickets.map((ticket) => (
                            <div
                                key={`${ticket.attendeeEmail}-${ticket.scannedAt}`}
                                className="ticket-card"
                            >
                                <div className="ticket-card__top">
                                    <div className="ticket-card__attendee">
                                        <span className="ticket-card__name">
                                            {getAttendeeFullName(ticket)}
                                        </span>
                                        <span className="ticket-card__email">
                                            {ticket.attendeeEmail}
                                        </span>
                                    </div>
                                    <div className="ticket-card__scanned-at">
                                        Scanned {formatDateTime(ticket.scannedAt)}
                                    </div>
                                </div>
                                <div className="ticket-card__event">
                                    <div className="ticket-card__event-name">{ticket.eventName}</div>
                                    <div className="ticket-card__event-dates">
                                        {formatDateTime(ticket.eventStartDateTime)} &mdash;{' '}
                                        {formatDateTime(ticket.eventEndDateTime)}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </main>
    );
}