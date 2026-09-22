import { Link } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';
import heroImage from './assets/hero.png';

/**
 * HomePage is for implementing the first page that someone sees
 * when they visit the site.
 * @returns JSX for the site's home page
 */
export function HomePage() {
    const { isLoggedIn } = useAuth();

    return (
        <main className="page-container page-container--wide">
            {/* Hero section */}
            <section className="hero">
                <div className="hero__content">
                    <h1 className="hero__title">
                        Event ticketing,<br />
                        made <span>simple</span>.
                    </h1>
                    <p className="hero__subtitle">
                        Create events, send invitations, and manage attendance
                        all from one place. Attendees just need an email
                        address to get their tickets.
                    </p>
                    <div className="hero__actions">
                        {isLoggedIn ? (
                            <Link to="/dashboard" className="btn btn--accent btn--lg">
                                Go to Dashboard
                            </Link>
                        ) : (
                            <>
                                <Link to="/signup" className="btn btn--accent btn--lg">
                                    Get Started For Free
                                </Link>
                                <Link to="/login" className="btn btn--outline btn--lg">
                                    Log In
                                </Link>
                            </>
                        )}
                    </div>
                </div>
                <div className="hero__image">
                    <img
                        src={heroImage}
                        alt="Illustration of event ticketing"
                    />
                </div>
            </section>

            {/* Feature cards */}
            <section className="features">
                <div className="feature-card">
                    <div className="feature-card__icon" aria-hidden="true">📋</div>
                    <h2 className="feature-card__title">Create Your Own Events</h2>
                    <p className="feature-card__text">
                        Create public pages for your own events so anyone
                        can register for it, or create private events and
                        send out invitations to people of your choice.
                    </p>
                </div>
                <div className="feature-card">
                    <div className="feature-card__icon" aria-hidden="true">✅</div>
                    <h2 className="feature-card__title">Simple Check-In</h2>
                    <p className="feature-card__text">
                        All an attendee needs in order to attend an event
                        or receive an invitation is a working email address.
                        Once they receive their tickets, they are free to
                        present them on any device of their choice or even
                        print out their tickets. No ScanMeIn.online account
                        or special smartphone app is required to attend an
                        event hosted on ScanMeIn.online.
                    </p>
                </div>
                <div className="feature-card">
                    <div className="feature-card__icon" aria-hidden="true">📊</div>
                    <h2 className="feature-card__title">Track And Manage Attendance</h2>
                    <p className="feature-card__text">
                        Event hosts scan in attendees using the ScanMeIn.online
                        app for Android. Event hosts can also view attendance
                        statistics for each of their events, and manage
                        invitations and registrations.
                    </p>
                </div>
            </section>
        </main>
    );
}