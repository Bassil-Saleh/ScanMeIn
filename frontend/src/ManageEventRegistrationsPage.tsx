/**
 * ManageEventRegistrationsPage is for implementing the page
 * that lets a logged in user retrieve, view, and manage
 * a list of registrations for an event they've created.
 * Note that only the event host who created the event
 * should be allowed to manage the event's registrations.
 * @returns JSX for the site's manage event registrations page
 */
export function ManageEventRegistrationsPage() {
    // TODO: If the user is not currently logged in, then they
    //       should be automatically redirected to the home page.

    // TODO: If the user is logged in but they are not the one
    //       who created the event, show a message saying that
    //       only the event host who created the event should be
    //       allowed to manage its registrations.

    // TODO: Show a table view of the event's registrations.
    //       The table should have columns for:
    //       - First name
    //       - Middle name
    //       - Last name
    //       - Email address
    //       - Present (if true, show a checkbox, otherwise show an X symbol)
    //       - Invitation status
    //       - Created (include month, day, year, and show time in AM/PM format)
    //       - Last updated (include month, day, year, and show time in AM/PM format)
    //       - A clickable checkbox (to select a specific record)

    // TODO: Show a "Delete" button above the table that gets enabled when
    //       at least one row in the event registrations table is selected.

    // TODO: Clicking the "Delete" button should show a confirmation dialog that has:
    //       - A message that asks the user if they want to proceed
    //         with deleting X amount of registrations (where X = the
    //         number of selected registrations in the table).
    //       - A "Go Back" button to exit the dialog.
    //       - A "Delete" button to confirm and trigger the deletion of
    //         the selected registrations.

    // TODO: When the user confirms the deletion of selected registrations,
    //       send a request to the API route DELETE /api/v1/tickets.

    // TODO: Show an appropriate message to the user based on
    //       the result of a request sent to the API route
    //       DELETE /api/v1/tickets.

    // TODO: After a request to DELETE /api/v1/tickets succeeds,
    //       update the table view of the event's registrations.

    // TODO: If the user is logged in and an event public ID
    //       was provided, send a request to the API route
    //       GET /api/v1/tickets/{publicId} to retrieve a
    //       list of records on tickets for the event.

    // TODO: If the event public ID provided does not correspond
    //       to an existing event, show a message saying so.

    // TODO: If the request to the API route GET /api/v1/tickets/{publicId}
    //       fails, show an appropriate error message.

    // TODO: If the request to the API route GET /api/v1/tickets/{publicId}
    //       succeeds, use the request payload to populate the table view
    //       of the event's registrations.

    return (
        <div></div>
    );
}