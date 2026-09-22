import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext.tsx';

/**
 * NavigationBar is for implementing a navigation bar that should
 * always be visible on the top of each web page. Its visible elements
 * should be different based on whether or not a user is currently logged in.
 * @returns JSX for the site's navigation bar.
 */
export function NavigationBar() {
    const { isLoggedIn, logout, authFetch } = useAuth();
    const navigate = useNavigate();

    /**
     * Handles the Log Out button click. Calls the logout API
     * endpoint to revoke the current session, then clears local
     * auth state and redirects to the home page.
     */
    const handleLogout = async () => {
        try {
            await authFetch('/api/v1/sessions/logout', { method: 'PATCH' });
        } catch {
            // Even if the API call fails (e.g. network error or expired JWT),
            // clear local auth state so the user can still log out locally.
        } finally {
            logout();
            navigate('/');
        }
    };

    return (
        <nav className="navbar">
            <Link to="/" className="navbar__brand">
                <span className="navbar__brand-icon" aria-hidden="true">🎟️</span>
                ScanMeIn
            </Link>
            <div className="navbar__links">
                {isLoggedIn ? (
                    <>
                        <Link to="/dashboard" className="navbar__btn navbar__btn--dashboard">Dashboard</Link>
                        <button
                            className="navbar__btn navbar__btn--logout"
                            onClick={handleLogout}
                        >
                            Log Out
                        </button>
                    </>
                ) : (
                    <>
                        <Link to="/login">
                            <button className="navbar__btn navbar__btn--login">Log In</button>
                        </Link>
                        <Link to="/signup">
                            <button className="navbar__btn navbar__btn--signup">Sign Up</button>
                        </Link>
                    </>
                )}
            </div>
        </nav>
    );
}