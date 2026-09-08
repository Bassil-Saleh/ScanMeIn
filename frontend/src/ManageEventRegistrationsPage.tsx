import { useState, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';

/** The possible invitation statuses a ticket can have, as returned by the API. */
type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

/**
 * Which kind of error (if any) occurred while loading the event's
 * registrations: the event does not exist, the logged in event host is
 * not the one who created the event, or some other failure.
 */
type LoadErrorKind = 'notFound' | 'forbidden' | 'generic';

/** Display label and badge CSS class for each invitation status. */
const INVITATION_STATUS_BADGES: Record<InvitationStatus, { label: string; className: string }> = {
    PENDING: { label: 'Pending', className: 'event-status-badge event-status-badge--draft' },
    ACCEPTED: { label: 'Accepted', className: 'event-status-badge event-status-badge--published' },
    REJECTED: { label: 'Rejected', className: 'event-status-badge event-status-badge--canceled' },
};

/**
 * Returns the display label and badge CSS class for the given invitation
 * status, falling back to the raw status value if the status is not recognized.
 * @param status the ticket's invitation status
 * @returns the label and CSS class to use for the status badge
 */
function getInvitationStatusBadge(status: InvitationStatus): { label: string; className: string } {
    return INVITATION_STATUS_BADGES[status] ?? { label: status, className: 'event-status-badge event-status-badge--draft' };
}

/** Shape of a single ticket returned by GET /api/v1/tickets/{publicId}. */
interface EventTicketInfo {
    firstName: string;
    middleName: string | null;
    lastName: string;
    email: string;
    present: boolean;
    invitationStatus: InvitationStatus;
    created: string;
    deletedAt: string | null;
    lastUpdated: string;
}

/**
 * ManageEventRegistrationsPage is for implementing the page
 * that lets a logged in user retrieve, view, and manage
 * a list of registrations for an event they've created.
 * Note that only the event host who created the event
 * should be allowed to manage the event's registrations.
 * @returns JSX for the site's manage event registrations page
 */
export function ManageEventRegistrationsPage() {
    const { isLoggedIn, authFetch } = useAuth();
    const navigate = useNavigate();
    const { publicId } = useParams<{ publicId: string }>();

    const [tickets, setTickets] = useState<EventTicketInfo[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [loadErrorKind, setLoadErrorKind] = useState<LoadErrorKind | null>(null);

    // Emails of the registrations currently selected in the table. Tickets are
    // identified by the attendee's email address in DELETE /api/v1/tickets.
    const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());

    // Delete confirmation dialog state, plus the result of a
    // DELETE /api/v1/tickets request.
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [deleteMessage, setDeleteMessage] = useState('');
    const [deleteError, setDeleteError] = useState('');

    // If the user is not logged in, redirect to the home page.
    useEffect(() => {
        if (!isLoggedIn) {
            navigate('/', { replace: true });
        }
    }, [isLoggedIn, navigate]);

    /**
     * Fetches the list of registrations (tickets) for the event with the
     * given public ID from GET /api/v1/tickets/{publicId}. Only the event
     * host who created the event is allowed to see those records, so a
     * failed request is classified as "event not found", "not the event's
     * host", or a generic error.
     */
    const fetchTickets = async () => {
        if (!publicId) return;
        setIsLoading(true);
        setLoadError('');
        setLoadErrorKind(null);
        try {
            const response = await authFetch(`/api/v1/tickets/${encodeURIComponent(publicId)}`, {
                method: 'GET',
            });
            const data = await response.json();

            if (response.ok && data.tickets) {
                setTickets(data.tickets);
                // Drop any selected rows which are no longer part of the
                // event's registrations (i.e. rows which were just deleted).
                setSelectedEmails((previous) => {
                    const remaining = new Set(data.tickets.map((ticket: EventTicketInfo) => ticket.email));
                    const next = new Set<string>();
                    previous.forEach((email) => {
                        if (remaining.has(email)) next.add(email);
                    });
                    return next;
                });
            } else {
                const message = data.message || 'Failed to load the event registrations.';
                setLoadError(message);
                if (response.status === 404) {
                    setLoadErrorKind('notFound');
                } else if (response.status === 401) {
                    setLoadErrorKind('forbidden');
                } else {
                    setLoadErrorKind('generic');
                }
            }
        } catch {
            setLoadError('An unexpected error occurred while loading the event registrations.');
            setLoadErrorKind('generic');
        } finally {
            setIsLoading(false);
        }
    };

    // Fetch the event's registrations on mount, and whenever the event changes.
    useEffect(() => {
        if (isLoggedIn && publicId) {
            fetchTickets();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isLoggedIn, publicId]);

    /**
     * Adds or removes the given email address from the set of registrations
     * currently selected in the table.
     * @param email the registration's email address
     * @param isSelected whether the row's checkbox is now checked
     */
    const toggleRowSelection = (email: string, isSelected: boolean) => {
        setSelectedEmails((previous) => {
            const next = new Set(previous);
            if (isSelected) {
                next.add(email);
            } else {
                next.delete(email);
            }
            return next;
        });
    };

    /**
     * Selects (or deselects) every registration in the table at once.
     * @param isSelected whether the header's checkbox is now checked
     */
    const toggleAllSelection = (isSelected: boolean) => {
        setSelectedEmails(
            isSelected ? new Set(tickets.map((ticket) => ticket.email)) : new Set<string>()
        );
    };

    /**
     * Deletes the selected registrations by sending a request to
     * DELETE /api/v1/tickets, shows a message based on the result, and
     * refreshes the table view when the request succeeds.
     */
    const handleDeleteSelected = async () => {
        setIsDeleting(true);
        setDeleteMessage('');
        setDeleteError('');
        try {
            const response = await authFetch('/api/v1/tickets', {
                method: 'DELETE',
                body: JSON.stringify({
                    publicId: publicId ?? '',
                    emails: Array.from(selectedEmails),
                }),
            });
            const data = await response.json();

            if (response.ok) {
                setDeleteMessage(data.message || 'The selected registrations have been deleted.');
                setShowDeleteConfirm(false);
                setSelectedEmails(new Set());
                // Refresh the table so the deleted registrations are gone.
                await fetchTickets();
            } else {
                setDeleteError(data.message || 'Failed to delete the selected registrations.');
                setShowDeleteConfirm(false);
            }
        } catch {
            setDeleteError('An unexpected error occurred while deleting the selected registrations.');
            setShowDeleteConfirm(false);
        } finally {
            setIsDeleting(false);
        }
    };

    /**
     * Formats an ISO date-time string into a human-readable format which
     * includes the month, day, and year, with the time in AM/PM format.
     */
    const formatDateTime = (isoString: string): string => {
        if (!isoString) return '';
        try {
            return new Date(isoString).toLocaleString(undefined, {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: 'numeric',
                minute: '2-digit',
            });
        } catch {
            return isoString;
        }
    };

    // Whether every registration in the table is currently selected.
    const areAllSelected = tickets.length > 0 && selectedEmails.size === tickets.length;
    // Show a loading screen while the event's registrations are being fetched.
    if (isLoading) {
        return (
            <main className="status-page">
                <div className="spinner spinner--lg" aria-label="Loading event registrations" />
                <h2 className="status-page__title">Loading Event Registrations...</h2>
            </main>
        );
    }

    // The event public ID provided does not correspond to an existing event.
    if (loadErrorKind === 'notFound') {
        return (
            <main className="status-page">
                <div className="status-page__icon" aria-hidden="true">🔍</div>
                <h2 className="status-page__title">Event Not Found</h2>
                <p className="status-page__message">
                    {loadError || 'No event exists with the provided public ID.'}
                </p>
                <Link to="/dashboard" className="btn btn--outline btn--lg">
                    Back to Dashboard
                </Link>
            </main>
        );
    }

    // The logged in event host is not the one who created the event.
    if (loadErrorKind === 'forbidden') {
        return (
            <main className="status-page">
                <div className="status-page__icon" aria-hidden="true">🔒</div>
                <h2 className="status-page__title">Access Denied</h2>
                <p className="status-page__message">
                    Only the event host who created this event is allowed to manage
                    its registrations.
                </p>
                <Link to="/dashboard" className="btn btn--outline btn--lg">
                    Back to Dashboard
                </Link>
            </main>
        );
    }

    // Any other failure while loading the event's registrations.
    if (loadError) {
        return (
            <main className="status-page">
                <div className="status-page__icon" aria-hidden="true">❌</div>
                <h2 className="status-page__title">Error Loading Event Registrations</h2>
                <p className="status-page__message">{loadError}</p>
                <div className="form-actions">
                    <button type="button" className="btn btn--primary" onClick={fetchTickets}>
                        Try Again
                    </button>
                    <Link to="/dashboard" className="btn btn--ghost">
                        Back to Dashboard
                    </Link>
                </div>
            </main>
        );
    }

    return (
        <main className="page-container page-container--wide">
            <div className="manage-registrations">
                <div className="manage-registrations__header">
                    <h1 className="manage-registrations__title">Manage Event Registrations</h1>
                    <Link to="/dashboard" className="btn btn--ghost">
                        Back to Dashboard
                    </Link>
                </div>

                {deleteMessage && (
                    <div className="alert alert--success" role="alert">
                        {deleteMessage}
                    </div>
                )}

                {deleteError && (
                    <div className="alert alert--error" role="alert">
                        {deleteError}
                    </div>
                )}

                {/* Toolbar: the "Delete" button is enabled only when at least
                    one row in the registrations table is selected. */}
                <div className="manage-registrations__toolbar">
                    <button
                        type="button"
                        className="btn btn--danger btn--sm"
                        onClick={() => setShowDeleteConfirm(true)}
                        disabled={selectedEmails.size === 0 || isDeleting}
                    >
                        Delete{selectedEmails.size > 0 ? ` (${selectedEmails.size})` : ''}
                    </button>
                    <span className="manage-registrations__count">
                        {tickets.length} registration{tickets.length === 1 ? '' : 's'}
                        {selectedEmails.size > 0 && ` · ${selectedEmails.size} selected`}
                    </span>
                </div>

                {tickets.length === 0 ? (
                    <div className="dashboard__empty">
                        <p>No one has registered for this event yet.</p>
                    </div>
                ) : (
                    <div className="registrations-table__wrapper">
                        <table className="registrations-table">
                            <thead>
                                <tr>
                                    <th scope="col">First Name</th>
                                    <th scope="col">Middle Name</th>
                                    <th scope="col">Last Name</th>
                                    <th scope="col">Email Address</th>
                                    <th scope="col">Present</th>
                                    <th scope="col">Invitation Status</th>
                                    <th scope="col">Created</th>
                                    <th scope="col">Last Updated</th>
                                    <th scope="col" className="registrations-table__select">
                                        <input
                                            type="checkbox"
                                            checked={areAllSelected}
                                            onChange={(e) => toggleAllSelection(e.target.checked)}
                                            aria-label="Select all registrations"
                                        />
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {tickets.map((ticket) => {
                                    const statusBadge = getInvitationStatusBadge(ticket.invitationStatus);
                                    const isSelected = selectedEmails.has(ticket.email);
                                    return (
                                        <tr
                                            key={ticket.email}
                                            className={
                                                isSelected
                                                    ? 'registrations-table__row--selected'
                                                    : undefined
                                            }
                                        >
                                            <td>{ticket.firstName}</td>
                                            <td>{ticket.middleName ?? ''}</td>
                                            <td>{ticket.lastName}</td>
                                            <td className="registrations-table__email">{ticket.email}</td>
                                            <td className="registrations-table__present">
                                                {ticket.present ? (
                                                    <input
                                                        type="checkbox"
                                                        checked
                                                        readOnly
                                                        aria-label={`${ticket.email} is present`}
                                                    />
                                                ) : (
                                                    <span
                                                        className="registrations-table__absent"
                                                        aria-label="Not present"
                                                    >
                                                        ✗
                                                    </span>
                                                )}
                                            </td>
                                            <td>
                                                <span className={statusBadge.className}>
                                                    {statusBadge.label}
                                                </span>
                                            </td>
                                            <td className="registrations-table__datetime">
                                                {formatDateTime(ticket.created)}
                                            </td>
                                            <td className="registrations-table__datetime">
                                                {formatDateTime(ticket.lastUpdated)}
                                            </td>
                                            <td className="registrations-table__select">
                                                <input
                                                    type="checkbox"
                                                    checked={isSelected}
                                                    onChange={(e) =>
                                                        toggleRowSelection(ticket.email, e.target.checked)
                                                    }
                                                    aria-label={`Select registration for ${ticket.email}`}
                                                />
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Delete registrations confirmation dialog */}
                {showDeleteConfirm && (
                    <div
                        className="modal-overlay"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="delete-registrations-confirm-title"
                    >
                        <div className="modal">
                            <h2 id="delete-registrations-confirm-title" className="modal__title">
                                Delete Registrations?
                            </h2>
                            <p className="modal__message">
                                Are you sure you want to proceed with deleting{' '}
                                {selectedEmails.size} registration
                                {selectedEmails.size === 1 ? '' : 's'}? The selected
                                tickets for this event will be removed, and this
                                cannot be undone.
                            </p>
                            <div className="modal__actions">
                                <button
                                    type="button"
                                    className="btn btn--danger"
                                    onClick={handleDeleteSelected}
                                    disabled={isDeleting}
                                >
                                    {isDeleting ? 'Deleting...' : 'Delete'}
                                </button>
                                <button
                                    type="button"
                                    className="btn btn--ghost"
                                    onClick={() => setShowDeleteConfirm(false)}
                                    disabled={isDeleting}
                                >
                                    Go Back
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </main>
    );
}