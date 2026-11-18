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
 * Shape of a single blocked registration returned by
 * GET /api/v1/blocked-registrations/{publicId}.
 */
interface BlockedRegistrationInfo {
    firstName: string;
    middleName: string | null;
    lastName: string;
    email: string;
    blockedByEmail: string;
    reason: string | null;
    created: string;
    revoked: string | null;
}

/**
 * The result of the most recent Delete, Block, or Unblock request,
 * used to show a success or error notification to the event host.
 */
interface ActionStatus {
    type: 'success' | 'error';
    text: string;
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

    // Delete confirmation dialog state for DELETE /api/v1/tickets.
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    // The result of the most recent Delete, Block, or Unblock request.
    // Only one notification is shown at a time.
    const [actionStatus, setActionStatus] = useState<ActionStatus | null>(null);

    // Blocked registrations for the event, as returned by
    // GET /api/v1/blocked-registrations/{publicId}.
    const [blockedRegistrations, setBlockedRegistrations] = useState<BlockedRegistrationInfo[]>([]);
    const [isBlockedLoading, setIsBlockedLoading] = useState(true);
    const [blockedLoadError, setBlockedLoadError] = useState('');

    // Emails of the blocked registrations currently selected in the
    // "Blocked Registrations" table. Blocked registrations are identified
    // by the attendee's email address in
    // DELETE /api/v1/blocked-registrations.
    const [selectedBlockedEmails, setSelectedBlockedEmails] = useState<Set<string>>(new Set());

    // Block and Unblock confirmation dialog state.
    const [showBlockConfirm, setShowBlockConfirm] = useState(false);
    const [isBlocking, setIsBlocking] = useState(false);
    const [showUnblockConfirm, setShowUnblockConfirm] = useState(false);
    const [isUnblocking, setIsUnblocking] = useState(false);

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

    /**
     * Fetches the list of registrations blocked from the event with the
     * given public ID from GET /api/v1/blocked-registrations/{publicId}.
     * A failed request is reported inline above the "Blocked Registrations"
     * table so the event's registrations can still be managed.
     */
    const fetchBlockedRegistrations = async () => {
        if (!publicId) return;
        setIsBlockedLoading(true);
        setBlockedLoadError('');
        try {
            const response = await authFetch(
                `/api/v1/blocked-registrations/${encodeURIComponent(publicId)}`,
                { method: 'GET' }
            );
            const data = await response.json();

            if (response.ok && data.blockedRegistrations) {
                setBlockedRegistrations(data.blockedRegistrations);
                // Drop any selected rows which are no longer blocked from
                // the event (i.e. rows which were just unblocked).
                setSelectedBlockedEmails((previous) => {
                    const remaining = new Set(
                        data.blockedRegistrations.map(
                            (block: BlockedRegistrationInfo) => block.email
                        )
                    );
                    const next = new Set<string>();
                    previous.forEach((email) => {
                        if (remaining.has(email)) next.add(email);
                    });
                    return next;
                });
            } else {
                setBlockedLoadError(
                    data.message || 'Failed to load the blocked registrations.'
                );
            }
        } catch {
            setBlockedLoadError(
                'An unexpected error occurred while loading the blocked registrations.'
            );
        } finally {
            setIsBlockedLoading(false);
        }
    };

    /**
     * Loads every list shown on the page: the event's registrations and
     * the registrations blocked from the event.
     */
    const loadPageData = () => {
        fetchTickets();
        fetchBlockedRegistrations();
    };

    // Fetch the event's registrations and blocked registrations on mount,
    // and whenever the event changes.
    useEffect(() => {
        if (isLoggedIn && publicId) {
            loadPageData();
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
     * Adds or removes the given email address from the set of blocked
     * registrations currently selected in the "Blocked Registrations" table.
     * @param email the blocked registration's email address
     * @param isSelected whether the row's checkbox is now checked
     */
    const toggleBlockedRowSelection = (email: string, isSelected: boolean) => {
        setSelectedBlockedEmails((previous) => {
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
     * Selects (or deselects) every blocked registration in the table at once.
     * @param isSelected whether the header's checkbox is now checked
     */
    const toggleAllBlockedSelection = (isSelected: boolean) => {
        setSelectedBlockedEmails(
            isSelected
                ? new Set(blockedRegistrations.map((block) => block.email))
                : new Set<string>()
        );
    };

    /**
     * Deletes the selected registrations by sending a request to
     * DELETE /api/v1/tickets, shows a notification based on the result, and
     * refreshes the table view when the request succeeds.
     */
    const handleDeleteSelected = async () => {
        setIsDeleting(true);
        setActionStatus(null);
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
                setActionStatus({
                    type: 'success',
                    text: data.message || 'The selected registrations have been deleted.',
                });
                setShowDeleteConfirm(false);
                setSelectedEmails(new Set());
                // Refresh the table so the deleted registrations are gone.
                await fetchTickets();
            } else {
                setActionStatus({
                    type: 'error',
                    text: data.message || 'Failed to delete the selected registrations.',
                });
                setShowDeleteConfirm(false);
            }
        } catch {
            setActionStatus({
                type: 'error',
                text: 'An unexpected error occurred while deleting the selected registrations.',
            });
            setShowDeleteConfirm(false);
        } finally {
            setIsDeleting(false);
        }
    };

    /**
     * Blocks the selected registrations by sending a request to
     * POST /api/v1/blocked-registrations, shows a notification based on the
     * result, and refreshes the "Blocked Registrations" table when the
     * request succeeds.
     */
    const handleBlockSelected = async () => {
        setIsBlocking(true);
        setActionStatus(null);
        try {
            const response = await authFetch('/api/v1/blocked-registrations', {
                method: 'POST',
                body: JSON.stringify({
                    publicId: publicId ?? '',
                    emails: Array.from(selectedEmails),
                }),
            });
            const data = await response.json();

            if (response.ok) {
                setActionStatus({
                    type: 'success',
                    text: data.message || 'The selected registrations have been blocked.',
                });
                setShowBlockConfirm(false);
                setSelectedEmails(new Set());
                // Refresh the blocked list so the newly blocked attendees appear.
                await fetchBlockedRegistrations();
            } else {
                setActionStatus({
                    type: 'error',
                    text: data.message || 'Failed to block the selected registrations.',
                });
                setShowBlockConfirm(false);
            }
        } catch {
            setActionStatus({
                type: 'error',
                text: 'An unexpected error occurred while blocking the selected registrations.',
            });
            setShowBlockConfirm(false);
        } finally {
            setIsBlocking(false);
        }
    };

    /**
     * Unblocks the selected registrations by sending a request to
     * DELETE /api/v1/blocked-registrations, shows a notification based on
     * the result, and refreshes the "Blocked Registrations" table when the
     * request succeeds.
     */
    const handleUnblockSelected = async () => {
        setIsUnblocking(true);
        setActionStatus(null);
        try {
            const response = await authFetch('/api/v1/blocked-registrations', {
                method: 'DELETE',
                body: JSON.stringify({
                    publicId: publicId ?? '',
                    emails: Array.from(selectedBlockedEmails),
                }),
            });
            const data = await response.json();

            if (response.ok) {
                setActionStatus({
                    type: 'success',
                    text: data.message || 'The selected registrations have been unblocked.',
                });
                setShowUnblockConfirm(false);
                setSelectedBlockedEmails(new Set());
                // Refresh the blocked list so the unblocked attendees disappear.
                await fetchBlockedRegistrations();
            } else {
                setActionStatus({
                    type: 'error',
                    text: data.message || 'Failed to unblock the selected registrations.',
                });
                setShowUnblockConfirm(false);
            }
        } catch {
            setActionStatus({
                type: 'error',
                text: 'An unexpected error occurred while unblocking the selected registrations.',
            });
            setShowUnblockConfirm(false);
        } finally {
            setIsUnblocking(false);
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
    // Whether every blocked registration in the table is currently selected.
    const areAllBlockedSelected =
        blockedRegistrations.length > 0 &&
        selectedBlockedEmails.size === blockedRegistrations.length;
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
                    <button type="button" className="btn btn--primary" onClick={loadPageData}>
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

                {actionStatus && (
                    <div
                        className={`alert ${
                            actionStatus.type === 'success' ? 'alert--success' : 'alert--error'
                        }`}
                        role="alert"
                    >
                        {actionStatus.text}
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
                    <button
                        type="button"
                        className="btn btn--outline btn--sm"
                        onClick={() => setShowBlockConfirm(true)}
                        disabled={selectedEmails.size === 0 || isBlocking}
                    >
                        Block{selectedEmails.size > 0 ? ` (${selectedEmails.size})` : ''}
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
                                    <th scope="col" className="registrations-table__present">Present</th>
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

                {/* "Blocked Registrations" section: lists the attendees who
                    are blocked from the event. The "Unblock" button is
                    enabled only when at least one blocked row is selected. */}
                <section className="manage-registrations__section">
                    <h2 className="manage-registrations__section-title">Blocked Registrations</h2>
                    <div className="manage-registrations__toolbar">
                        <button
                            type="button"
                            className="btn btn--primary btn--sm"
                            onClick={() => setShowUnblockConfirm(true)}
                            disabled={selectedBlockedEmails.size === 0 || isUnblocking}
                        >
                            Unblock
                            {selectedBlockedEmails.size > 0
                                ? ` (${selectedBlockedEmails.size})`
                                : ''}
                        </button>
                        <span className="manage-registrations__count">
                            {blockedRegistrations.length} blocked
                            {selectedBlockedEmails.size > 0 &&
                                ` · ${selectedBlockedEmails.size} selected`}
                        </span>
                    </div>

                    {isBlockedLoading ? (
                        <div className="dashboard__empty">
                            <div className="spinner" aria-label="Loading blocked registrations" />
                            <p>Loading blocked registrations...</p>
                        </div>
                    ) : blockedLoadError ? (
                        <div className="alert alert--error" role="alert">
                            {blockedLoadError}{' '}
                            <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                onClick={fetchBlockedRegistrations}
                            >
                                Try Again
                            </button>
                        </div>
                    ) : blockedRegistrations.length === 0 ? (
                        <div className="dashboard__empty">
                            <p>No one is currently blocked from this event.</p>
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
                                        <th scope="col">Blocked</th>
                                        <th scope="col" className="registrations-table__select">
                                            <input
                                                type="checkbox"
                                                checked={areAllBlockedSelected}
                                                onChange={(e) =>
                                                    toggleAllBlockedSelection(e.target.checked)
                                                }
                                                aria-label="Select all blocked registrations"
                                            />
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {blockedRegistrations.map((block) => {
                                        const isSelected = selectedBlockedEmails.has(block.email);
                                        return (
                                            <tr
                                                key={block.email}
                                                className={
                                                    isSelected
                                                        ? 'registrations-table__row--selected'
                                                        : undefined
                                                }
                                            >
                                                <td>{block.firstName}</td>
                                                <td>{block.middleName ?? ''}</td>
                                                <td>{block.lastName}</td>
                                                <td className="registrations-table__email">
                                                    {block.email}
                                                </td>
                                                <td className="registrations-table__datetime">
                                                    {formatDateTime(block.created)}
                                                </td>
                                                <td className="registrations-table__select">
                                                    <input
                                                        type="checkbox"
                                                        checked={isSelected}
                                                        onChange={(e) =>
                                                            toggleBlockedRowSelection(
                                                                block.email,
                                                                e.target.checked
                                                            )
                                                        }
                                                        aria-label={`Select blocked registration for ${block.email}`}
                                                    />
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

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

                {/* Block registrations confirmation dialog */}
                {showBlockConfirm && (
                    <div
                        className="modal-overlay"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="block-registrations-confirm-title"
                    >
                        <div className="modal">
                            <h2 id="block-registrations-confirm-title" className="modal__title">
                                Block Registrations?
                            </h2>
                            <p className="modal__message">
                                Are you sure you want to proceed with blocking{' '}
                                {selectedEmails.size} registration
                                {selectedEmails.size === 1 ? '' : 's'}? The selected
                                attendees will no longer be able to scan themselves into
                                this event.
                            </p>
                            <div className="modal__actions">
                                <button
                                    type="button"
                                    className="btn btn--danger"
                                    onClick={handleBlockSelected}
                                    disabled={isBlocking}
                                >
                                    {isBlocking ? 'Blocking...' : 'Block'}
                                </button>
                                <button
                                    type="button"
                                    className="btn btn--ghost"
                                    onClick={() => setShowBlockConfirm(false)}
                                    disabled={isBlocking}
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {/* Unblock registrations confirmation dialog */}
                {showUnblockConfirm && (
                    <div
                        className="modal-overlay"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="unblock-registrations-confirm-title"
                    >
                        <div className="modal">
                            <h2 id="unblock-registrations-confirm-title" className="modal__title">
                                Unblock Registrations?
                            </h2>
                            <p className="modal__message">
                                Are you sure you want to proceed with unblocking{' '}
                                {selectedBlockedEmails.size} person
                                {selectedBlockedEmails.size === 1 ? '' : 'people'} from this
                                event? The selected attendees will be able to scan
                                themselves into the event again.
                            </p>
                            <div className="modal__actions">
                                <button
                                    type="button"
                                    className="btn btn--primary"
                                    onClick={handleUnblockSelected}
                                    disabled={isUnblocking}
                                >
                                    {isUnblocking ? 'Unblocking...' : 'Unblock'}
                                </button>
                                <button
                                    type="button"
                                    className="btn btn--ghost"
                                    onClick={() => setShowUnblockConfirm(false)}
                                    disabled={isUnblocking}
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </main>
    );
}