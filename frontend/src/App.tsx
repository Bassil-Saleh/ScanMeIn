import { Routes, Route } from 'react-router-dom';
import { HomePage } from './HomePage.tsx';
import { NavigationBar } from './NavigationBar.tsx';
import { Footer } from './Footer.tsx';
import { LoginPage } from './LoginPage.tsx';
import { SignUpPage } from './SignUpPage.tsx';
import { Dashboard } from './Dashboard.tsx';
import { ProfilePage } from './ProfilePage.tsx';
import { ForgotPasswordPage } from './ForgotPasswordPage.tsx';
import { ResetPasswordPage } from './ResetPasswordPage.tsx';
import { AccountVerificationPage } from './AccountVerificationPage.tsx';
import { CreateEventPage } from './CreateEventPage.tsx';
import { EditEventPage } from './EditEventPage.tsx';
import { ViewEventPage } from './ViewEventPage.tsx';
import { ViewScannedTicketsPage } from './ViewScannedTicketsPage.tsx';
import { ScanTicketPage } from './ScanTicketPage.tsx';
import { DeleteAccountPage } from './DeleteAccountPage.tsx';
import { EventRegistrationPage } from './EventRegistrationPage.tsx';
import { CreateInvitationPage } from './CreateInvitationPage.tsx';
import { InvitationResponsePage } from './InvitationResponsePage.tsx';
import { ManageEventRegistrationsPage } from './ManageEventRegistrationsPage.tsx';

/**
 * App is the component from which all the other
 * components branch off from. It defines the client-side
 * routes for every page in the application.
 * @returns JSX for the application's route tree.
 */
function App() {
  return (
    <div>
      <NavigationBar />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignUpPage />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/verify-account" element={<AccountVerificationPage />} />
        <Route path="/create-event" element={<CreateEventPage />} />
        <Route path="/scanned-tickets" element={<ViewScannedTicketsPage />} />
        <Route path="/scan-tickets" element={<ScanTicketPage />} />
        <Route path="/delete-account" element={<DeleteAccountPage />} />
        <Route path="/events/:publicId" element={<ViewEventPage />} />
        <Route path="/events/:publicId/register" element={<EventRegistrationPage />} />
        <Route path="/events/:publicId/invite" element={<CreateInvitationPage />} />
        <Route path="/events/:publicId/edit" element={<EditEventPage />} />
        <Route path="/events/:publicId/registrations" element={<ManageEventRegistrationsPage />} />
        <Route path="/respond-to-invitation" element={<InvitationResponsePage />} />
      </Routes>
      <Footer />
    </div>
  );
}

export default App