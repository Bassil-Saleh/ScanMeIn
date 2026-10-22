package com.ticketproject.webapp.services.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.exceptions.InvalidRequestException;

/**
 * Unit tests for {@link PasswordStrengthService}, covering zxcvbn score
 * calculation, the inclusive minimum-strength-score threshold semantics,
 * null/blank password handling, the rejection message, and the fail-fast
 * validation of out-of-range configured thresholds.
 *
 * <p>The password fixtures below have deterministic zxcvbn scores (verified
 * against zxcvbn4j 1.9.0), so the assertions compare exact scores rather
 * than ranges.</p>
 */
class PasswordStrengthServiceTest
{
    /** zxcvbn score 0: a repeated-character password. */
    private static final String SCORE_0_PASSWORD = "aaaaaaaaaaaa";

    /** zxcvbn score 1: a very common password. */
    private static final String SCORE_1_PASSWORD = "password1234";

    /** zxcvbn score 2: a dictionary-based password with a common suffix. */
    private static final String SCORE_2_PASSWORD = "securePassword123";

    /** zxcvbn score 3: a word-based passphrase. */
    private static final String SCORE_3_PASSWORD = "summer2024vacation";

    /** zxcvbn score 4: a high-entropy random password. */
    private static final String SCORE_4_PASSWORD = "Qz7!mN2#vKp9$Lx4";

    /**
     * Service configured with the default minimum strength score. Building the
     * underlying zxcvbn estimator loads its dictionaries, so a single instance
     * is shared by the tests which do not need a custom threshold.
     */
    private static final PasswordStrengthService DEFAULT_SERVICE =
        new PasswordStrengthService(AppConstants.Passwords.DEFAULT_MIN_STRENGTH_SCORE);

    @Nested
    @DisplayName("Score calculation")
    class ScoreCalculationTests
    {
        @Test
        @DisplayName("A repeated-character password scores 0")
        void repeatedCharactersScoreZero()
        {
            assertThat(DEFAULT_SERVICE.calculateScore(SCORE_0_PASSWORD)).isZero();
        }

        @Test
        @DisplayName("A very common password scores 1")
        void veryCommonPasswordScoresOne()
        {
            assertThat(DEFAULT_SERVICE.calculateScore(SCORE_1_PASSWORD)).isEqualTo(1);
        }

        @Test
        @DisplayName("A dictionary-based password scores 2")
        void dictionaryBasedPasswordScoresTwo()
        {
            assertThat(DEFAULT_SERVICE.calculateScore(SCORE_2_PASSWORD)).isEqualTo(2);
        }

        @Test
        @DisplayName("A word-based passphrase scores 3")
        void wordBasedPassphraseScoresThree()
        {
            assertThat(DEFAULT_SERVICE.calculateScore(SCORE_3_PASSWORD)).isEqualTo(3);
        }

        @Test
        @DisplayName("A high-entropy random password scores 4")
        void highEntropyPasswordScoresFour()
        {
            assertThat(DEFAULT_SERVICE.calculateScore(SCORE_4_PASSWORD)).isEqualTo(4);
        }

        @Test
        @DisplayName("Scores always fall within zxcvbn's 0..4 range")
        void scoresFallWithinZxcvbnRange()
        {
            for (String password : new String[]
                {
                    SCORE_0_PASSWORD,
                    SCORE_1_PASSWORD,
                    SCORE_2_PASSWORD,
                    SCORE_3_PASSWORD,
                    SCORE_4_PASSWORD
                })
            {
                assertThat(DEFAULT_SERVICE.calculateScore(password))
                    .isBetween(AppConstants.Passwords.MIN_STRENGTH_SCORE, AppConstants.Passwords.MAX_STRENGTH_SCORE);
            }
        }
    }

    @Nested
    @DisplayName("Null and blank passwords")
    class NullAndBlankPasswordTests
    {
        @Test
        @DisplayName("A null password is rejected instead of being measured")
        void nullPasswordIsRejected()
        {
            assertThatThrownBy(() -> DEFAULT_SERVICE.calculateScore(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Password is required");
        }

        @Test
        @DisplayName("A blank password is rejected instead of being measured")
        void blankPasswordIsRejected()
        {
            assertThatThrownBy(() -> DEFAULT_SERVICE.isStrongEnough("   "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Password is required");
        }

        @Test
        @DisplayName("requireStrongPassword rejects a null password")
        void requireStrongPasswordRejectsNull()
        {
            assertThatThrownBy(() -> DEFAULT_SERVICE.requireStrongPassword(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Password is required");
        }
    }

    @Nested
    @DisplayName("Threshold enforcement")
    class ThresholdEnforcementTests
    {
        @Test
        @DisplayName("A password scoring exactly at the threshold is accepted")
        void scoreExactlyAtThresholdIsAccepted()
        {
            PasswordStrengthService service = new PasswordStrengthService(2);

            assertThat(service.getMinStrengthScore()).isEqualTo(2);
            assertThat(service.isStrongEnough(SCORE_2_PASSWORD)).isTrue();
            assertThatCode(() -> service.requireStrongPassword(SCORE_2_PASSWORD)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A password scoring above the threshold is accepted")
        void scoreAboveThresholdIsAccepted()
        {
            assertThat(DEFAULT_SERVICE.isStrongEnough(SCORE_3_PASSWORD)).isTrue();
            assertThat(DEFAULT_SERVICE.isStrongEnough(SCORE_4_PASSWORD)).isTrue();
            assertThatCode(() -> DEFAULT_SERVICE.requireStrongPassword(SCORE_4_PASSWORD)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A password scoring below the threshold is rejected")
        void scoreBelowThresholdIsRejected()
        {
            assertThat(DEFAULT_SERVICE.isStrongEnough(SCORE_2_PASSWORD)).isFalse();
            assertThat(DEFAULT_SERVICE.isStrongEnough(SCORE_0_PASSWORD)).isFalse();
            assertThatThrownBy(() -> DEFAULT_SERVICE.requireStrongPassword(SCORE_1_PASSWORD))
                .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("A threshold of 0 accepts every password")
        void thresholdZeroAcceptsEveryPassword()
        {
            PasswordStrengthService service = new PasswordStrengthService(0);

            assertThat(service.getMinStrengthScore()).isZero();
            assertThatCode(() -> service.requireStrongPassword(SCORE_0_PASSWORD)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A threshold of 4 rejects a password scoring 3")
        void thresholdFourRejectsScoreThree()
        {
            PasswordStrengthService service = new PasswordStrengthService(4);

            assertThatThrownBy(() -> service.requireStrongPassword(SCORE_3_PASSWORD))
                .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("Rejection messages")
    class RejectionMessageTests
    {
        @Test
        @DisplayName("The rejection message reports the score and the configured minimum")
        void rejectionMessageReportsScoreAndMinimum()
        {
            assertThatThrownBy(() -> DEFAULT_SERVICE.requireStrongPassword(SCORE_2_PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Password is too weak")
                .hasMessageContaining("strength score of 2 out of 4")
                .hasMessageContaining("minimum required score is 3");
        }

        @Test
        @DisplayName("The rejection message reports a custom configured minimum")
        void rejectionMessageReportsCustomMinimum()
        {
            PasswordStrengthService service = new PasswordStrengthService(4);

            assertThatThrownBy(() -> service.requireStrongPassword(SCORE_3_PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("strength score of 3 out of 4")
                .hasMessageContaining("minimum required score is 4");
        }

        @Test
        @DisplayName("The rejection message includes zxcvbn's feedback")
        void rejectionMessageIncludesZxcvbnFeedback()
        {
            // zxcvbn flags "password1234" as a very common password, and that
            // warning is appended to the rejection message.
            assertThatThrownBy(() -> DEFAULT_SERVICE.requireStrongPassword(SCORE_1_PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("common");
        }

        @Test
        @DisplayName("The rejection message never echoes the submitted password")
        void rejectionMessageNeverEchoesPassword()
        {
            for (String password : new String[]
                {
                    SCORE_0_PASSWORD,
                    SCORE_1_PASSWORD,
                    SCORE_2_PASSWORD
                })
            {
                assertThatThrownBy(() -> DEFAULT_SERVICE.requireStrongPassword(password))
                    .isInstanceOf(InvalidRequestException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(password));
            }
        }
    }

    @Nested
    @DisplayName("Configured threshold validation")
    class ThresholdValidationTests
    {
        @Test
        @DisplayName("A negative threshold fails fast")
        void negativeThresholdFailsFast()
        {
            assertThatThrownBy(() -> new PasswordStrengthService(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.password.min-strength-score must be between 0 and 4")
                .hasMessageContaining("-1");
        }

        @Test
        @DisplayName("A threshold above zxcvbn's maximum score fails fast")
        void thresholdAboveMaximumFailsFast()
        {
            assertThatThrownBy(() -> new PasswordStrengthService(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.password.min-strength-score must be between 0 and 4")
                .hasMessageContaining("5");
        }
    }
}