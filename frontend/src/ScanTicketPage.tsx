import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Html5Qrcode, type CameraDevice } from 'html5-qrcode';
import { useAuth } from './AuthContext.tsx';

/** DOM element id the QR scanner renders its camera feed into. */
const SCANNER_ELEMENT_ID = 'qr-reader';

/** How long (in ms) repeated detections of a failed token are ignored. */
const FAILED_TOKEN_COOLDOWN_MS = 5000;

/** The states of the camera setup flow. */
type CameraSetupState =
    | 'requesting'
    | 'insecure-context'
    | 'permission-denied'
    | 'no-camera'
    | 'ready';

/** The phases of an active scanning session. */
type ScanPhase = 'idle' | 'starting' | 'scanning';

/** A success or failure message shown after a scan request completes. */
interface ScanResultMessage {
    type: 'success' | 'error';
    text: string;
}

/**
 * ScanTicketPage is for implementing the page that lets a
 * logged in event host scan a QR code ticket using their
 * device's camera (webcam, smartphone camera, etc.)
 * going through their web browser.
 * @returns JSX for the site's ticket scanning page
 */
export function ScanTicketPage() {
    const { isLoggedIn, authFetch } = useAuth();
    const navigate = useNavigate();

    const [setupState, setSetupState] = useState<CameraSetupState>('requesting');
    const [cameras, setCameras] = useState<CameraDevice[]>([]);
    const [selectedCameraId, setSelectedCameraId] = useState('');
    const [scanPhase, setScanPhase] = useState<ScanPhase>('idle');
    const [cameraError, setCameraError] = useState('');
    const [scanMessage, setScanMessage] = useState<ScanResultMessage | null>(null);
    const [isSubmittingScan, setIsSubmittingScan] = useState(false);

    const scannerRef = useRef<Html5Qrcode | null>(null);
    const isHandlingScanRef = useRef(false);
    const failedTokenCooldownRef = useRef<{ token: string; until: number } | null>(null);
    const handleQrDetectedRef = useRef<(decodedText: string) => void>(() => {});

    // If the user is not logged in, redirect to the home page.
    useEffect(() => {
        if (!isLoggedIn) {
            navigate('/', { replace: true });
        }
    }, [isLoggedIn, navigate]);

    // Request access to the user's camera through the web browser
    // and enumerate the available camera devices.
    useEffect(() => {
        if (!isLoggedIn) return;

        let cancelled = false;
        let probeStream: MediaStream | null = null;

        const initCameras = async () => {
            // The camera API is only available in secure contexts
            // (HTTPS or localhost).
            if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
                if (!cancelled) setSetupState('insecure-context');
                return;
            }

            // Ask the browser for camera access. This triggers the
            // camera permission prompt the first time it runs.
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ video: true });
                if (cancelled) {
                    stream.getTracks().forEach((track) => track.stop());
                    return;
                }
                probeStream = stream;
            } catch (error) {
                if (cancelled) return;
                if (
                    error instanceof DOMException &&
                    (error.name === 'NotFoundError' ||
                        error.name === 'DevicesNotFoundError' ||
                        error.name === 'OverconstrainedError')
                ) {
                    setSetupState('no-camera');
                } else {
                    setSetupState('permission-denied');
                }
                return;
            }

            // The probe stream was only used to verify camera access;
            // release the camera so the QR scanner can use it.
            probeStream.getTracks().forEach((track) => track.stop());

            try {
                const foundCameras = await Html5Qrcode.getCameras();
                if (cancelled) return;

                if (foundCameras.length === 0) {
                    setSetupState('no-camera');
                    return;
                }

                // Prefer a rear-facing camera, since that's the one a
                // smartphone user would point at an attendee's ticket.
                const rearCameraIndex = foundCameras.findIndex((camera) =>
                    /back|rear|environment/i.test(camera.label)
                );

                setCameras(foundCameras);
                setSelectedCameraId(
                    rearCameraIndex >= 0
                        ? foundCameras[rearCameraIndex].id
                        : foundCameras[0].id
                );
                setSetupState('ready');
            } catch {
                if (!cancelled) setSetupState('no-camera');
            }
        };

        initCameras();

        return () => {
            cancelled = true;
            if (probeStream) {
                probeStream.getTracks().forEach((track) => track.stop());
            }
        };
    }, [isLoggedIn]);

    // Stop the camera feed if the page unmounts.
    useEffect(() => {
        return () => {
            const scanner = scannerRef.current;
            scannerRef.current = null;
            if (scanner) {
                scanner
                    .stop()
                    .then(() => scanner.clear())
                    .catch(() => {});
            }
        };
    }, []);

    /**
     * Resumes QR scanning after the scanner was paused, e.g. after
     * a successful scan or after a failed scan request was handled.
     */
    const resumeScanning = () => {
        const scanner = scannerRef.current;
        if (scanner) {
            try {
                scanner.resume();
            } catch {
                // The scanner is not in a state that can be resumed.
            }
        }
        isHandlingScanRef.current = false;
    };

    /**
     * Sends the scanned public token to POST /api/v1/ticket-scans and
     * shows a message to the user based on the result of the request.
     */
    const submitScannedToken = async (publicToken: string) => {
        setIsSubmittingScan(true);
        try {
            const response = await authFetch('/api/v1/ticket-scans', {
                method: 'POST',
                body: JSON.stringify({ publicToken }),
            });
            const data = await response.json().catch(() => null);

            if (response.ok) {
                // Leave the scanner paused until the event host is
                // ready to scan the next ticket.
                setScanMessage({
                    type: 'success',
                    text: data?.message || 'Your ticket has been scanned.',
                });
            } else {
                setScanMessage({
                    type: 'error',
                    text: data?.message || 'Failed to scan the ticket.',
                });
                // Let the event host keep scanning, but ignore the same
                // token for a short while to avoid retry loops.
                failedTokenCooldownRef.current = {
                    token: publicToken,
                    until: Date.now() + FAILED_TOKEN_COOLDOWN_MS,
                };
                resumeScanning();
            }
        } catch {
            setScanMessage({
                type: 'error',
                text: 'An unexpected error occurred while scanning the ticket.',
            });
            failedTokenCooldownRef.current = {
                token: publicToken,
                until: Date.now() + FAILED_TOKEN_COOLDOWN_MS,
            };
            resumeScanning();
        } finally {
            setIsSubmittingScan(false);
        }
    };

    /**
     * Handles a QR code detected in the camera feed by pausing the
     * scanner and submitting the decoded value to the API.
     */
    const handleQrDetected = (decodedText: string) => {
        if (isHandlingScanRef.current) return;

        const cooldown = failedTokenCooldownRef.current;
        if (cooldown && cooldown.token === decodedText && Date.now() < cooldown.until) {
            return;
        }

        isHandlingScanRef.current = true;
        setScanMessage(null);

        try {
            scannerRef.current?.pause(true);
        } catch {
            // Pausing is best effort; the scan request still proceeds.
        }

        submitScannedToken(decodedText);
    };

    // Keep the latest handler available to the scanner callbacks, which
    // are only registered once when the scanner starts.
    handleQrDetectedRef.current = handleQrDetected;

    /**
     * Moves the page back to the camera selection screen, stopping
     * the active scanner if one is running.
     */
    const stopScanner = async () => {
        const scanner = scannerRef.current;
        scannerRef.current = null;
        isHandlingScanRef.current = false;
        failedTokenCooldownRef.current = null;
        setScanMessage(null);
        setIsSubmittingScan(false);
        setScanPhase('idle');

        if (scanner) {
            try {
                await scanner.stop();
                scanner.clear();
            } catch {
                // The scanner was not running; nothing to stop.
            }
        }
    };

    // Start the scanner once the event host presses "Start Scanning"
    // and the scanner's DOM element has been rendered.
    useEffect(() => {
        if (scanPhase !== 'starting') return;

        let cancelled = false;

        const startScanner = async () => {
            const scanner = new Html5Qrcode(SCANNER_ELEMENT_ID, { verbose: false });
            const qrboxSize = Math.max(
                150,
                Math.min(250, Math.floor(window.innerWidth * 0.6))
            );

            try {
                await scanner.start(
                    { deviceId: selectedCameraId },
                    { fps: 10, qrbox: { width: qrboxSize, height: qrboxSize } },
                    (decodedText) => handleQrDetectedRef.current(decodedText),
                    () => {
                        // This error callback fires on every frame where
                        // no QR code can be found; this is expected
                        // behavior and can be safely ignored.
                    }
                );

                if (cancelled) {
                    await scanner.stop().catch(() => {});
                    scanner.clear();
                    return;
                }

                scannerRef.current = scanner;
                isHandlingScanRef.current = false;
                setScanPhase('scanning');
            } catch (error) {
                if (!cancelled) {
                    setCameraError(
                        error instanceof Error
                            ? error.message
                            : 'Unable to start the selected camera.'
                    );
                    setScanPhase('idle');
                }
            }
        };

        startScanner();

        return () => {
            cancelled = true;
        };
    }, [scanPhase, selectedCameraId]);

    /**
     * Handles the event host selecting a different camera from the list.
     */
    const handleCameraChange = (event: ChangeEvent<HTMLSelectElement>) => {
        setSelectedCameraId(event.target.value);
    };

    /**
     * Handles the event host pressing the "Start Scanning" button.
     */
    const handleStartScanning = () => {
        setCameraError('');
        setScanMessage(null);
        setScanPhase('starting');
    };

    if (!isLoggedIn) {
        return null;
    }

    return (
        <main className="page-container">
            <div className="scan-ticket">
                <div className="scan-ticket__header">
                    <h1 className="scan-ticket__title">Scan Tickets</h1>
                    <div className="scan-ticket__nav-actions">
                        <Link to="/dashboard" className="btn btn--ghost btn--sm">
                            ← Back to Dashboard
                        </Link>
                    </div>
                </div>

                {setupState === 'requesting' && (
                    <div className="dashboard__empty">
                        <div className="spinner" aria-label="Requesting camera access" />
                        <p>Requesting camera access...</p>
                    </div>
                )}

                {setupState === 'insecure-context' && (
                    <div className="alert alert--error" role="alert">
                        Camera access requires a secure connection (HTTPS or
                        localhost). QR codes cannot be scanned without access
                        to a camera.
                    </div>
                )}

                {setupState === 'permission-denied' && (
                    <div className="alert alert--error" role="alert">
                        QR codes cannot be scanned without access to a camera.
                        Please allow camera access for this site in your
                        browser settings, then reload this page.
                    </div>
                )}

                {setupState === 'no-camera' && (
                    <div className="alert alert--error" role="alert">
                        No camera has been found on your device. Please connect
                        a camera and reload this page to scan tickets.
                    </div>
                )}

                {setupState === 'ready' && scanPhase === 'idle' && (
                    <div className="card scan-ticket__setup-card">
                        <label className="form-label" htmlFor="camera-select">
                            Choose a camera
                        </label>
                        <select
                            id="camera-select"
                            className="form-input"
                            value={selectedCameraId}
                            onChange={handleCameraChange}
                        >
                            {cameras.map((camera, index) => (
                                <option key={camera.id} value={camera.id}>
                                    {camera.label || `Camera ${index + 1}`}
                                </option>
                            ))}
                        </select>

                        {cameraError && (
                            <div className="alert alert--error" role="alert">
                                {cameraError}
                            </div>
                        )}

                        <button
                            type="button"
                            className="btn btn--accent btn--block"
                            onClick={handleStartScanning}
                        >
                            Start Scanning
                        </button>
                    </div>
                )}

                {setupState === 'ready' && scanPhase !== 'idle' && (
                    <div className="scan-ticket__scanner">
                        {scanPhase === 'starting' && (
                            <div className="dashboard__empty">
                                <div className="spinner" aria-label="Starting camera" />
                                <p>Starting the camera...</p>
                            </div>
                        )}

                        <div id="qr-reader" className="scan-ticket__video-container" />

                        <p className="scan-ticket__hint">
                            Point your camera at the QR code on the attendee's ticket.
                        </p>

                        {isSubmittingScan && (
                            <div className="dashboard__empty">
                                <div className="spinner" aria-label="Scanning ticket" />
                                <p>Scanning ticket...</p>
                            </div>
                        )}

                        {scanMessage && (
                            <div
                                className={`alert ${
                                    scanMessage.type === 'success'
                                        ? 'alert--success'
                                        : 'alert--error'
                                }`}
                                role="alert"
                            >
                                {scanMessage.text}
                            </div>
                        )}

                        <div className="scan-ticket__actions">
                            {scanMessage?.type === 'success' && (
                                <button
                                    type="button"
                                    className="btn btn--primary"
                                    onClick={() => {
                                        setScanMessage(null);
                                        resumeScanning();
                                    }}
                                >
                                    Scan Next Ticket
                                </button>
                            )}
                            <button
                                type="button"
                                className="btn btn--outline"
                                onClick={stopScanner}
                                disabled={isSubmittingScan}
                            >
                                {scanMessage?.type === 'success'
                                    ? 'Stop Scanning'
                                    : 'Switch Camera'}
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </main>
    );
}